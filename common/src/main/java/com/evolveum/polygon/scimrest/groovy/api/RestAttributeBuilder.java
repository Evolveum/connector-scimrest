/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.evolveum.polygon.scimrest.groovy.GroovyClosures;
import com.fasterxml.jackson.databind.JsonNode;
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
    RestAttributeBuilder readable(DefinitionValue<Boolean> readable);

    default RestAttributeBuilder readable(boolean readable) {
        return readable(DefinitionValue.from(readable, SourceLocation.capture()));
    }

    /**
     * Specifies whether the attribute is required.
     *
     * Required attributes must be provided when creating or updating objects. They cannot be omitted.
     *
     * @param required true if the attribute should be required, false otherwise
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    RestAttributeBuilder required(DefinitionValue<Boolean> required);

    default RestAttributeBuilder required(boolean required) {
        return required(DefinitionValue.from(required, SourceLocation.capture()));
    }

    RestAttributeBuilder description(DefinitionValue<String> description);

    default RestAttributeBuilder description(String description) {
        return description(DefinitionValue.from(description, SourceLocation.capture()));
    }

    RestAttributeBuilder returnedByDefault(DefinitionValue<Boolean> returnedByDefault);

    default RestAttributeBuilder returnedByDefault(boolean returnedByDefault) {
        return returnedByDefault(DefinitionValue.from(returnedByDefault, SourceLocation.capture()));
    }

    RestAttributeBuilder multiValued(DefinitionValue<Boolean> multiValued);

    default RestAttributeBuilder multiValued(boolean multiValued) {
        return multiValued(DefinitionValue.from(multiValued, SourceLocation.capture()));
    }

    /**
     * Specifies whether the attribute is creatable.
     *
     * Creatable attributes can be included in requests to create new objects or update existing ones.
     *
     * @param creatable true if the attribute should be creatable, false otherwise
     * @return the current instance of {@code RestAttributeBuilder} for method chaining
     */
    RestAttributeBuilder creatable(DefinitionValue<Boolean> creatable);

    default RestAttributeBuilder creatable(boolean creatable) {
        return creatable(DefinitionValue.from(creatable, SourceLocation.capture()));
    }

    default RestAttributeBuilder updatable(DefinitionValue<Boolean> updatable) {
        return updateable(updatable);
    }

    default RestAttributeBuilder updatable(boolean updatable) {
        return updateable(updatable);
    }

    RestAttributeBuilder updateable(DefinitionValue<Boolean> updateable);

    default RestAttributeBuilder updateable(boolean updateable) {
        return updateable(DefinitionValue.from(updateable, SourceLocation.capture()));
    }

    RestAttributeBuilder emulated(DefinitionValue<Boolean> emulated);

    default RestAttributeBuilder emulated(boolean emulated) {
        return emulated(DefinitionValue.from(emulated, SourceLocation.capture()));
    }


    // Protocol specific mappings

    JsonMapping json();

    ScimMapping scim();

    JsonMapping json(@DelegatesTo(value = JsonMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    ConnIdMapping connId();

    default ConnIdMapping connId(@DelegatesTo(ConnIdMapping.class) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, connId());
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
    RestAttributeBuilder protocolName(DefinitionValue<String> protocolName);

    default RestAttributeBuilder protocolName(String protocolName) {
        return protocolName(DefinitionValue.from(protocolName, SourceLocation.capture()));
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
    RestAttributeBuilder remoteName(DefinitionValue<String> remoteName);

    default RestAttributeBuilder remoteName(String remoteName) {
        return remoteName(DefinitionValue.from(remoteName, SourceLocation.capture()));
    }

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


    RestAttributeBuilder complexType(DefinitionValue<String> objectClass);

    default RestAttributeBuilder complexType(String objectClass) {
        return complexType(DefinitionValue.from(objectClass, SourceLocation.capture()));
    }


    default RestAttributeBuilder openApiFormat(String openapiFormat) {
        json().openApiFormat(openapiFormat);
        return this;
    }

    ScimMapping scim(@DelegatesTo(value = ScimMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);


    interface MappingBuilder<T extends MappingBuilder<T>> {

        T implementation(ValueMapping<?, JsonNode> mapping);

        T implementation(@DelegatesTo(ValueMappingBuilder.class) Closure<?> closure);

        MappingTableBuilder mappingTable();

        MappingTableBuilder mappingTable(@DelegatesTo(value = MappingTableBuilder.class) Closure<?> closure);
    }

    interface MappingTableBuilder {

        MappingTableBuilder pair(Object remote, Object local);
    }

    interface JsonMapping extends MappingBuilder<JsonMapping> {
        String name();
        JsonMapping name(String protocolName);
        JsonMapping type(String jsonType);
        JsonMapping openApiFormat(String openapiFormat);

        default AttributePath attribute(String name) {
            return AttributePath.of(name);
        }

        JsonMapping path(AttributePath path);
    }

    interface ScimMapping extends MappingBuilder<ScimMapping> {
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

        AttributePath path();
    }

    interface ConnIdMapping {

        ConnIdMapping name(String name);

        ConnIdMapping type(Class<?> connIdType);
    }

}
