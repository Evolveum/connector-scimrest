package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ContextLookup;
import com.evolveum.polygon.scim.rest.groovy.api.AttributeResolver;
import com.evolveum.polygon.scim.rest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    public void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        var coordinator = new AttributeResolutionCoordinator(resultsHandler);
        delegate.executeQuery(context, filter, coordinator, operationOptions);
    }

    private class AttributeResolutionCoordinator implements BatchAwareResultHandler {
        private final ResultsHandler delegate;

        List<ConnectorObjectBuilder> outstanding = new ArrayList<>();

        public AttributeResolutionCoordinator(ResultsHandler resultsHandler) {
            this.delegate = resultsHandler;
        }

        @Override
        public boolean handle(ConnectorObject original) {
            var builder = new ConnectorObjectBuilder().add(original);
            for (AttributeResolver resolver : perObject) {
                if (resolver.getSupportedAttributes().stream().anyMatch(a -> original.getAttributeByName(a.connId().getName()) == null)) {
                    resolver.resolveSingle(builder);
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
                resolver.resolve(batch);
            }

            for (ConnectorObjectBuilder builder : batch) {
                delegate.handle(builder.build());
            }
        }
    }

}
