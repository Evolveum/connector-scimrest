package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.groovy.api.PagingInfo;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;
import java.util.function.Function;

// FIXME Find proper name
public interface RestSearchOperationHandler<BF, OF> {

    Iterable<OF> extractRemoteObject(HttpResponse<BF> response);

    void addUriAndPaging(RestContext.RequestBuilder requestBuilder, int currentPage, int pageLimit);

    static <BF,OF> Builder<BF,OF> builder() {
        return new Builder<>();
    }

    Integer extractTotalResultCount(HttpResponse<BF> response);

    Class<?> responseType();

    class Builder<BF, OF> {

        private Function<HttpResponse<BF>, Iterable<OF>> extractor = null;
        private BiConsumer<RestContext.RequestBuilder, PagingInfo> pagingConsumer = (req, resp) -> {};
        private TotalCountExtractor<BF> totalCountExtractor = TotalCountExtractor.unsupported();
        private Class<?> responseType = JSONObject.class;

        public Builder<BF, OF> remoteObjectExtractor(Function<HttpResponse<BF>, Iterable<OF>> extractor) {
            this.extractor = extractor;
            return this;
        }

        public Builder<BF,OF> addRequestUri(BiConsumer<RestContext.RequestBuilder, PagingInfo> pagingConsumer) {
            this.pagingConsumer = pagingConsumer;
            return this;
        }
        public <T> Builder<T, OF> responseFormat(Class<T> responseFormat) {
            this.responseType = responseFormat;
            return (Builder) this;
        }

        public Builder<BF,OF> totalCountExtractor(TotalCountExtractor<BF> totalCountExtractor) {
            this.totalCountExtractor = totalCountExtractor;
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
                    return totalCountExtractor.extractTotalCount(response);
                }

                @Override
                public Class<?> responseType() {
                    return responseType;
                }
            };
        }


    }



}
