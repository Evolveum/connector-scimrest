/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;

/**
 * Binds the object-class SCIM mapping {@code flatten:} key — the YAML counterpart of the Groovy
 * {@code scim { flatten "..." }} calls: a sequence of complex-attribute names (or a single scalar
 * name) that are flattened into plain attributes of the object class. Each entry is bound by
 * invoking the mapping's {@code flatten(String)} method.
 */
public class ScimFlattenListHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestObjectClassSchemaBuilder.ScimMapping scim)) {
            throw new IllegalArgumentException("The 'flatten' key requires an object-class SCIM mapping, got: "
                    + (target == null ? "null" : target.getClass().getName()));
        }
        if (value == null || value.isNull()) {
            return;
        }
        if (value.kind() == LocatedNode.Kind.SCALAR) {
            scim.flatten(value.text());
            return;
        }
        if (value.kind() != LocatedNode.Kind.ARRAY) {
            throw new IllegalArgumentException("Expected a list of SCIM attribute names for the 'flatten' key but found a "
                    + value.kind() + " at " + value.line() + ":" + value.col());
        }
        for (var item : value.elements()) {
            if (!item.isValue()) {
                throw new IllegalArgumentException("Expected a list of SCIM attribute names for the 'flatten' key but found a "
                        + item.kind() + " at " + item.line() + ":" + item.col());
            }
            scim.flatten(item.text());
        }
    }
}
