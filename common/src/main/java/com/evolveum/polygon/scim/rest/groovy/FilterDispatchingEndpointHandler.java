package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.spi.SearchEndpointHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

public class FilterDispatchingEndpointHandler<BF, OF> implements SearchEndpointHandler<BF, OF> {

    private final ResponseObjectExtractor<BF,OF> objectExtractor;
    private final PagingHandler pagingSupport;
    private final String apiEndpoint;

    public FilterDispatchingEndpointHandler(SearchEndpointBuilder<BF, OF> builder) {
        this.apiEndpoint = builder.path;
        this.objectExtractor = builder.objectExtractor;
        this.pagingSupport = builder.pagingSupport;
    }


    @Override
    public RestSearchOperationHandler<BF,OF> process(Filter filter) {
        return RestSearchOperationHandler.<BF,OF>builder()
                .addRequestUri((request, paging) -> {
                    request.apiEndpoint(apiEndpoint);
                    if (pagingSupport != null) {
                        pagingSupport.handlePaging(request, paging);
                    }
                })
                .remoteObjectExtractor(objectExtractor::extractObjects)
                .build();
    }
}
