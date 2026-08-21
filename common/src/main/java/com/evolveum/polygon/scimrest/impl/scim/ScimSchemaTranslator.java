/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.scimrest.schema.RestAttributeBuilderImpl;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinitionBuilder;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMappingRule;
import com.evolveum.polygon.scimrest.schema.ScimMappingAction;
import com.evolveum.polygon.scimrest.schema.ScimResourceMappingRule;
import com.evolveum.polygon.scimrest.schema.strategy.ScimMetadataToConnIdRule;
import com.evolveum.polygon.scimrest.schema.strategy.ScimMembershipReferenceRule;
import com.evolveum.polygon.scimrest.schema.strategy.ScimMutabilityToCrudRule;
import com.evolveum.polygon.scimrest.schema.strategy.ScimNameDetectionRule;
import com.evolveum.polygon.scimrest.schema.strategy.ScimTypeToJsonTypeRule;
import com.evolveum.polygon.scimrest.schema.strategy.ScimUidDetectionRule;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.Uid;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates SCIM schema metadata into Connector Framework schema definitions using a
 * rule-based approach. Strategies examine resource and attribute metadata, produce
 * {@link ScimMappingAction} instances that modify schema builders.
 *
 * <p>Flow within {@link #populateSchema(ScimResourceContext, RestSchemaBuilderImpl)}:</p>
 * <ol>
 *   <li>Create attributes from SCIM schema and run attribute-level rules</li>
 *   <li>Handle COMPLEX attributes inline (embedded object class creation)</li>
 *   <li>Apply resource-level rules (Uid detection)</li>
 *   <li>Populate path-based attributes from Groovy definitions</li>
 * </ol>
 *
 * @see ScimResourceMappingRule
 * @see ScimAttributeMappingRule
 * @see ScimMappingAction
 */
public class ScimSchemaTranslator {

    private static final String USER_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String GROUP_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:Group";

    private Map<String, String> resourceToObjectClass = new HashMap<>();
    private Map<String, ScimResourceContext> objectClassToResource = new HashMap<>();

    private final ContextLookup contextLookup;

    private final List<ScimResourceMappingRule> resourceRules = new ArrayList<>();
    private final List<ScimAttributeMappingRule> attributeRules = new ArrayList<>();

    public ScimSchemaTranslator(ContextLookup contextLookup) {
        this.contextLookup = contextLookup;
        registerDefaultRules();
    }

    /** Add a custom resource-level mapping rule. */
    public ScimSchemaTranslator addResourceRule(ScimResourceMappingRule rule) {
        this.resourceRules.add(rule);
        return this;
    }

    /** Add a custom attribute-level mapping rule. */
    public ScimSchemaTranslator addAttributeRule(ScimAttributeMappingRule rule) {
        this.attributeRules.add(rule);
        return this;
    }

    private void registerDefaultRules() {
        // Attribute-level rules — order matters; general rules run before specific ones
        attributeRules.add(new ScimTypeToJsonTypeRule());
        attributeRules.add(new ScimMetadataToConnIdRule());
        attributeRules.add(new ScimMutabilityToCrudRule());
        attributeRules.add(new ScimNameDetectionRule());
        attributeRules.add(new ScimMembershipReferenceRule(resourceToObjectClass, objectClassToResource, contextLookup));

        // Resource-level rules
        resourceRules.add(new ScimUidDetectionRule());
    }

    public void correlateObjectClasses(ScimResourceContext scim, RestSchemaBuilderImpl schema) {
        var objectClass = findOrCreateObjectClass(scim.resource(), schema);
        resourceToObjectClass.put(scim.resource().getName(), objectClass.name());
        objectClassToResource.put(objectClass.name(), scim);

        if (objectClass.description() == null) {
            objectClass.description(scim.resource().getDescription());
        }
        objectClass.scim().name(scim.resource().getName());
        if (scim.schemaUri() != null) {
            objectClass.scim().schemaUri(scim.schemaUri().toString());
        }
    }

    public void populateSchema(ScimResourceContext scim, RestSchemaBuilderImpl schema) {
        var objectClassName = resourceToObjectClass.get(scim.resource().getName());
        var objectClass = schema.objectClass(objectClassName);
        var onlyListed = objectClass.scim().isOnlyExplicitlyListed();

        // Handle built-in attributes (id) from explicit Groovy definitions
        populateBuiltInAttributes(objectClass, onlyListed);

        for (var scimAttr : scim.primarySchema().getAttributes()) {
            if (isComplexNotMembership(scimAttr, scim.primarySchema())) {
                if (!onlyListed && !isAlreadyDefined(scimAttr, objectClass)) {
                    populateComplexAttribute(scimAttr, schema, objectClass);
                }
                continue;
            }

            var attribute = findOrCreateAttribute(scimAttr, objectClass, onlyListed);
            if (attribute != null) {
                attribute.scim().name(scimAttr.getName());
                applyAttributeRules(scim, scimAttr, objectClass, attribute);
            }
        }

        // Resource-level rules (Uid detection etc.)
        applyResourceRules(scim, objectClass);

        // Path-based attributes from Groovy definitions
        populatePathBasedSchema(scim, objectClass);
    }

    /**
     * Handle built-in attributes from explicit Groovy definitions.
     * This ensures that explicitly-defined attributes (like "id") get
     * their ConnId mapping applied even when the SCIM schema doesn't list them.
     */
    private void populateBuiltInAttributes(RestObjectClassDefinitionBuilder objectClass, boolean onlyListed) {
        var idAttr = findOrCreateAttributeByScimName("id", objectClass, onlyListed);
        if (idAttr != null) {
            idAttr.scim().name("id").type("string");
            if (objectClass.connIdAttributeNotDefined(Uid.NAME)) {
                idAttr.connId().name(Uid.NAME);
            }
        }
    }

    private RestAttributeBuilderImpl findOrCreateAttributeByScimName(String scimName,
                                                                      RestObjectClassDefinitionBuilder objectClass,
                                                                      boolean onlyListed) {
        for (var attr : objectClass.allAttributes()) {
            if (scimName.equals(attr.scim().name())) {
                return attr;
            }
        }
        if (onlyListed) {
            return null;
        }
        return objectClass.attribute(scimName);
    }

    private boolean isComplexNotMembership(AttributeDefinition attrDef,
                                            SchemaResource schemaResource) {
        if (!AttributeDefinition.Type.COMPLEX.equals(attrDef.getType())) {
            return false;
        }
        return !isMembershipReference(schemaResource.getId(), attrDef.getName());
    }

    private static boolean isMembershipReference(String schemaId, String attrName) {
        return (USER_SCHEMA_URN.equals(schemaId) && "groups".equals(attrName))
                || (GROUP_SCHEMA_URN.equals(schemaId) && "members".equals(attrName));
    }

    private static boolean isAlreadyDefined(AttributeDefinition scimAttr,
                                              RestObjectClassDefinitionBuilder objectClass) {
        if (!objectClass.findAttributes(a -> scimAttr.getName().equals(a.scim().name())).isEmpty()) {
            return true;
        }
        if (!objectClass.findAttributes(a -> scimAttr.getName().equals(a.name())).isEmpty()) {
            return true;
        }
        return false;
    }

    private void populateComplexAttribute(AttributeDefinition scimAttr,
                                           RestSchemaBuilderImpl schema,
                                           RestObjectClassDefinitionBuilder parentOc) {
        var complexAttr = parentOc.attribute(scimAttr.getName());

        String embeddedClassName = parentOc.name() + "__" + scimAttr.getName();
        var embeddedBuilder = schema.objectClass(embeddedClassName);
        embeddedBuilder.embedded(true);

        for (AttributeDefinition subAttr : scimAttr.getSubAttributes()) {
            if (AttributeDefinition.Type.COMPLEX.equals(subAttr.getType())) {
                continue;
            }
            var subAttribute = embeddedBuilder.attribute(subAttr.getName());
            subAttribute.scim().name(subAttr.getName());
            applySubAttributeRules(subAttribute, subAttr);
        }

        complexAttr.scim()
                .name(scimAttr.getName())
                .implementation(new ScimEmbeddedObjectValueMapping(contextLookup, embeddedClassName));
        complexAttr.connId()
                .type(EmbeddedObject.class)
                .referencedObjectClassName(detected(embeddedClassName))
                .multiValued(detected(scimAttr.isMultiValued()))
                .required(detected(scimAttr.isRequired()))
                .returnedByDefault(detected(
                        AttributeDefinition.Returned.DEFAULT.equals(scimAttr.getReturned())));
        complexAttr.connId()
                .roleInReference(detected(
                        AttributeInfo.RoleInReference.SUBJECT.toString()));
    }

    private void applySubAttributeRules(RestAttributeBuilderImpl attr,
                                         AttributeDefinition scimAttr) {
        switch (scimAttr.getType()) {
            case DATETIME:
                attr.scim().type("string");
                attr.scim().implementation(OpenApiValueMapping.DateTime);
                break;
            case DECIMAL:
                attr.scim().type("number");
                attr.scim().implementation(OpenApiValueMapping.Decimal);
                break;
            case BINARY:
                attr.scim().type("binary");
                attr.scim().implementation(OpenApiValueMapping.Binary);
                break;
            case REFERENCE:
                attr.scim().type("string");
                break;
            default:
                attr.scim().type(scimAttr.getType().getName());
                break;
        }
        attr.nativeType(scimAttr.getType().getName());
        if (scimAttr.getDescription() != null) {
            attr.connId().description(detected(scimAttr.getDescription()));
        }
        attr.connId().required(detected(scimAttr.isRequired()));
        attr.connId().multiValued(detected(scimAttr.isMultiValued()));
        attr.connId().returnedByDefault(detected(
                AttributeDefinition.Returned.DEFAULT.equals(scimAttr.getReturned())));

        switch (scimAttr.getMutability()) {
            case IMMUTABLE:
                attr.connId().readable(detected(true)).creatable(detected(true)).updatable(detected(false));
                break;
            case READ_ONLY:
                attr.connId().readable(detected(true)).creatable(detected(false)).updatable(detected(false));
                break;
            case READ_WRITE:
                attr.connId().readable(detected(true)).creatable(detected(true)).updatable(detected(true));
                break;
            case WRITE_ONLY:
                attr.connId().readable(detected(false)).creatable(detected(true)).updatable(detected(true));
                break;
        }
    }

    /**
     * Apply all matching attribute-level rules to the given attribute.
     */
    private void applyAttributeRules(ScimResourceContext resource,
                                      AttributeDefinition attrDef,
                                      RestObjectClassDefinitionBuilder objectClass,
                                      RestAttributeBuilderImpl attribute) {
        for (var rule : attributeRules) {
            if (rule.checkIfApplicable(resource, attrDef)) {
                var action = rule.createAction(resource, attrDef);
                if (action instanceof ScimMappingAction.AttributeSpecific attrSpecific) {
                    attrSpecific.applyToAttribute(objectClass, attribute);
                }
            }
        }
    }

    /**
     * Apply all matching resource-level rules to the given object class.
     */
    private void applyResourceRules(ScimResourceContext resource,
                                      RestObjectClassDefinitionBuilder objectClass) {
        for (var rule : resourceRules) {
            if (rule.checkIfApplicable(resource)) {
                var action = rule.createAction(resource);
                if (action != null) {
                    action.applyToSchema(objectClass);
                }
            }
        }
    }

    private void populatePathBasedSchema(ScimResourceContext scim, RestObjectClassDefinitionBuilder objectClass) {
        for (var attr : objectClass.allAttributes()) {
            var path = attr.scim().path();
            if (path == null || path.onlyAttribute() != null) {
                // Simple path — handled in primary schema processing
                continue;
            }
            var attrDef = scim.findAttributeDefinition(path);
            if (attrDef == null) {
                throw new IllegalStateException(String.format("Attribute '%s' not found", path));
            }
            applySubAttributeRules(attr, attrDef);
        }
    }

    private RestAttributeBuilderImpl findOrCreateAttribute(AttributeDefinition scimAttr,
                                                             RestObjectClassDefinitionBuilder objectClass,
                                                             boolean onlyListed) {
        for (var attr : objectClass.allAttributes()) {
            if (scimAttr.getName().equals(attr.scim().name())) {
                return attr;
            }
        }
        if (onlyListed) {
            return null;
        }
        return objectClass.attribute(scimAttr.getName());
    }

    private RestObjectClassDefinitionBuilder findOrCreateObjectClass(ResourceTypeResource scim,
                                                                       RestSchemaBuilderImpl schema) {
        for (var objClass : schema.allObjectClasses()) {
            if (objClass.embedded()) {
                // Skip embedded classes
            }
            if (scim.getName().equals(objClass.scim().name())) {
                return objClass;
            }
        }

        for (var objClass : schema.allObjectClasses()) {
            if (scim.getSchema().toString().equals(objClass.scim().schemaUri())) {
                return objClass;
            }
        }

        return schema.objectClass(scim.getName());
    }

    public Map<String, String> resourceToObjectClass() {
        return resourceToObjectClass;
    }

    public Map<String, ScimResourceContext> objectClassToResource() {
        return objectClassToResource;
    }
}
