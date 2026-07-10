/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.conndev.yaml.model.YamlNormalize;
import com.evolveum.polygon.scimrest.groovy.api.NormalizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchOperationBuilder;

/** Maps the {@code normalize} section onto {@link NormalizationBuilder}; scripts become closures. */
final class YamlNormalizeHandler {

    private final GroovyScriptCompiler scriptCompiler;

    YamlNormalizeHandler(GroovyScriptCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }

    void load(RestSearchOperationBuilder searchBuilder, YamlNormalize normalize) {
        if (normalize == null) {
            return;
        }
        NormalizationBuilder nb = searchBuilder.normalize();
        if (normalize.toSingleValue != null) {
            nb.toSingleValue(normalize.toSingleValue);
        }
        if (normalize.rewriteUid != null) {
            nb.rewriteUid(scriptCompiler.compile(normalize.rewriteUid));
        }
        if (normalize.rewriteName != null) {
            nb.rewriteName(scriptCompiler.compile(normalize.rewriteName));
        }
        if (normalize.restoreUid != null) {
            nb.restoreUid(scriptCompiler.compile(normalize.restoreUid));
        }
        if (normalize.restoreName != null) {
            nb.restoreName(scriptCompiler.compile(normalize.restoreName));
        }
    }
}
