package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.AttributeMapping;
import com.evolveum.polygon.scim.rest.OpenApiTypeMapping;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObject;

import java.util.List;

public class RestAttribute {


    private final AttributeInfo info;
    private final String remoteName;
    private AttributeMapping mapping;

    public RestAttribute(RestAttributeBuilder restAttributeBuilder) {
        info = restAttributeBuilder.connIdBuilder.build();
        remoteName = restAttributeBuilder.remoteName;
        // FIXME Do the mapping resolution
        mapping = ConnIdMapping.of(info, OpenApiTypeMapping.from(restAttributeBuilder.jsonType, restAttributeBuilder.openApiFormat));

    }

    public AttributeMapping valueMapping() {
        return this.mapping;
    }

    public String remoteName() {
        return this.remoteName;
    }

    public Attribute attributeOf(Object connIdValues) {
        return AttributeBuilder.build(info.getName(), (List<Object>) connIdValues);
    }

    public AttributeInfo connId() {
        return this.info;
    }
}
