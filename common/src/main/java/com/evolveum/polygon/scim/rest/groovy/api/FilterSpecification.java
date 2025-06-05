package com.evolveum.polygon.scim.rest.groovy.api;

import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;

import java.util.function.Predicate;

/**
 * Functional interface for specifying filtering criteria of filters.
 * This allows for flexible and concise filtering logic to be defined in a declarative manner.
 *
 * Use {@link FilterSpecification.Attribute} for writing checks and specifications for {@link AttributeFilter}
 *
 * @see Filter
 */
@FunctionalInterface
public interface FilterSpecification {

    static FilterSpecification EMPTY_FILTER = f -> f == null;


    /**
     * Determines if the provided filter matches the criteria specified by this specification.
     *
     * @param filter The filter to be checked against this specification.
     * @return true if the filter matches, false otherwise.
     */
    boolean matches(Filter filter);

    /**
     * Creates a new {@link FilterSpecification.Attribute} that matches filters with a specific attribute name.
     *
     * @param name the name of the attribute to match
     * @return a {@link FilterSpecification.Attribute} that checks if a filter's attribute name equals the specified name
     */
    static FilterSpecification.Attribute attribute(String name) {
        // TODO: Should we do some probably translation between attribute names? Or should it be handled in builder?
        return filter -> name.equals(filter.getAttribute().getName());
    }


    static FilterSpecification.Attribute attribute(Predicate<AttributeFilter> filterPredicate) {
        return filter -> filterPredicate.test((AttributeFilter) filter);
    }

    /**
     * Functional interface for specifying filtering criteria of attribute filters.
     *
     * The check if  filter type is {@link AttributeFilter} is built-in.
     *
     */
    @FunctionalInterface
    interface Attribute extends FilterSpecification {

        @Override
        default boolean matches(Filter filter) {
            if (filter instanceof AttributeFilter attrFilter) {
                return matches(attrFilter);
            }
            return false;
        }

        /**
         * Checks if the attribute matches the given filter.
         *
         * @param filter The filter to apply to the attribute.
         * @return true if the attribute matches the filter, false otherwise.
         */
        boolean matches(AttributeFilter filter);

        /**
         * A derived specification that all previous conditions, and tests if filter is {@link EqualsFilter}.
         *
         */
        default FilterSpecification.Attribute eq() {
            return chain(f -> f instanceof EqualsFilter);
        }

        /**
         * A derived specification that all previous conditions, and tests if filter is {@link ContainsFilter}.
         *
         * @return A new {@link Attribute} specification that checks if an attribute contains a specific value.
         */
        default FilterSpecification.Attribute contains() {
            return chain(f -> f instanceof ContainsFilter);
        }

        /**
         * Creates a new {@link Attribute} that matches only if the current and provided specifications both matches.
         * @param filterSpec The filter specification to chain with this specification.
         * @return A new {@link Attribute} spacification that represents the chaining of two filter specifications.
         */
        default Attribute chain(Attribute filterSpec) {
            return filter -> matches(filter) && filterSpec.matches(filter) ;
        }

        /**
         * A derived specification that all previous conditions, and tests if filter has only one single value.
         *
         * @return An {@link Attribute} specification that matches only if the attribute has exactly one value.
         */
        default Attribute anySingleValue() {
            return chain(filter -> filter.getAttribute().getValue().size() == 1);
        }
    }
}
