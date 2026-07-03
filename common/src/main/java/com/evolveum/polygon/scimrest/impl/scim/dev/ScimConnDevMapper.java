/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.dev;

import com.evolveum.polygon.conndev.dev.ConnDevAttribute;
import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.objects.ConnectorObject;

import java.net.URI;
import java.util.Collection;
import java.util.Map;

/**
 * SCIM development-mode mapping: reads the <em>rich</em> SCIM source (scim2-sdk-client
 * {@link SchemaResource} / {@link ResourceTypeResource}) and emits the normalized
 * {@code conndev_ObjectClass} {@link ConnectorObject} via the shared {@link ConnDevObjectClass}
 * builder from conndev. References (SCIM reference attributes and group membership) are expressed
 * <em>on the attributes themselves</em> ({@code referencedObjectClass} / {@code role}), so the output
 * matches the SQL connector and every other conndev-based connector.
 */
public class ScimConnDevMapper {

    private record CoreMembership(String referencedObjectClass, String role) {
    }

    private static final Map<String, CoreMembership> CORE_MEMBERSHIP = Map.of(
            "groups", new CoreMembership("Group", "subject"),
            "members", new CoreMembership("User", "object"));

    /**
     * Links a resource type with its schemas into a single {@code conndev_ObjectClass}: the primary
     * schema plus every extension are merged into one object class (one SCIM entity = one endpoint =
     * one identity). Attributes from an extension are tagged with their own {@code namespace}.
     */
    public ConnectorObject toObjectClass(ResourceTypeResource resourceType, Map<String, SchemaResource> schemasByUrn) {
        var primaryUrn = asString(resourceType.getSchema());
        var objectClass = ConnDevObjectClass.objectClass(resourceType.getName())
                .uid(primaryUrn != null ? primaryUrn : resourceType.getName());
        if (resourceType.getEndpoint() != null) {
            objectClass.endpoint(resourceType.getEndpoint().toString());
        }
        if (primaryUrn != null) {
            objectClass.namespace(primaryUrn);
        }
        // primary schema: attributes inherit the object-class namespace, so they are not tagged
        collect(schemasByUrn.get(primaryUrn), null, objectClass);
        // extension schemas: attributes are tagged with the extension URN (a different "source")
        if (resourceType.getSchemaExtensions() != null) {
            for (var extension : resourceType.getSchemaExtensions()) {
                var extensionUrn = asString(extension.getSchema());
                collect(schemasByUrn.get(extensionUrn), extensionUrn, objectClass);
            }
        }
        return objectClass.build();
    }

    private void collect(SchemaResource schema, String namespace, ConnDevObjectClass objectClass) {
        if (schema == null || schema.getAttributes() == null) {
            return;
        }
        for (var definition : schema.getAttributes()) {
            toAttribute(definition, namespace, objectClass.attribute(definition.getName()));
        }
    }

    private void toAttribute(AttributeDefinition definition, String namespace, ConnDevAttribute target) {
        if (namespace != null) {
            target.namespace(namespace);
        }
        if (definition.getType() != null) {
            target.type(definition.getType().getName());
        }
        if (definition.isRequired()) {
            target.required(true);
        }
        if (definition.isMultiValued()) {
            target.multiValued(true);
        }
        addMutabilityFlags(definition, target);
        if (definition.getReturned() == AttributeDefinition.Returned.NEVER
                || definition.getReturned() == AttributeDefinition.Returned.REQUEST) {
            target.returnedByDefault(false);
        }
        // A reference/membership attribute points to another object class — expressed on the attribute.
        if (isReference(definition)) {
            var referenced = referencedObjectClass(definition);
            if (referenced != null) {
                target.referencedObjectClass(referenced);
            }
            var core = CORE_MEMBERSHIP.get(definition.getName());
            if (core != null) {
                target.role(core.role());
            }
        }
        if (definition.getType() == AttributeDefinition.Type.COMPLEX && definition.getSubAttributes() != null) {
            for (var sub : definition.getSubAttributes()) {
                toAttribute(sub, null, target.subAttribute(sub.getName()));
            }
        }
    }

    /**
     * A reference is a single SCIM reference attribute, or a complex membership attribute (e.g.
     * {@code groups}/{@code members}) — a complex attribute carrying a {@code $ref} sub-attribute.
     */
    private static boolean isReference(AttributeDefinition definition) {
        if (definition.getType() == AttributeDefinition.Type.REFERENCE) {
            return true;
        }
        return definition.getType() == AttributeDefinition.Type.COMPLEX
                && (referenceSubAttribute(definition) != null
                        || CORE_MEMBERSHIP.containsKey(definition.getName()));
    }

    private static AttributeDefinition referenceSubAttribute(AttributeDefinition definition) {
        if (definition.getSubAttributes() == null) {
            return null;
        }
        return definition.getSubAttributes().stream()
                .filter(sub -> "$ref".equals(sub.getName()) || sub.getType() == AttributeDefinition.Type.REFERENCE)
                .findFirst()
                .orElse(null);
    }

    /**
     * Target object class of a reference: from {@code referenceTypes} if the server declares them,
     * otherwise the SCIM-core convention for {@code groups}/{@code members}.
     */
    private static String referencedObjectClass(AttributeDefinition definition) {
        Collection<String> referenceTypes;
        if (definition.getType() == AttributeDefinition.Type.REFERENCE) {
            referenceTypes = definition.getReferenceTypes();
        } else {
            var ref = referenceSubAttribute(definition);
            referenceTypes = ref == null ? null : ref.getReferenceTypes();
        }
        if (referenceTypes != null && !referenceTypes.isEmpty()) {
            return referenceTypes.iterator().next();
        }
        var core = CORE_MEMBERSHIP.get(definition.getName());
        return core == null ? null : core.referencedObjectClass();
    }

    private static void addMutabilityFlags(AttributeDefinition definition, ConnDevAttribute target) {
        var mutability = definition.getMutability();
        if (mutability == null) {
            return;
        }
        switch (mutability) {
            case READ_ONLY -> target.creatable(false).updateable(false);
            case IMMUTABLE -> target.updateable(false);
            case WRITE_ONLY -> target.readable(false);
            case READ_WRITE -> {
            }
        }
    }

    private static String asString(URI uri) {
        return uri == null ? null : uri.toString();
    }
}
