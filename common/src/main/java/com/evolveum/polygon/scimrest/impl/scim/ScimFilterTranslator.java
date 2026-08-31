/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.evolveum.polygon.scimrest.schema.ScimPathFormat;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

import java.util.*;

/**
 * Translates Connector Framework filters into SCIM filter expressions as defined by
 * RFC 7644 section 3.4.2.2. The filter tree is walked recursively and rendered through
 * the SCIM 2 SDK filter model, whose {@code toString()} produces the canonical
 * RFC-compliant expression (including value escaping) for the {@code filter} query
 * parameter of a SCIM search request.
 *
 * <p>Supported translation: {@code eq, co, sw, ew, gt, ge, lt, le, and, or, not}.
 * Filter values must be strings ({@link String} / {@link GuardedString}), booleans or
 * integers/longs/doubles/floats for numeric comparison. Filters without a SCIM
 * equivalent ({@code EqualsIgnoreCaseFilter}, {@code ContainsAllValuesFilter},
 * externally chained filters, unknown filter types), filters on attributes without a
 * SCIM mapping, and filters on value paths (e.g. {@code emails[type eq "work"].value}
 * ) cannot be translated and result in an {@link IllegalArgumentException}.</p>
 */
public class ScimFilterTranslator {

    private final Map<String, AttributePath> attributes;

    public ScimFilterTranslator(Map<String, AttributePath> attributes) {
        this.attributes = Map.copyOf(attributes);
    }

    public static ScimFilterTranslator fromObjectClass(RestObjectClassDefinition objectClass) {
        var attributes = new LinkedHashMap<String, AttributePath>();
        for (var attribute : objectClass.attributes()) {
            var mapping = attribute.scim();
            if (mapping == null || mapping.path() == null) {
                continue;
            }
            attributes.putIfAbsent(attribute.connId().getName(), mapping.path());
        }
        return new ScimFilterTranslator(attributes);
    }

