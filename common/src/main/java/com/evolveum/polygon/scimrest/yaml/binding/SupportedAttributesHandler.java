/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;

/**
 * Binds a {@code supportedAttributes:} block onto an update endpoint. Each list item is either a
 * bare attribute name (the short form, e.g. {@code - displayName}) or a mapping with a {@code name}
 * and optional {@code value}/{@code transition} ({@code from}/{@code to}) selectors.
 */
public class SupportedAttributesHandler implements CustomYamlHandler {

    @SuppressWarnings("rawtypes")
    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof UpdateOperationBuilder.AttributeSpecific specific)) {
            throw new IllegalArgumentException("The 'supportedAttributes' block requires an update endpoint, got: "
                    + target.getClass().getName());
        }
        for (var item : value.elements()) {
            if (item.isValue()) {
                specific.supportedAttribute(item.text());
                continue;
            }
            var name = requireScalar(item, "name");
            var filter = (UpdateOperationBuilder.AttributeValueFilter) specific.supportedAttribute(name);
            var val = item.get("value");
            if (val != null && val.isValue()) {
                filter.value(scalar(val));
            }
            var transition = item.get("transition");
            if (transition != null && transition.kind() == LocatedNode.Kind.OBJECT) {
                filter.transition(scalar(transition.get("from")), scalar(transition.get("to")));
            }
        }
    }

    private static Object scalar(LocatedNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.text();
    }

    private static String requireScalar(LocatedNode map, String key) {
        var node = map.get(key);
        if (node == null || !node.isValue()) {
            throw new IllegalArgumentException("Missing required key '" + key + "' in supportedAttribute at "
                    + map.line() + ":" + map.col());
        }
        return node.text();
    }
}
