package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ContextLookup;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.RestPagingAwareObjectRetriever;
import com.evolveum.polygon.scim.rest.schema.MappedObjectClass;
import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.HashSet;
import java.util.Set;

public class EndpointBasedSearchHandler<BF, OF> implements SearchEndpointHandler<BF, OF>, FilterAwareExecuteQueryProcessor {

    private MappedObjectClass objectClass;
    private final ResponseObjectExtractor<BF,OF> objectExtractor;
    private final PagingHandler pagingSupport;
    private final String apiEndpoint;
    private final Set<FilterToRequestMapper> filterMappers;
    private final Class<?> responseFormat;
    private final TotalCountExtractor<BF> totalCountExtractor;

    public EndpointBasedSearchHandler(EndpointBasedSearchBuilder<BF, OF> builder, Set<FilterToRequestMapper> filterMappers) {
        this.objectClass = builder.objectClass;
        this.apiEndpoint = builder.path;
        this.objectExtractor = builder.objectExtractor;
        this.pagingSupport = builder.pagingSupport;
        this.filterMappers = new HashSet<>(filterMappers);
        this.responseFormat = builder.responseFormat;
        this.totalCountExtractor = builder.totalCountExtractor;
    }


    @Override
    public boolean supports(Filter filter) {
        return filterMappers.stream().anyMatch(m -> m.canHandle(filter));
    }

    @Override
    public RestSearchOperationHandler process(Filter filter) {
        var maybeMapper = filterMappers.stream().filter(m -> m.canHandle(filter)).findFirst();
        if  (maybeMapper.isEmpty()) {
            return null;
        }
        var mapper = maybeMapper.get();
        return RestSearchOperationHandler.<BF,OF>builder()
                .addRequestUri((request, paging) -> {
                    request.apiEndpoint(apiEndpoint);
                    mapper.mapToRequest(request, filter);
                    if (pagingSupport != null) {
                        pagingSupport.handlePaging(request, paging);
                    }
                })
                .remoteObjectExtractor(objectExtractor::extractObjects)
                .totalCountExtractor(this.totalCountExtractor)
                .responseFormat(responseFormat)
                .build();
    }

    @Override
    public void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        // FIXME: naive refactor for now, reimplement to be better
        var spec = process(filter);
        if (spec != null) {
            new RestPagingAwareObjectRetriever(objectClass, spec).fetch(context, filter, resultsHandler, operationOptions);

        } else {
            throw new IllegalStateException("Cannot execute query");
        };
    }
}
