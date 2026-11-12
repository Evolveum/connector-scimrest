/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.scimrest.config.RelationshipContext;
import com.evolveum.polygon.scimrest.groovy.api.PagingSupportBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RelationshipOperationBuilder;

/**
Binds each relationship through the existing YAML engine, attaching diagnostic context before any fields are applied.
The endpoint is read from the whole declaration so errors do not depend on YAML key order.
*/
public class RelationshipRecordsHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.kind() != LocatedNode.Kind.OBJECT) {
            throw new RelationshipContext(null, null).invalid("Expected a mapping for the 'relationshipRecords' block but found a " + value.kind()
                    + " at " + binder.document().location(value.line(), value.col()));
        }
        var operation = (RelationshipOperationBuilder<?>) target;
        for (var entry : value.entries()) {
            var context = context(entry);
            try {
                binder.locationOf(entry).<RuntimeException>run(() -> {
                    var declaration = operation.relationshipRecords(entry.key());
                    binder.bind(entry.value(), declaration);
                });
            } catch (RuntimeException exception) {
                throw context.invalid(exception.getMessage() + " (" + binder.locationOf(entry) + ")", exception);
            }
        }
    }

    @Override
    public void checkGroovySyntax(LocatedNode value, String path, GroovySyntaxChecker checker) {
        if (value == null || value.isNull()) {
            return;
        }
        for (var entry : value.entries()) {
            // Paging is the only relationship field with an existing script syntax check.
            checker.checkFragments(entry.value().entries(), PagingSupportBuilder.class,
                    path + "." + entry.key() + " [" + context(entry).description() + "]");
        }
    }

    private static RelationshipContext context(LocatedNode.Entry entry) {
        String endpoint = null;
        for (var field : entry.value().entries()) {
            if ("endpoint".equals(field.key()) && !field.value().isNull()) {
                endpoint = field.value().isValue() ? field.value().text() : "<invalid value>";
            }
        }
        return new RelationshipContext(entry.key(), endpoint);
    }
}
