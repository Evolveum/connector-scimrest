package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import groovy.lang.Closure;

import java.util.Base64;

public class AuthorizationCustomizationBuilderImpl implements AuthenticationCustomizationBuilder {

    private final DispatchingAuthorizationCustomizer dispatcher = new DispatchingAuthorizationCustomizer();

    public AuthorizationCustomizationBuilderImpl() {
        addCustomizer(RestClientConfiguration.BasicAuthorization.class, (conf, request) -> {
                var basicConf = conf.require(RestClientConfiguration.BasicAuthorization.class);
                var tokenAccessor = new GuardedStringAccessor();
                basicConf.getRestPassword().access(tokenAccessor);
                request.basicAuthorization(basicConf.getRestUsername(), tokenAccessor.getClearString());
                });
        addCustomizer(RestClientConfiguration.TokenAuthorization.class, (conf, request) -> {
            var tokenConf = conf.require(RestClientConfiguration.TokenAuthorization.class);
            var decryptor = new GuardedStringAccessor();
            tokenConf.getRestTokenValue().access(decryptor);
            request.header("Authorization", "Bearer " + decryptor.getClearString());
        });
    }



    public void addCustomizer(Class<? extends RestClientConfiguration> clazz, RestContext.AuthorizationCustomizer customizer) {
        dispatcher.addCustomizer(clazz, customizer);
    }

    @Override
    public RestBuilder rest(Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, rest());
    }

    RestBuilder rest() {
        return new RestBuilder() {
            @Override
            public RestContext.AuthorizationCustomizer customizer(Class<? extends RestClientConfiguration> type, Closure<?> o) {
                var customizer = new GroovyAuthorizationCustomizer(o);
                dispatcher.addCustomizer(type, customizer);
                return customizer;
            }
        };
    }

    static class GroovyAuthorizationCustomizer implements RestContext.AuthorizationCustomizer {
        private final Closure<?> closure;

        public GroovyAuthorizationCustomizer(Closure<?> closure) {
            this.closure = closure;
        }

        @Override
        public void customize(RestClientConfiguration configuration, RestContext.RequestBuilder request) {
            GroovyClosures.copyAndCall(closure, new Context(request, configuration));
        }

        private record Context(RestContext.RequestBuilder request,
                               RestClientConfiguration configuration) implements AuthenticationCustomizationBuilder.CustomizationContext {


            @Override
            public RestClientConfiguration getConfiguration() {
                return configuration;
            }

            @Override
            public RestContext.RequestBuilder getRequest() {
                return request;
            }
        }
    }

    public RestContext.AuthorizationCustomizer build() {
        return dispatcher;
    }

}
