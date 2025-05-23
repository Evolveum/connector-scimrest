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

    /**
     * Sets the protocol name for the current attribute.
     *
     * The protocol name represents the name of the attribute as present in serialized form
     * eg. JSON key or XML tag.
     *
     * @param protocolName the protocol name to be set for the attribute
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    public RestAttributeBuilder protocolName(String protocolName) {
        this.protocolName = protocolName;
        return this;
    }

    /**
     * Sets the remote name of the attribute.
     *
     * The remote name of the attribute represents the attribute name in the remote system.
     * This may differ from {@link #protocolName}.
     *
     * @param remoteName the name of attribute in remote system
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    public RestAttributeBuilder remoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }

    /**
     * Sets the JSON type of the attribute.
     *
     * The JSON type specifies the data type of the attribute when represented in JSON format.
     *
     * @param jsonType the JSON type of the attribute
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    public RestAttributeBuilder jsonType(String jsonType) {
        this.jsonType = jsonType;
        return this;
    }

    public RestAttributeBuilder openApiFormat(String openApiFormat) {
        this.openApiFormat = openApiFormat;
        return this;
    }

    /**
     * Sets the readability of the attribute.
     *
     * When the attribute is marked as not readable it also disables the attribute from being returned by default.
     *
     * @param readable true if the attribute should be readable, false otherwise
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    public RestAttributeBuilder readable(boolean readable) {
        connIdBuilder.setReadable(readable);
        if (!readable) {
            connIdBuilder.setReturnedByDefault(false);
        }
        return this;
    }

    /**
     * Specifies whether the attribute is required.
     *
     * Required attributes must be provided when creating or updating objects. They cannot be omitted.
     *
     * @param required true if the attribute should be required, false otherwise
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    public RestAttributeBuilder required(boolean required) {
        connIdBuilder.setRequired(required);
        return this;
    }

    public RestAttributeBuilder updateable(boolean updatable) {
        connIdBuilder.setUpdateable(updatable);
        return this;
    }

    /**
     * Specifies whether the attribute is creatable.
     *
     * Creatable attributes can be included in requests to create new objects or update existing ones.
     *
     * @param creatable true if the attribute should be creatable, false otherwise
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    public RestAttributeBuilder creatable(boolean creatable) {
        connIdBuilder.setCreateable(creatable);
        return this;
    }

    public RestAttributeBuilder description(String description) {
        this.description = description;
        return this;
    }

    public RestAttributeBuilder returnedByDefault(boolean returnedByDefault) {
        connIdBuilder.setReturnedByDefault(returnedByDefault);
        return this;
    }

    public RestAttributeBuilder multiValued(boolean multiValued) {
        connIdBuilder.setMultiValued(multiValued);
        return this;
    }

    /**
     * Builds and returns a {@code RestAttribute} instance with the specified attributes.
     *
     * @return a new {@code RestAttribute} instance configured with the current settings
     */
    public RestAttribute build() {
        return new RestAttribute(this);
    }

}
