/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.ContextLookup;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import com.evolveum.polygon.scimrest.schema.RestAttributeBuilderImpl;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinitionBuilder;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.evolveum.polygon.scimrest.schema.ScimAttributeMappingRule;
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

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Translates SCIM schema metadata into Connector Framework schema definitions using a
 * rule-based approach. Strategies examine resource and attribute metadata and apply their
 * effect directly to the schema builder — see {@link ScimResourceMappingRule}/
 * {@link ScimAttributeMappingRule}.
 *
 * <p>{@link #populateSchema(ScimResourceContext, RestSchemaBuilderImpl)} only creates attributes
 * (identity only). The caller must call {@link #applyRules()} and then {@code build()} itself,
 * in that order.
 *
 * @see ScimResourceMappingRule
 * @see ScimAttributeMappingRule
 */
public class ScimSchemaTranslator {

    private Map<String, String> resourceToObjectClass = new HashMap<>();
    private Map<String, ScimResourceContext> objectClassToResource = new HashMap<>();

    private final ContextLookup contextLookup;

    private final List<ScimResourceMappingRule> resourceRules = new ArrayList<>();
    private final List<ScimAttributeMappingRule> attributeRules = new ArrayList<>();

    /** Object classes this translator correlated to a SCIM resource (see {@link #populateSchema}),
     * kept here rather than on the builder itself, since the correlation is this translator's
     * concern. */
    private final Map<RestObjectClassDefinitionBuilder, Correlation> correlated = new HashMap<>();

    private record Correlation(ScimResourceContext resource, boolean onlyListed) {}

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

        for (var scimAttr : scim.primarySchema().getAttributes()) {
            if (isComplexNotMembership(scimAttr, scim.primarySchema())) {
                if (!onlyListed && !isAlreadyDefined(scimAttr, objectClass)) {
                    populateComplexAttribute(scim, scimAttr, schema, objectClass);
                }
                continue;
            }

            // Attribute identity only here — rule evaluation and application is deferred to
            // #applyRules.
            var attribute = findOrCreateAttribute(scimAttr, objectClass, onlyListed);
            if (attribute != null) {
                attribute.scim().name(scimAttr.getName());
            }
        }

        // Defer rule dispatch to #applyRules — this resource's metadata must still be reachable
        // then, since it may run long after this resource is processed.
        correlated.put(objectClass, new Correlation(scim, onlyListed));

        // Path-based attributes from Groovy definitions
        populatePathBasedSchema(scim, objectClass);
    }

    /**
     * Applies {@link #applyRulesFor} to every object class this translator correlated to a SCIM
     * resource (see {@link #populateSchema}). Must be called before {@code build()}.
     */
    public void applyRules() {
        for (var entry : correlated.entrySet()) {
            applyRulesFor(entry.getValue().resource(), entry.getKey(), entry.getValue().onlyListed());
        }
    }

    /**
     * Evaluates and applies this translator's rules against the given resource.
     */
    public void applyRulesFor(ScimResourceContext scim, RestObjectClassDefinitionBuilder objectClass, boolean onlyListed) {
        for (var scimAttr : scim.primarySchema().getAttributes()) {
            if (isComplexNotMembership(scimAttr, scim.primarySchema())) {
                continue;
            }
            var attribute = findOrCreateAttribute(scimAttr, objectClass, onlyListed);
            if (attribute != null) {
                applyAttributeRules(scim, scimAttr, objectClass, attribute);
            }
        }

        // Resource-level rules (Uid detection etc.)
        applyResourceRules(scim, objectClass);

        // Path-based attributes from Groovy definitions
        populatePathBasedSchema(scim, objectClass);
    }

    private boolean isComplexNotMembership(AttributeDefinition attrDef,
                                            SchemaResource schemaResource) {
        if (!AttributeDefinition.Type.COMPLEX.equals(attrDef.getType())) {
            return false;
        }
        return !ScimMembershipReferenceRule.isMembershipReference(schemaResource.getId(), attrDef.getName());
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

    private void populateComplexAttribute(ScimResourceContext resource,
                                           AttributeDefinition scimAttr,
                                           RestSchemaBuilderImpl schema,
                                           RestObjectClassDefinitionBuilder parentOc) {
        var complexAttr = parentOc.attribute(scimAttr.getName());

        String embeddedClassName = parentOc.name() + "__" + scimAttr.getName();
        var embeddedBuilder = schema.objectClass(embeddedClassName);
        embeddedBuilder.embedded(true);

        var subAttributes = Objects.requireNonNullElse(scimAttr.getSubAttributes(), List.<AttributeDefinition>of());
        for (AttributeDefinition subAttr : subAttributes) {
            if (AttributeDefinition.Type.COMPLEX.equals(subAttr.getType())) {
                continue;
            }
            var subAttribute = embeddedBuilder.attribute(subAttr.getName());
            subAttribute.scim().name(subAttr.getName());
            applyAttributeRules(resource, subAttr, embeddedBuilder, subAttribute);
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

    /**
     * Apply all matching attribute-level rules to the given attribute.
     */
    private void applyAttributeRules(ScimResourceContext resource,
                                      AttributeDefinition attrDef,
                                      RestObjectClassDefinitionBuilder objectClass,
                                      RestAttributeBuilderImpl attribute) {
        var context = new ScimAttributeMappingRule.Context(resource, attrDef);
        for (var rule : attributeRules) {
            if (rule.checkIfApplicable(context, objectClass, attribute)) {
                var action = rule.createAction(context);
                if (action != null) {
                    action.applyToAttribute(attribute);
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
            if (rule.checkIfApplicable(resource, objectClass, null)) {
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
            if (path == null || path.actual().onlyAttribute() != null) {
                // Simple path — handled in primary schema processing
                continue;
            }
            var attrDef = scim.findAttributeDefinition(path.actual());
            if (attrDef == null) {
                // A schema mapping mismatch (the connector maps an attribute to a SCIM path the
                // server does not expose) — retries will not fix it.
                throw new ConfigurationException(String.format(
                        "Attribute path '%s' not found in the SCIM schema of resource '%s' — check the attribute's SCIM mapping",
                        path.actual(), scim.resource().getName()));
            }
            applyAttributeRules(scim, attrDef, objectClass, attr);
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
