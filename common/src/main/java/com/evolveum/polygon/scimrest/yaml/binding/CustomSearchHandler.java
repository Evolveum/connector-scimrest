/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;

import java.util.ArrayList;

/**
 * Binds a search {@code custom:} block onto the {@code SearchScriptBuilder} returned by
 * {@code custom()}. {@code supportedFilters} is a list (no {@code @Yaml.*} kind expresses that
 * shape), so it is handled here: each entry carries a build-time {@code spec}, evaluated against
 * the script builder to yield a {@link FilterSpecification}. The remaining keys
 * ({@code implementation}, {@code emptyFilterSupported}) bind declaratively via
 * {@code SearchScriptBuilder}'s own {@code @Yaml.*} annotations.
 */
public class CustomSearchHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchOperationBuilder search)) {
            throw new IllegalArgumentException("The 'custom' block requires a search operation builder, got: "
                    + target.getClass().getName());
        }
        var custom = search.custom();
        var remaining = new ArrayList<LocatedNode.Entry>();
        for (var entry : value.entries()) {
            if ("supportedFilters".equals(entry.key())) {
                bindSupportedFilters(binder, custom, entry.value());
            } else {
                remaining.add(entry);
            }
        }
        binder.bindEntries(remaining, custom);
    }

    @Override
    public void checkGroovySyntax(LocatedNode value, String path, GroovySyntaxChecker checker) {
        if (value == null || value.isNull()) {
            return;
        }
        var remaining = new ArrayList<LocatedNode.Entry>();
        for (var entry : value.entries()) {
            if ("supportedFilters".equals(entry.key())) {
                checkSupportedFilters(entry.value(), path + ".supportedFilters", checker);
            } else {
                remaining.add(entry);
            }
        }
        // The remaining keys (implementation, emptyFilterSupported, ...) bind onto
        // SearchScriptBuilder via its own @Yaml.* shape, same as apply()'s
        // binder.bindEntries(remaining, custom).
        checker.checkFragments(remaining, SearchScriptBuilder.class, path);
    }

    private static void checkSupportedFilters(LocatedNode list, String listPath, GroovySyntaxChecker checker) {
        if (list == null || list.isNull()) {
            return;
        }
        int i = 0;
        for (var item : list.elements()) {
            var spec = item.get("spec");
            if (spec != null) {
                checker.checkFragment(spec, listPath + "[" + i + "].spec");
            }
            i++;
        }
    }

    private static void bindSupportedFilters(DeclYamlBinder binder, SearchScriptBuilder custom, LocatedNode list) {
        for (var item : list.elements()) {
            var spec = item.get("spec");
            if (spec == null || !spec.isValue()) {
                throw new IllegalArgumentException("Missing required key 'spec' in custom supportedFilter at "
                        + item.line() + ":" + item.col());
            }
            custom.supportedFilter((FilterSpecification) binder.evaluate(spec.text(), custom));
        }
    }
}
