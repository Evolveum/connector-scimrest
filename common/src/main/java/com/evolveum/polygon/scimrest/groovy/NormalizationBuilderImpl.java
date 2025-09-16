/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.groovy.api.NormalizationBuilder;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import groovy.lang.Closure;

import java.util.function.BiFunction;

public class NormalizationBuilderImpl implements NormalizationBuilder {


    private String attributeToNormalize;
    private BiFunction<String, Object, String> nameTransformer;
    private BiFunction<String, Object, String> uidTransformer;

    @Override
    public NormalizationBuilder toSingleValue(String attribute) {
        attributeToNormalize = attribute;
        return this;
    }

    @Override
    public NormalizationBuilder rewriteUid(Closure<?> implementation) {
        uidTransformer = new GroovyRewrite(implementation);
        return this;
    }

    @Override
    public NormalizationBuilder rewriteName(Closure<?> implementation) {
        nameTransformer = new GroovyRewrite(implementation);
        return this;
    }

    ExecuteQueryProcessor build(ExecuteQueryProcessor executeQueryProcessor) {

        return new NormalizationQueryProcessor(attributeToNormalize, executeQueryProcessor, nameTransformer, uidTransformer);
    }

    private record GroovyRewrite(Closure<?> implementation) implements BiFunction<String, Object, String> {


        @Override
        public String apply(String original, Object value) {
            return GroovyClosures.copyAndCall((Closure<String>) implementation, new RewriteContext(original, value));
        }
    }
}
