package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.JsonBodyHandler;
import com.evolveum.polygon.scim.rest.RestContext;
import org.json.JSONObject;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface SearchHandler {

    Iterable<JSONObject> extractRemoteObject(HttpResponse<JSONObject> response);

    void addUriAndPaging(RestContext.RequestBuilder requestBuilder, int currentPage, int pageLimit);

    static Builder builder() {
        return new Builder();
    }

    Integer extractTotalResultCount(HttpResponse<JSONObject> response);

    class Builder {

        private Function<HttpResponse<JSONObject>, Iterable<JSONObject>> extractor = v -> List.of(v.body());
        private BiConsumer<RestContext.RequestBuilder, PagingInfo> pagingConsumer = (req, resp) -> {};
        private Function<HttpResponse<JSONObject>, Integer> totalCountExtractor = (e) -> null;

        public Builder remoteObjectExtractor(Function<HttpResponse<JSONObject>, Iterable<JSONObject>> extractor) {
            this.extractor = extractor;
            return this;
        }

        public Builder addRequestUri(BiConsumer<RestContext.RequestBuilder, PagingInfo> pagingConsumer) {
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
                public void addUriAndPaging(RestContext.RequestBuilder requestBuilder, int currentPage, int pageLimit) {
                    pagingConsumer.accept(requestBuilder, new PagingInfo(pageLimit, currentPage));
                }

                @Override
                public Integer extractTotalResultCount(HttpResponse<JSONObject> response) {
                    return totalCountExtractor.apply(response);
                }
            };
        }
    }



}
