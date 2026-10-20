/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.scimrest.groovy.api.EndpointBuilder;

/**
 * Binds a single-object endpoint's {@code pathParameters:} key — a mapping of path placeholder
 * name (the {@code {name}} segment of the endpoint's path) to an attribute name, or a null
 * value for the implicit identity (the placeholder is filled from the object's UID).
 * Placeholders whose attribute is mapped to the UID need no entry.
 */
public class PathParametersHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (!(target instanceof EndpointBuilder.SingleObject<?, ?> single)) {
            throw new IllegalArgumentException("The 'pathParameters' key requires a single-object endpoint, got: "
                    + target.getClass().getName());
        }
        if (value.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("The 'pathParameters' key requires a mapping of path placeholder"
                    + " name to attribute name at " + value.line() + ":" + value.col());
        }
        for (var entry : value.entries()) {
            var attribute = entry.value();
            if (attribute.isNull()) {
                single.pathParameter(entry.key());
            } else if (attribute.isValue()) {
                single.pathParameter(entry.key(),
                        DefinitionValue.from(attribute.text(),
                                binder.document().location(attribute.line(), attribute.col())));
            } else {
                throw new IllegalArgumentException("The path parameter '" + entry.key()
                        + "' requires a scalar attribute name or a null value at "
                        + attribute.line() + ":" + attribute.col());
            }
        }
    }
}