    /**
     * Whether the given filter can be translated to a SCIM filter expression.
     * A {@code null} filter is considered translatable.
     */
    public boolean isTranslatable(Filter filter) {
        if (filter == null) {
            return true;
        }
        try {
            toScimFilter(filter);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Translates the given filter to a SCIM filter expression.
     *
     * @param filter the filter to translate, or {@code null} for an unfiltered search
     * @return the SCIM filter expression, or {@code null} when {@code filter} is {@code null}
     * @throws IllegalArgumentException if the filter cannot be translated to SCIM
     */
    public String translate(Filter filter) {
        if (filter == null) {
            return null;
        }
        return toScimFilter(filter).toString();
    }

    private com.unboundid.scim2.common.filters.Filter toScimFilter(Filter filter) {
        if (filter instanceof AndFilter and) {
            return com.unboundid.scim2.common.filters.Filter.and(toSubfilters(and.getFilters(), filter));
        }
        if (filter instanceof OrFilter or) {
            return com.unboundid.scim2.common.filters.Filter.or(toSubfilters(or.getFilters(), filter));
        }
        if (filter instanceof NotFilter not) {
            return com.unboundid.scim2.common.filters.Filter.not(toScimFilter(not.getFilter()));
        }
        if (filter instanceof AttributeFilter attributeFilter) {
            return toComparison(attributeFilter, filter);
        }
        throw unsupported(filter);
    }

    private List<com.unboundid.scim2.common.filters.Filter> toSubfilters(Collection<Filter> subfilters, Filter source) {
        if (subfilters.size() < 2) {
            throw unsupported(source);
        }
        var translated = new ArrayList<com.unboundid.scim2.common.filters.Filter>(subfilters.size());
        for (var subfilter : subfilters) {
            translated.add(toScimFilter(subfilter));
        }
        return translated;
    }

    private com.unboundid.scim2.common.filters.Filter toComparison(AttributeFilter filter, Filter source) {
        if (filter instanceof ContainsAllValuesFilter) {
            throw unsupported(source);
        }
        String operator;
        boolean stringOnly;
        if (filter instanceof EqualsFilter) {
            operator = "eq";
            stringOnly = false;
        } else if (filter instanceof ContainsFilter) {
            operator = "co";
            stringOnly = true;
        } else if (filter instanceof StartsWithFilter) {
            operator = "sw";
            stringOnly = true;
        } else if (filter instanceof EndsWithFilter) {
            operator = "ew";
            stringOnly = true;
        } else if (filter instanceof GreaterThanFilter) {
            operator = "gt";
            stringOnly = false;
        } else if (filter instanceof GreaterThanOrEqualFilter) {
            operator = "ge";
            stringOnly = false;
        } else if (filter instanceof LessThanFilter) {
            operator = "lt";
            stringOnly = false;
        } else if (filter instanceof LessThanOrEqualFilter) {
            operator = "le";
            stringOnly = false;
        } else {
            throw unsupported(source);
        }

        var path = resolvePath(filter.getAttribute().getName(), source);
        var value = singleValue(filter, source);
        var text = asText(value);
        if (text != null) {
            value = text;
        } else if (stringOnly) {
            throw unsupported(source);
        }
        return compare(operator, path, value, source);
    }

    private com.unboundid.scim2.common.filters.Filter compare(String operator, String path, Object value, Filter source) {
        try {
            if (value instanceof String text) {
                return scimComparison(operator, path, text);
            }
            if (value instanceof Boolean booleanValue) {
                return scimComparison(operator, path, booleanValue);
            }
            if (value instanceof Integer intValue) {
                return scimComparison(operator, path, intValue);
            }
            if (value instanceof Long longValue) {
                return scimComparison(operator, path, longValue);
            }
            if (value instanceof Double doubleValue) {
                return scimComparison(operator, path, doubleValue);
            }
            if (value instanceof Float floatValue) {
                return scimComparison(operator, path, floatValue);
            }
            throw unsupported(source);
        } catch (BadRequestException e) {
            throw new IllegalArgumentException(
                    "Invalid SCIM attribute path '" + path + "' for operator '" + operator + "'", e);
        }
    }

    private com.unboundid.scim2.common.filters.Filter scimComparison(String operator, String path, String value)
            throws BadRequestException {
        return switch (operator) {
            case "eq" -> com.unboundid.scim2.common.filters.Filter.eq(path, value);
            case "ne" -> com.unboundid.scim2.common.filters.Filter.ne(path, value);
            case "co" -> com.unboundid.scim2.common.filters.Filter.co(path, value);
            case "sw" -> com.unboundid.scim2.common.filters.Filter.sw(path, value);
            case "ew" -> com.unboundid.scim2.common.filters.Filter.ew(path, value);
            case "gt" -> com.unboundid.scim2.common.filters.Filter.gt(path, value);
            case "ge" -> com.unboundid.scim2.common.filters.Filter.ge(path, value);
            case "lt" -> com.unboundid.scim2.common.filters.Filter.lt(path, value);
            case "le" -> com.unboundid.scim2.common.filters.Filter.le(path, value);
            default -> throw new IllegalStateException("Unsupported SCIM operator: " + operator);
        };
    }

    private com.unboundid.scim2.common.filters.Filter scimComparison(String operator, String path, Boolean value)
            throws BadRequestException {
        return switch (operator) {
            case "eq" -> com.unboundid.scim2.common.filters.Filter.eq(path, value);
            case "ne" -> com.unboundid.scim2.common.filters.Filter.ne(path, value);
            default -> throw new IllegalStateException("Operator '" + operator + "' does not support boolean values");
        };
    }

    private com.unboundid.scim2.common.filters.Filter scimComparison(String operator, String path, Integer value)
            throws BadRequestException {
        return switch (operator) {
            case "eq" -> com.unboundid.scim2.common.filters.Filter.eq(path, value);
            case "ne" -> com.unboundid.scim2.common.filters.Filter.ne(path, value);
            case "gt" -> com.unboundid.scim2.common.filters.Filter.gt(path, value);
            case "ge" -> com.unboundid.scim2.common.filters.Filter.ge(path, value);
            case "lt" -> com.unboundid.scim2.common.filters.Filter.lt(path, value);
            case "le" -> com.unboundid.scim2.common.filters.Filter.le(path, value);
            default -> throw new IllegalStateException("Operator '" + operator + "' does not support numeric values");
        };
    }

    private com.unboundid.scim2.common.filters.Filter scimComparison(String operator, String path, Long value)
            throws BadRequestException {
        return switch (operator) {
            case "eq" -> com.unboundid.scim2.common.filters.Filter.eq(path, value);
            case "ne" -> com.unboundid.scim2.common.filters.Filter.ne(path, value);
            case "gt" -> com.unboundid.scim2.common.filters.Filter.gt(path, value);
            case "ge" -> com.unboundid.scim2.common.filters.Filter.ge(path, value);
            case "lt" -> com.unboundid.scim2.common.filters.Filter.lt(path, value);
            case "le" -> com.unboundid.scim2.common.filters.Filter.le(path, value);
            default -> throw new IllegalStateException("Operator '" + operator + "' does not support numeric values");
        };
    }

    private com.unboundid.scim2.common.filters.Filter scimComparison(String operator, String path, Double value)
            throws BadRequestException {
        return switch (operator) {
            case "eq" -> com.unboundid.scim2.common.filters.Filter.eq(path, value);
            case "ne" -> com.unboundid.scim2.common.filters.Filter.ne(path, value);
            case "gt" -> com.unboundid.scim2.common.filters.Filter.gt(path, value);
            case "ge" -> com.unboundid.scim2.common.filters.Filter.ge(path, value);
            case "lt" -> com.unboundid.scim2.common.filters.Filter.lt(path, value);
            case "le" -> com.unboundid.scim2.common.filters.Filter.le(path, value);
            default -> throw new IllegalStateException("Operator '" + operator + "' does not support numeric values");
        };
    }

    private com.unboundid.scim2.common.filters.Filter scimComparison(String operator, String path, Float value)
            throws BadRequestException {
        return switch (operator) {
            case "eq" -> com.unboundid.scim2.common.filters.Filter.eq(path, value);
            case "ne" -> com.unboundid.scim2.common.filters.Filter.ne(path, value);
            case "gt" -> com.unboundid.scim2.common.filters.Filter.gt(path, value);
            case "ge" -> com.unboundid.scim2.common.filters.Filter.ge(path, value);
            case "lt" -> com.unboundid.scim2.common.filters.Filter.lt(path, value);
            case "le" -> com.unboundid.scim2.common.filters.Filter.le(path, value);
            default -> throw new IllegalStateException("Operator '" + operator + "' does not support numeric values");
        };
    }

    private String resolvePath(String attributeName, Filter source) {
        var path = attributes.get(attributeName);
        if (path == null) {
            throw new IllegalArgumentException(
                    "Attribute '" + attributeName + "' cannot be translated to a SCIM filter: no SCIM attribute mapping");
        }
        for (var component : path.components()) {
            if (component instanceof AttributePath.FilterComponent) {
                throw new IllegalArgumentException(
                        "Attribute '" + attributeName + "' cannot be translated to a SCIM filter: "
                                + "value paths are not supported in filter expressions");
            }
        }
        return ScimPathFormat.INSTANCE.serialize(path);
    }

    private Object singleValue(AttributeFilter single, Filter source) {
        var values = single.getAttribute().getValue();
        if (values == null || values.size() != 1 || values.get(0) == null) {
            throw unsupported(source);
        }
        return values.get(0);
    }

    private String asText(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof GuardedString guarded) {
            var accessor = new GuardedStringAccessor();
            guarded.access(accessor);
            return accessor.getClearString();
        }
        return null;
    }

    private IllegalArgumentException unsupported(Filter filter) {
        return new IllegalArgumentException("Filter cannot be translated to SCIM: " + filter);
    }
}
