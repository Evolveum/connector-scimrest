package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface RestSearchOperationHandler<BF, OF> {

    Iterable<OF> extractRemoteObject(HttpResponse<BF> response);

    void addUriAndPaging(RestContext.RequestBuilder requestBuilder, int currentPage, int pageLimit);

    static <BF,OF> Builder<BF,OF> builder() {
        return new Builder<>();
    }

    Integer extractTotalResultCount(HttpResponse<BF> response);

    class Builder<BF, OF> {

        private Function<HttpResponse<BF>, Iterable<OF>> extractor = null;
        private BiConsumer<RestContext.RequestBuilder, PagingInfo> pagingConsumer = (req, resp) -> {};
        private Function<HttpResponse<BF>, Integer> totalCountExtractor = (e) -> null;

        public Builder<BF, OF> remoteObjectExtractor(Function<HttpResponse<BF>, Iterable<OF>> extractor) {
            this.extractor = extractor;
            return this;
        }

        public Builder<BF,OF> addRequestUri(BiConsumer<RestContext.RequestBuilder, PagingInfo> pagingConsumer) {
            this.pagingConsumer = pagingConsumer;
            return this;
        }

        public RestSearchOperationHandler<BF,OF> build() {
            return new RestSearchOperationHandler<BF,OF>() {

                @Override
                public Iterable<OF> extractRemoteObject(HttpResponse<BF> response) {
                    return extractor.apply(response);
                }

                @Override
                public void addUriAndPaging(RestContext.RequestBuilder requestBuilder, int currentPage, int pageLimit) {
                    pagingConsumer.accept(requestBuilder, new PagingInfo(pageLimit, currentPage));
                }

                @Override
                public Integer extractTotalResultCount(HttpResponse<BF> response) {
                    return totalCountExtractor.apply(response);
                }
            };
        }
    }



}
