package com.evolveum.polygon.scim.rest.schema;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;

public class RestAttributeBuilder {

    RestObjectClassBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    String protocolName;
    String remoteName;

    String jsonType;
    String openApiFormat;
    private String description;

    public RestAttributeBuilder(RestObjectClassBuilder restObjectClassBuilder, String name) {
        this.remoteName = name;
        connIdBuilder.setName(name);
        connIdBuilder.setNativeName(name);
        this.objectClass = restObjectClassBuilder;
    }

    public RestAttributeBuilder protocolName(String protocolName) {
        this.protocolName = protocolName;
        return this;
    }

    public RestAttributeBuilder remoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }

    public RestAttributeBuilder jsonType(String jsonType) {
        this.jsonType = jsonType;
        return this;
    }

    public RestAttributeBuilder openApiFormat(String openApiFormat) {
        this.openApiFormat = openApiFormat;
        return this;
    }

    public RestAttributeBuilder readable(boolean readable) {
        connIdBuilder.setReadable(readable);
        if (!readable) {
            connIdBuilder.setReturnedByDefault(false);
        }
        return this;
    }

    public RestAttributeBuilder required(boolean required) {
        connIdBuilder.setRequired(required);
        return this;
    }

    public RestAttributeBuilder updateable(boolean updatable) {
        connIdBuilder.setUpdateable(updatable);
        return this;
    }

    public RestAttributeBuilder creatable(boolean creatable) {
        connIdBuilder.setCreateable(creatable);
        return this;
    }

    public RestAttributeBuilder description(String description) {
        this.description = description;
        return this;
    }

    public RestAttribute build() {
        return new RestAttribute(this);
    }

}
