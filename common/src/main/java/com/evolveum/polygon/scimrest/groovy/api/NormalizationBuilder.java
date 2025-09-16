/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.scimrest.groovy.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface NormalizationBuilder {

    /**
     * Normalizes attribute to single value ones.
     *
     * This creates multiple objects from single object and UIDs needs to be recomputed.
     *
     * @param attribute
     * @return
     */
    NormalizationBuilder toSingleValue(String attribute);

    NormalizationBuilder rewriteUid(@DelegatesTo(value = RewriteContext.class) Closure<?> implementation);

    NormalizationBuilder rewriteName(@DelegatesTo(value = RewriteContext.class) Closure<?> implementation);



    record RewriteContext(String original, Object value) {
        public String getOriginal() {
            return original();
        }

        public Object getValue() {
            return value();
        }
    }
}
