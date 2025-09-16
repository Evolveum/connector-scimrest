/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *  
 */

package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.HashSet;
import java.util.function.BiFunction;

public class NormalizationQueryProcessor implements ExecuteQueryProcessor {

    private final String propertyToNormalize;
    private final ExecuteQueryProcessor delegate;
    private final BiFunction<String, Object, String> nameTransformer;
    private final BiFunction<String, Object, String> uidTransformer;

    public NormalizationQueryProcessor(String propertyToNormalize, ExecuteQueryProcessor executeQueryProcessor, BiFunction<String, Object, String> nameTransformer, BiFunction<String, Object, String> uidTransformer) {
        this.propertyToNormalize = propertyToNormalize;
        this.delegate = executeQueryProcessor;
        this.nameTransformer = nameTransformer;
        this.uidTransformer = uidTransformer;
    }

    @Override
    public void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        // FIXME: We should rewrite filter here
        delegate.executeQuery(context, filter, new TransformationHandler(resultsHandler, filter), operationOptions);
    }

    private class TransformationHandler implements ResultsHandler {
        private final ResultsHandler delegate;
        private final Filter filter;

        public TransformationHandler(ResultsHandler resultsHandler, Filter filter) {
            this.delegate = resultsHandler;
            this.filter = filter;
        }

        @Override
        public boolean handle(ConnectorObject original) {
            var common = new HashSet<>(original.getAttributes());

            var uid = original.getUid();
            var name = original.getName();
            var toNormalize = original.getAttributeByName(propertyToNormalize);
            // FIXME: What should happen if normalization attribute is not present?

            common.remove(uid);
            common.remove(name);
            common.remove(toNormalize);

            for (var value : toNormalize.getValue()) {
                var newName = computeName(name.getNameValue(), value);
                var newUid = computeUid(uid.getUidValue(), value);
                var newAttr = AttributeBuilder.build(toNormalize.getName(), value);
                var newAttributes = new HashSet<>(common);
                newAttributes.add(newUid);
                newAttributes.add(newName);
                newAttributes.add(newAttr);

                var newObj = new ConnectorObject(original.getObjectClass(), newAttributes);
                if (matchesFilter(newObj)) {
                    if (!delegate.handle(newObj)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean matchesFilter(ConnectorObject newObj) {
            if (filter == null) {
                return true;
            }
            return filter.accept(newObj);
        }
    }

    Name computeName(String original, Object value) {
        return new Name(nameTransformer.apply(original, value));
    }

    Uid computeUid(String original, Object value) {
        return new Uid(uidTransformer.apply(original, value));
    }
}
