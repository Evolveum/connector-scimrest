/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.lookup;

import com.evolveum.polygon.scimrest.schema.RestLookupMapping;
import com.evolveum.polygon.scimrest.schema.RestSchema;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LookupValueConverter {

    private final RestSchema schema;
    private final Map<String, Map<String, RestLookupMapping>> classLookups;
    private final ConcurrentHashMap<RestLookupMapping, CachedObjectLookupResolver> resolvers = new ConcurrentHashMap<>();
    private final CachedObjectLookupResolver.SearchExecutor searchExecutor;

    public LookupValueConverter(RestSchema schema, CachedObjectLookupResolver.SearchExecutor searchExecutor) {
        this.schema = schema;
        this.searchExecutor = searchExecutor;
        var index = new HashMap<String, Map<String, RestLookupMapping>>();
        for (var def : schema.objectClasses()) {
            var name = def.connId().getType().toLowerCase(Locale.ROOT);
            var attrLookups = new HashMap<String, RestLookupMapping>();
            for (var attrDef : def.attributes()) {
                var lookup = attrDef.lookup();
                if (lookup != null) {
                    attrLookups.put(attrDef.connId().getName(), lookup);
                }
            }
            if (!attrLookups.isEmpty()) {
                index.put(name, attrLookups);
            }
        }
        this.classLookups = Map.copyOf(index);
    }

    public Set<Attribute> toNative(ObjectClass objectClass, Set<Attribute> attributes) {
        var converted = transform(objectClass, new ArrayList<>(attributes), CachedObjectLookupResolver::toNative);
        return new LinkedHashSet<>(converted);
    }

    public Set<Attribute> toUserFacing(ObjectClass objectClass, Set<Attribute> attributes) {
        var converted = transform(objectClass, new ArrayList<>(attributes), CachedObjectLookupResolver::toUser);
        return new LinkedHashSet<>(converted);
    }

    public ConnectorObject toUserFacing(ObjectClass objectClass, ConnectorObject object) {
        var converted = transform(objectClass, new ArrayList<>(object.getAttributes()), CachedObjectLookupResolver::toUser);
        var def = schema.objectClass(objectClass.getObjectClassValue());
        var builder = def.newObjectBuilder().setUid(object.getUid());
        converted.forEach(builder::addAttribute);
        return builder.build();
    }

    @FunctionalInterface
    private interface Transform {
        Object apply(CachedObjectLookupResolver resolver, Object value);
    }

    private ArrayList<Attribute> transform(ObjectClass objectClass, List<Attribute> attributes, Transform transform) {
        if (attributes.isEmpty()) {
            return (ArrayList<Attribute>) attributes;
        }
        var lookupAttrs = classLookups.get(objectClass.getObjectClassValue().toLowerCase(Locale.ROOT));
        if (lookupAttrs == null) {
            return (ArrayList<Attribute>) attributes;
        }
        var result = new ArrayList<Attribute>(attributes.size());
        for (var attr : attributes) {
            var lookupDef = lookupAttrs.get(attr.getName());
            if (lookupDef == null) {
                result.add(attr);
                continue;
            }
            var resolver = resolvers.computeIfAbsent(lookupDef,
                    m -> new CachedObjectLookupResolver(m, searchExecutor));
            var newValues = new ArrayList<>(attr.getValue().size());
            for (var value : attr.getValue()) {
                var converted = transform.apply(resolver, value);
                newValues.add(converted != null ? converted : value);
            }
            var rebuilt = new AttributeBuilder();
            rebuilt.setName(attr.getName());
            rebuilt.addValue(newValues);
            result.add(rebuilt.build());
        }
        return result;
    }
}