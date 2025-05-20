package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.HashSet;
import java.util.Set;

public class FilterDispatchingEndpointHandler<BF, OF> implements SearchEndpointHandler<BF, OF> {

    private final ResponseObjectExtractor<BF,OF> objectExtractor;
    private final PagingHandler pagingSupport;
    private final String apiEndpoint;
    private final Set<FilterToRequestMapper> filterMappers;
    private final Class<?> responseFormat;
    private final TotalCountExtractor<BF> totalCountExtractor;

    public FilterDispatchingEndpointHandler(SearchEndpointBuilder<BF, OF> builder, Set<FilterToRequestMapper> filterMappers) {
        this.apiEndpoint = builder.path;
        this.objectExtractor = builder.objectExtractor;
        this.pagingSupport = builder.pagingSupport;
        this.filterMappers = new HashSet<>(filterMappers);
        this.responseFormat = builder.responseFormat;
        this.totalCountExtractor = builder.totalCountExtractor;
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
}
