package com.evolveum.polygon.scim.rest.groovy;

import org.json.JSONObject;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface SearchHandler {

    Iterable<JSONObject> extractRemoteObject(HttpResponse<JSONObject> response);

    void addUriAndPaging(HttpRequest.Builder requestBuilder,  int currentPage, int pageLimit);

    static Builder builder() {
        return new Builder();
    }

    class Builder {

        private Function<HttpResponse<JSONObject>, Iterable<JSONObject>> extractor = v -> List.of(v.body());
        private BiConsumer<HttpRequest.Builder, PagingInfo> pagingConsumer = (req, resp) -> {};

        public Builder remoteObjectExtractor(Function<HttpResponse<JSONObject>, Iterable<JSONObject>> extractor) {
            this.extractor = extractor;
            return this;
        }

        public Builder addRequestUri(BiConsumer<HttpRequest.Builder, PagingInfo> pagingConsumer) {
            this.pagingConsumer = pagingConsumer;
            return this;
        }

        public SearchHandler build() {
            return new SearchHandler() {

                @Override
                public Iterable<JSONObject> extractRemoteObject(HttpResponse<JSONObject> response) {
                    return extractor.apply(response);
                }

                @Override
                public void addUriAndPaging(HttpRequest.Builder requestBuilder, int currentPage, int pageLimit) {
                    pagingConsumer.accept(requestBuilder, new PagingInfo(pageLimit, currentPage));
                }
            };
        }
    }



}
