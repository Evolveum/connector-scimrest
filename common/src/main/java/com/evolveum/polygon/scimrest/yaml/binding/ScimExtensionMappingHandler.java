/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.scimrest.groovy.api.RestObjectClassSchemaBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Binds the object-class SCIM mapping {@code extensions:} key — the YAML counterpart of the
 * Groovy {@code scim { extension(alias, namespace) { flatten "..." } }} calls: a map of extension
 * aliases, each mapping to a schema URI scalar or to a mapping with a required {@code uri} and an
 * optional {@code flatten} list (same scalar-or-sequence shape as the object-class {@code flatten}
 * key). Each entry is bound by invoking the mapping's {@code extension(alias, namespace, flatten)}
 * method.
 */
public class ScimExtensionMappingHandler implements CustomYamlHandler {

    private static final Set<String> ENTRY_KEYS = Set.of("uri", "flatten");

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestObjectClassSchemaBuilder.ScimMapping scim)) {
            throw new IllegalArgumentException("The 'extensions' key requires an object-class SCIM mapping, got: "
                    + (target == null ? "null" : target.getClass().getName()));
        }
        if (value == null || value.isNull()) {
            return;
        }
        if (value.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("Expected a map of SCIM extensions for the 'extensions' key but found a "
                    + value.kind() + " at " + value.line() + ":" + value.col());
        }
        for (var entry : value.entries()) {
            bindExtension(scim, entry);
        }
    }

    private void bindExtension(RestObjectClassSchemaBuilder.ScimMapping scim, LocatedNode.Entry entry) {
        var alias = entry.key();
        var node = entry.value();
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("The extension '" + alias + "' of the 'extensions' key must declare its schema URI"
                    + " at " + entry.keyLine() + ":" + entry.keyCol());
        }
        if (node.kind() == LocatedNode.Kind.SCALAR) {
            scim.extension(alias, node.text(), List.of());
            return;
        }
        if (node.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("Expected a schema URI or a mapping for the extension '" + alias
                    + "' of the 'extensions' key but found a " + node.kind() + " at " + node.line() + ":" + node.col());
        }
        for (var field : node.entries()) {
            if (!ENTRY_KEYS.contains(field.key())) {
                throw new IllegalArgumentException("Unknown key '" + field.key() + "' for the extension '" + alias
                        + "' of the 'extensions' key at " + field.keyLine() + ":" + field.keyCol());
            }
        }
        var uriNode = node.get("uri");
        if (uriNode == null || !uriNode.isValue()) {
            throw new IllegalArgumentException("The extension '" + alias + "' of the 'extensions' key is missing its "
                    + "required 'uri' at " + node.line() + ":" + node.col());
        }
        var flattenNode = node.get("flatten");
        if (flattenNode == null || flattenNode.isNull()) {
            scim.extension(alias, uriNode.text(), List.of());
            return;
        }
        scim.extension(alias, uriNode.text(), flattenAttributes(alias, flattenNode));
    }

    /** The extension's flatten list: a single scalar name or a sequence of names (mirrors {@code ScimFlattenListHandler}). */
    private List<String> flattenAttributes(String alias, LocatedNode node) {
        var attributes = new ArrayList<String>();
        if (node.kind() == LocatedNode.Kind.SCALAR) {
            attributes.add(node.text());
            return attributes;
        }
        if (node.kind() != LocatedNode.Kind.ARRAY) {
            throw new IllegalArgumentException("Expected a SCIM attribute name or a list of names for the 'flatten' key "
                    + "of extension '" + alias + "' but found a " + node.kind() + " at " + node.line() + ":" + node.col());
        }
        for (var item : node.elements()) {
            if (!item.isValue()) {
                throw new IllegalArgumentException("Expected a list of SCIM attribute names for the 'flatten' key "
                        + "of extension '" + alias + "' but found a " + item.kind() + " at " + item.line() + ":" + item.col());
            }
            attributes.add(item.text());
        }
        return attributes;
    }
}
