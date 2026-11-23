/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Computes the URNs for the {@code schemas} attribute of a SCIM resource write body (create or
 * {@code PUT} full replacement), as required by RFC 7643/7644.
 *
 * <p>The list is determined from the endpoint and the set attributes: the resource's primary
 * schema (identified by the endpoint / resource type the request is sent to) is always declared,
 * plus the URN of every schema extension referenced by an attribute that is actually being
 * written. A leading {@link AttributePath.Extension} component of an attribute's SCIM path names
 * the extension schema whose attributes it carries (RFC 7643 merges those attributes into the
 * resource's top level, so the extension is declared rather than nested).</p>
 */
public final class ScimSchemas {

    private ScimSchemas() {
    }

    /**
     * Determines the {@code schemas} URNs for a SCIM resource write body.
     *
     * @param resource       the discovered SCIM resource context (primary schema + endpoint)
     * @param attributeNames the ConnId names of the attributes being written
     * @param objectClassDef the object-class definition (attribute / SCIM path resolution)
     * @return the {@code schemas} URNs, primary schema first, deduplicated
     */
    public static List<String> determine(ScimResourceContext resource, Collection<String> attributeNames,
                                         RestObjectClassDefinition objectClassDef) {
        var urns = new LinkedHashSet<String>();
        urns.add(primaryUrn(resource));
        for (var name : attributeNames) {
            var definition = objectClassDef.attributeFromConnIdName(name);
            if (definition == null) {
                continue;
            }
            var mapping = definition.scim();
            if (mapping == null) {
                continue;
            }
            var path = mapping.path();
            if (path == null || path.components().isEmpty()) {
                continue;
            }
            if (path.components().getFirst() instanceof AttributePath.Extension extension) {
                urns.add(extension.name());
            }
        }
        return List.copyOf(urns);
    }

    /** The primary schema URN for the given resource, from its discovered schema or resource type. */
    private static String primaryUrn(ScimResourceContext resource) {
        var primary = resource.primarySchema();
        if (primary != null && primary.getId() != null) {
            return primary.getId();
        }
        return resource.resource().getSchema().toString();
    }
}
