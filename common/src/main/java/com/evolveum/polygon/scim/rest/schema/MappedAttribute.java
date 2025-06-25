/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.ValueMapping;
import com.evolveum.polygon.scim.rest.OpenApiValueMapping;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolver;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MappedAttribute {

    private final AttributeInfo info;
    private final String remoteName;
    private final Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMapping<?,?>> protocolMappings = new HashMap<>();
    private final boolean emulated;
    private AttributeResolver resolver;

    public MappedAttribute(MappedAttributeBuilderImpl mappedBuilder) {
        remoteName = mappedBuilder.remoteName;
        emulated = mappedBuilder.emulated;

        Class<?> suggestedConnIdType = null;
        for (var proto : mappedBuilder.protocolMappings.entrySet()) {
            var protocolMapping = proto.getValue().build();
            if (protocolMapping == null) {
                continue;
            }
            protocolMappings.put(proto.getKey(), protocolMapping);
            if (protocolMapping.connIdType() != null) {
                if (suggestedConnIdType != null && !protocolMapping.connIdType().equals(suggestedConnIdType)) {
                    throw new IllegalStateException("Multiple ConnID types declared for attribute. " + protocolMapping.connIdType() + ", " + suggestedConnIdType);
                }
                suggestedConnIdType = protocolMapping.connIdType();
            }
        }
        if (suggestedConnIdType == null) {
            suggestedConnIdType = mappedBuilder.connIdType;
        }

        if (!mappedBuilder.isReference()) {
            if (suggestedConnIdType == null) {
                throw new IllegalArgumentException("Missing ConnId type definition for attribute " + remoteName);
            }
            mappedBuilder.connIdBuilder.setType(suggestedConnIdType);
        } else {
            mappedBuilder.connIdBuilder.setType(ConnectorObjectReference.class);
        }
        // FIXME: Do the reference attribute mappings

        info = mappedBuilder.connIdBuilder.build();
        mappedBuilder.deffered.set(this);

        if (mappedBuilder.resolverBuilder != null) {
            this.resolver = mappedBuilder.resolverBuilder.build();
        }




    }

    public String remoteName() {
        return this.remoteName;
    }

    public Attribute attributeOf(Object connIdValues) {
        if (connIdValues instanceof List) {
            return AttributeBuilder.build(info.getName(), (List<Object>) connIdValues);
        }
        return AttributeBuilder.build(info.getName(), connIdValues);
    }

    public AttributeInfo connId() {
        return this.info;
    }

    public ScimAttributeMapping scim() {
        return mapping(ScimAttributeMapping.class);
    }

    public JsonAttributeMapping json() {
        return mapping(JsonAttributeMapping.class);
    }

    public <T extends AttributeProtocolMapping<?,?>> T mapping(Class<T> type) {
        return type.cast(protocolMappings.get(type));
    }

    public boolean emulated() {
        return emulated;
    }

    public AttributeResolver resolver() {
        return resolver;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append("{connid=")
                .append(info.getName())
                .append(", remoteName=")
                .append(remoteName)
                .append('}')
                .toString();
    }
}
