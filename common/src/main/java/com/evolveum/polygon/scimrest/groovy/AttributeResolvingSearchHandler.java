/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.api.AttributeResolver;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.evolveum.polygon.scimrest.groovy.api.AttributeResolver.SKIP_ATTRIBUTE_RESOLUTION_OPTION;

public class AttributeResolvingSearchHandler implements ExecuteQueryProcessor {

    private final ExecuteQueryProcessor delegate;
    private final Set<AttributeResolver> perObject;
    private final Set<AttributeResolver> batched;

    private Set<AttributeResolver> attributeResolvers;

    public AttributeResolvingSearchHandler(ExecuteQueryProcessor delegate, Set<AttributeResolver> perObjectResolvers, Set<AttributeResolver> batchedResolvers) {
        this.delegate = delegate;
        this.perObject = perObjectResolvers;
        this.batched = batchedResolvers;
    }

    @Override
    public void executeQuery(ContextLookup context, Filter filter, ResultsHandler userHandler, OperationOptions operationOptions) {
        var handler = userHandler;
        if (shouldPerformAttributeResolution(operationOptions)) {
            handler = new AttributeResolutionCoordinator(context, userHandler, operationOptions);
        }
        delegate.executeQuery(context, filter, handler, operationOptions);
    }

    private boolean shouldPerformAttributeResolution(OperationOptions operationOptions) {
        if (operationOptions == null) {
            return true;
        }
        var skipResolution = operationOptions.getOptions().get(SKIP_ATTRIBUTE_RESOLUTION_OPTION);
        return !Boolean.TRUE.equals(skipResolution);
    }

    private class AttributeResolutionCoordinator implements BatchAwareResultHandler {
        private final ResultsHandler delegate;
        private final ContextLookup contextLookup;

        List<ConnectorObjectBuilder> outstanding = new ArrayList<>();

        public AttributeResolutionCoordinator(ContextLookup lookup, ResultsHandler resultsHandler, OperationOptions operationOptions) {
            this.delegate = resultsHandler;
            this.contextLookup = lookup;
        }

        @Override
        public boolean handle(ConnectorObject original) {
            var builder = new ConnectorObjectBuilder().add(original);
            for (AttributeResolver resolver : perObject) {
                if (resolver.getSupportedAttributes().stream().anyMatch(a -> original.getAttributeByName(a.connId().getName()) == null)) {
                    resolver.resolveSingle(contextLookup, builder);
                }
            }
            if (batched.isEmpty()) {
                delegate.handle(builder.build());
            } else {
                outstanding.add(builder);
            }
            return true;
        }

        @Override
        public void batchFinished() {
            var batch = outstanding;
            outstanding = new ArrayList<>();

            for (AttributeResolver resolver : batched) {
                resolver.resolve(contextLookup, batch);
            }

            for (ConnectorObjectBuilder builder : batch) {
                delegate.handle(builder.build());
            }
        }
    }

}
