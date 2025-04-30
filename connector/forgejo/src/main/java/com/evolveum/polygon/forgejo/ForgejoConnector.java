package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.scim.rest.groovy.*;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.function.Function;

public class ForgejoConnector extends AbstractGroovyRestConnector {

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/UserSchema.groovy");
    }

    @Override
    protected void initializeObjectClassHandler(HandlersBuilder builder) {
        builder.objectClass("User")
                .search(new FilterRequestProcessor() {
                    Function<HttpResponse<JSONObject>, Iterable<JSONObject>> dataExtractor = r ->
                            (Iterable<JSONObject>) r.body().get("data");


                    @Override
                    public SearchHandler createRequest(Filter filter) {
                        if (isFilter(filter, EqualsFilter.class, "id")) {
                            return SearchHandler.builder()
                                    .addRequestUri((request, paging) -> {
                                        request.uri(URI.create("/users/search?uid="));
                                    })
                                    .remoteObjectExtractor(dataExtractor)
                                    .build();
                        }
                        if (isFilter(filter, EqualsFilter.class, "name")) {

                        }
                        throw new UnsupportedOperationException();
                    }
                });
    }

    private boolean isFilter(Filter filter, Class<? extends AttributeFilter> type, String id) {
        return type.isInstance(filter) && ((AttributeFilter) filter).getName().equals(id);
    }
}
