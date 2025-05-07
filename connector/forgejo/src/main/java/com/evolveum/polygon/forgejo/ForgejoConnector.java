package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scim.rest.RestContext;
import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;
import com.evolveum.polygon.scim.rest.groovy.*;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.Function;

public class ForgejoConnector extends AbstractGroovyRestConnector<ForgejoConfiguration> {

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/UserNativeSchema.groovy");
        loader.loadFromResource("/OrganizationNativeSchema.groovy");
        loader.loadFromResource("/ConnIdMapping.groovy");
    }

    @Override
    protected void initializeObjectClassHandler(RestHandlerBuilder builder) {
        //builder.loadFromResource("/OrganizationHandler.groovy");

        // FIXME: Remove once groovy handler lambdas have proposed necessary helpers.
        builder.objectClass("User")
                .search(new FilterRequestProcessor() {
                    Function<HttpResponse<JSONObject>, Iterable<JSONObject>> dataExtractor = r ->
                            (Iterable<JSONObject>) r.body().get("data");

                    @Override
                    public SearchHandler createRequest(Filter filter) {
                        if (isFilter(filter, EqualsFilter.class, "id")) {
                            return SearchHandler.builder()
                                    .addRequestUri((request, paging) -> {
                                        request.apiEndpoint("users/search")
                                                .query("uid", Objects.toString(getOnlyValue(filter)));
                                    })
                                    .remoteObjectExtractor(dataExtractor)
                                    .build();
                        }
                        if (isFilter(filter, EqualsFilter.class, "name")) {

                        }
                        if (filter == null ) {
                            return SearchHandler.builder()
                                    .addRequestUri((request, paging) -> {
                                        request.apiEndpoint("users/search");
                                        if (paging.pageSize() > 0) {
                                            request.query("limit", Objects.toString(paging.pageSize()));
                                        }
                                        if (paging.pageOffset() > 0) {
                                            request.query("page", Objects.toString(paging.pageOffset()));
                                        }
                                    })
                                    .remoteObjectExtractor(dataExtractor)
                                    .build();
                        }
                        throw new UnsupportedOperationException();
                    }
                });
    }

    @Override
    protected RestContext.AuthorizationCustomizer authorizationCustomizer() {
        return (c,request) -> {

            if (c instanceof HttpClientConfiguration.TokenAuthorization tokenAuth) {
                var tokenAccessor = new GuardedStringAccessor();
                tokenAuth.getAuthorizationTokenValue().access(tokenAccessor);
                request.header("Authentication", "token " + tokenAccessor.getClearString());
            }
        };
    }

    // FIXME: This should be some util methods in lambda executor
    private boolean isFilter(Filter filter, Class<? extends AttributeFilter> type, String id) {
        return type.isInstance(filter) && ((AttributeFilter) filter).getName().equals(id);
    }

    // FIXME: This should be some util methods in lambda executor
    private Object getOnlyValue(Filter filter) {
        if (filter instanceof AttributeFilter attrFilter) {
            return attrFilter.getAttribute().getValue().get(0);
        }
        return null;
    }
}
