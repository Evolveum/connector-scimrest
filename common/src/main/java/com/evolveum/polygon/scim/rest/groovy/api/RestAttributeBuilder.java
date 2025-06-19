package com.evolveum.polygon.scim.rest.groovy.api;

import com.evolveum.polygon.scim.rest.ValueMapping;
import com.evolveum.polygon.scim.rest.api.AttributePath;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface RestAttributeBuilder {

    /**
     * Sets the readability of the attribute.
     *
     * When the attribute is marked as not readable it also disables the attribute from being returned by default.
     *
     * @param readable true if the attribute should be readable, false otherwise
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    RestAttributeBuilder readable(boolean readable);

    /**
     * Specifies whether the attribute is required.
     *
     * Required attributes must be provided when creating or updating objects. They cannot be omitted.
     *
     * @param required true if the attribute should be required, false otherwise
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    RestAttributeBuilder required(boolean required);

    RestAttributeBuilder returnedByDefault(boolean returnedByDefault);

    RestAttributeBuilder multiValued(boolean multiValued);

    /**
     * Specifies whether the attribute is creatable.
     *
     * Creatable attributes can be included in requests to create new objects or update existing ones.
     *
     * @param creatable true if the attribute should be creatable, false otherwise
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    RestAttributeBuilder creatable(boolean creatable);

    RestAttributeBuilder updateable(boolean updateable);

    void emulated(boolean emulated);


    // Protocol specific mappings

    JsonMapping json();

    ScimMapping scim();

    JsonMapping json(@DelegatesTo(value = JsonMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    ConnIdMapping connId();
    /**
     * Sets the protocol name for the current attribute.
     *
     * The protocol name represents the name of the attribute as present in serialized form
     * eg. JSON key or XML tag.
     *
     * @param protocolName the protocol name to be set for the attribute
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    RestAttributeBuilder protocolName(String protocolName);

    /**
     * Sets the remote name of the attribute.
     *
     * The remote name of the attribute represents the attribute name in the remote system.
     * This may differ from {@link #protocolName}.
     *
     * @param remoteName the name of attribute in remote system
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    RestAttributeBuilder remoteName(String remoteName);

    /**
     * Sets the JSON type of the attribute.
     *
     * The JSON type specifies the data type of the attribute when represented in JSON format.
     *
     * @param jsonType the JSON type of the attribute
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    default RestAttributeBuilder jsonType(String jsonType) {
        json().type(jsonType);
        return this;
    }

    default RestAttributeBuilder openApiFormat(String openapiFormat) {
        json().openApiFormat(openapiFormat);
        return this;
    }

    ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    interface JsonMapping {
        String name();
        JsonMapping name(String protocolName);
        JsonMapping type(String jsonType);
        JsonMapping openApiFormat(String openapiFormat);


    }

    interface ScimMapping {
        /**
         * Name of the matching SCIM attribute
         **/
        String name();

        ScimMapping name(String name);

        ScimMapping type(String name);

        /**
         * SCIM Attribute path
         *
         * A path to SCIM attribute which will be mapped to ConnID attribute
         *
         * Path should uniquely point to one JSON attribute (eg. `name.formatted` or `email[type eq primary]`)
         *
         * @param path
         * @return
         */
        ScimMapping path(String path);

        /**
         * SCIM Attribute path
         *
         * A path to SCIM attribute which will be mapped to ConnID attribute
         *
         * Path should uniquely point to one JSON attribute (eg. `name.formatted` or `email[type eq primary]`)
         *
         * @param path
         * @return
         */
        ScimMapping path(AttributePath path);

        default AttributePath attribute(String name) {
            return AttributePath.of(name);
        }

        AttributePath extension(String uriOrAlias);

        ScimMapping implementation(ValueMapping<?,?> mapping);

        ScimMapping implementation(@DelegatesTo(value = ValueMappingBuilder.class) Closure<?> closure);
    }

    interface ConnIdMapping {

        ConnIdMapping name(String name);

        ConnIdMapping type(Class<?> connIdType);
    }

}
