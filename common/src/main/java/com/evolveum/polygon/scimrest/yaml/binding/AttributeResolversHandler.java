/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;

/**
 * Binds a search {@code attributeResolvers:} list onto the search operation. It only handles the
 * list shape itself (no {@code @Yaml.*} kind expresses "a list of sub-builders created by a
 * no-arg factory"); each entry's own fields ({@code attribute}/{@code resolutionType}/
 * {@code implementation}) bind declaratively via {@code AttributeResolverBuilder}'s own
 * {@code @Yaml.*} annotations, the same way {@code attributes:} entries bind onto
 * {@code AttributeBuilder}.
 */
public class AttributeResolversHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchOperationBuilder search)) {
            throw new IllegalArgumentException("The 'attributeResolvers' block requires a search operation builder, got: "
                    + target.getClass().getName());
        }
        for (var item : value.elements()) {
            binder.bind(item, search.attributeResolver());
        }
    }
}
