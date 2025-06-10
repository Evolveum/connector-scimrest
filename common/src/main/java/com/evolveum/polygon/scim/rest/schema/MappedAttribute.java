package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.AttributeMapping;
import com.evolveum.polygon.scim.rest.OpenApiTypeMapping;
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
    private AttributeMapping mapping;
    private ScimAttributeMapping scim;
    private Map<Class<? extends AttributeProtocolMapping>, AttributeProtocolMapping> protocolMappings = new HashMap<>();

    public MappedAttribute(MappedAttributeBuilderImpl mappedBuilder) {
        remoteName = mappedBuilder.remoteName;

        if (!mappedBuilder.isReference()) {
            var openApiMapping = OpenApiTypeMapping.from(mappedBuilder.jsonType, mappedBuilder.openApiFormat);
            mappedBuilder.connIdBuilder.setType(openApiMapping.connIdType());
            mapping = ConnIdMapping.of(mappedBuilder.connIdBuilder.build().getName(), openApiMapping);
            // ConnID Specific mapping may changed the name.
            mappedBuilder.connIdBuilder.setType(mapping.connIdType());
        } else {
            mappedBuilder.connIdBuilder.setType(ConnectorObjectReference.class);
        }
        // FIXME: Do the reference attribute mappings

        info = mappedBuilder.connIdBuilder.build();
        for (var proto : mappedBuilder.protocolMappings.entrySet()) {
            protocolMappings.put(proto.getKey(), proto.getValue().build());
        }



    }

    public AttributeMapping valueMapping() {
        return this.mapping;
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
        return scim;
    }

    public <T extends AttributeProtocolMapping> T mapping(Class<T> type) {
        return type.cast(protocolMappings.get(type));
    }
}
