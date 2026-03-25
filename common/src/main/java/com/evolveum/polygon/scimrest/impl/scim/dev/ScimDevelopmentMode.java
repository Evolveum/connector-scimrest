/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.dev;

import com.evolveum.polygon.scimrest.schema.RestSchemaBuilder;
import com.evolveum.polygon.scimrest.JsonSchemaValueMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * Facilitates addition of development-specific SCIM object classes and JSON processing utilities
 * for schema and resource definitions when development mode is enabled.
 * <p>
 * This class defines two object classes: {@value #SCHEMA_OC_NAME} for SCIM schema representations
 * and {@value #RESOURCE_OC_NAME} for SCIM resource representations, including their attributes
 * and constraints required during connector development.
 * </p>
 */
public class ScimDevelopmentMode {

    public static final String SCHEMA_OC_NAME = "conndev_ScimSchema";
    public static final String RESOURCE_OC_NAME = "conndev_ScimResource";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Contributes dev schema object classes to the schema builder.
     * Only called when developmentMode is enabled.
     */
    public void contributeSchemaObjects(RestSchemaBuilder schemaBuilder) {
        contributeSchemaObjectClass(schemaBuilder);
        contributeResourceObjectClass(schemaBuilder);
    }

    /**
     * Creates conndev_ScimSchema object class with attributes:
     * - id (Uid): SCIM schema URN
     * - name (Name): SCIM schema name
     * - schemaContent: Full SchemaResource JSON
     */
    private void contributeSchemaObjectClass(RestSchemaBuilder schemaBuilder) {
        var oc = schemaBuilder.objectClass(SCHEMA_OC_NAME);

        oc.attribute("id")
            .connId()
                .name(Uid.NAME)
                .type(String.class);

        oc.attribute("name")
            .connId()
                .name(Name.NAME)
                .type(String.class);

        // Use json() directly to get the JsonMapping builder, then set implementation
        oc.attribute("schemaContent")
            .creatable(false)
            .updateable(false)
            .json()  // Get the JsonMapping builder for this attribute
                .type("string")  // Set the JSON type
                .implementation(JsonSchemaValueMapping.STRING);  // Set the implementation
    }

    /**
     * Creates conndev_ScimResource object class with attributes:
     * - id (Uid): SCIM resource ID
     * - name (Name): SCIM resource name
     * - schema: Primary schema URN
     * - endpoint: REST endpoint path
     * - primarySchema: Primary SchemaResource JSON
     * - schemaExtensions: Array of extension SchemaResource JSON
     */
    private void contributeResourceObjectClass(RestSchemaBuilder schemaBuilder) {
        var oc = schemaBuilder.objectClass(RESOURCE_OC_NAME);

        oc.attribute("id")
            .connId()
                .name(Uid.NAME)
                .type(String.class);

        oc.attribute("name")
            .connId()
                .name(Name.NAME)
                .type(String.class);

        oc.attribute("schema")
            .connId()
                .type(String.class);

        oc.attribute("endpoint")
            .connId()
                .type(String.class);

        oc.attribute("primarySchema")
            .creatable(false)
            .updateable(false)
            .json()
                .type("string")
                .implementation(JsonSchemaValueMapping.STRING);

        oc.attribute("schemaExtensions")
            .creatable(false)
            .updateable(false)
            .multiValued(true)
            .json()
                .type("array")
                .implementation(JsonSchemaValueMapping.STRING);
    }

    /**
     * Converts a SchemaResource object to JSON string.
     */
    public static String schemaToJson(Object schemaResource) {
        try {
            if (schemaResource == null) {
                return null;
            }
            return OBJECT_MAPPER.writeValueAsString(schemaResource);
        } catch (Exception e) {
            throw new ConnectorException("Failed to convert SchemaResource to JSON", e);
        }
    }
}
