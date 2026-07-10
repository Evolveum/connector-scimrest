/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.conndev.yaml.model.YamlCustom;
import com.evolveum.polygon.conndev.yaml.model.YamlSupportedFilter;
import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.SearchScriptBuilder;

/** Maps the fully scripted {@code custom} search section onto {@link SearchScriptBuilder}. */
final class YamlCustomSearchHandler {

    private final GroovyScriptCompiler scriptCompiler;

    YamlCustomSearchHandler(GroovyScriptCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }

    void load(RestSearchOperationBuilder searchBuilder, YamlCustom custom) {
        if (custom == null) {
            return;
        }
        SearchScriptBuilder sb = searchBuilder.custom();
        if (Boolean.TRUE.equals(custom.emptyFilterSupported)) {
            sb.emptyFilterSupported(true);
        }
        if (custom.supportedFilters != null) {
            for (YamlSupportedFilter filter : custom.supportedFilters) {
                sb.supportedFilter((FilterSpecification) scriptCompiler.evaluate(filter.spec, sb));
            }
        }
        if (custom.implementation != null) {
            sb.implementation(scriptCompiler.compile(custom.implementation));
        }
    }
}
