/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.search;

import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.groovy.api.PagingInfo;
import com.evolveum.polygon.scimrest.spi.TotalCountExtractor;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

// FIXME Find proper name
public interface RestSearchOperationHandler<BF, OF> {

    Iterable<OF> extractRemoteObject(HttpResponse<BF> response);

    void addUriAndPaging(HttpRequestSpecification requestBuilder, int currentPage, int pageLimit);

    void addUriOnly(HttpRequestSpecification requestBuilder);

    static <BF,OF> Builder<BF,OF> builder() {
        return new Builder<>();
    }

    Integer extractTotalResultCount(HttpResponse<BF> response);

    Class<?> responseType();

    Integer responsePageLimit();

    class Builder<BF, OF> {

        private Function<HttpResponse<BF>, Iterable<OF>> extractor = null;
        private BiConsumer<HttpRequestSpecification, PagingInfo> pagingConsumer = (req, resp) -> {};
        private Consumer<HttpRequestSpecification> uriConsumer = (req) -> {};
        private TotalCountExtractor<BF> totalCountExtractor = TotalCountExtractor.unsupported();
        private Class<?> responseType = JSONObject.class;
        private Integer responsePageLimit = null;

        public Builder<BF, OF> remoteObjectExtractor(Function<HttpResponse<BF>, Iterable<OF>> extractor) {
            this.extractor = extractor;
            return this;
        }

        public Builder<BF,OF> addRequestUri(BiConsumer<HttpRequestSpecification, PagingInfo> pagingConsumer) {
            this.pagingConsumer = pagingConsumer;
            return this;
        }

        public Builder<BF,OF> addRequestUri(Consumer<HttpRequestSpecification> uriConsumer) {
            this.uriConsumer = uriConsumer;
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

        public Builder<BF, OF> responsePageLimit(Integer responsePageLimit) {
            this.responsePageLimit = responsePageLimit;
            return this;
        }

        public RestSearchOperationHandler<BF,OF> build() {
            return new RestSearchOperationHandler<BF,OF>() {

                @Override
                public Iterable<OF> extractRemoteObject(HttpResponse<BF> response) {
                    return extractor.apply(response);
                }

                @Override
                public void addUriAndPaging(HttpRequestSpecification requestBuilder, int currentPage, int pageLimit) {
                    pagingConsumer.accept(requestBuilder, new PagingInfo(pageLimit, currentPage));
                }

                @Override
                public void addUriOnly(HttpRequestSpecification requestBuilder) {
                    uriConsumer.accept(requestBuilder);
                }

                @Override
                public Integer extractTotalResultCount(HttpResponse<BF> response) {
                    return totalCountExtractor.extractTotalCount(response);
                }

                @Override
                public Class<?> responseType() {
                    return responseType;
                }

                @Override
                public Integer responsePageLimit() {
                    return responsePageLimit;
                }
            };
        }
    }
}
