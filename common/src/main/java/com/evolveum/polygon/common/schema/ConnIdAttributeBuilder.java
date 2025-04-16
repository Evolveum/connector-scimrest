package com.evolveum.polygon.common.schema;

import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;

public class ConnIdAttributeBuilder {

    public AttributeInfoBuilder setName(String name) {
        return builder.setName(name);
    }

    public AttributeInfoBuilder setType(Class<?> value) {
        return builder.setType(value);
    }

    public AttributeInfoBuilder setReadable(boolean value) {
        return builder.setReadable(value);
    }

    public AttributeInfoBuilder setCreateable(boolean value) {
        return builder.setCreateable(value);
    }

    public AttributeInfoBuilder setRequired(boolean value) {
        return builder.setRequired(value);
    }

    public AttributeInfoBuilder setMultiValued(boolean value) {
        return builder.setMultiValued(value);
    }

    public AttributeInfoBuilder setUpdateable(boolean value) {
        return builder.setUpdateable(value);
    }

    public AttributeInfoBuilder setReturnedByDefault(boolean value) {
        return builder.setReturnedByDefault(value);
    }

    AttributeInfoBuilder builder;

    public ConnIdAttributeBuilder() {
        builder = new AttributeInfoBuilder();
    }

}
