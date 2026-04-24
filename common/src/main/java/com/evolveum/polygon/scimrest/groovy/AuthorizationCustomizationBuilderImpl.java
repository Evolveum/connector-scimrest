package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestDTO;
import com.evolveum.polygon.common.GuardedStringAccessor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.impl.rest.OAuth2TokenManager;
import groovy.lang.Closure;

public class AuthorizationCustomizationBuilderImpl implements AuthenticationCustomizationBuilder {

    private final DispatchingAuthorizationCustomizer dispatcher = new DispatchingAuthorizationCustomizer();

    public AuthorizationCustomizationBuilderImpl() {
        addCustomizer(RestClientConfiguration.BasicAuthorization.class, (conf, request) -> {
                var basicConf = conf.require(RestClientConfiguration.BasicAuthorization.class);
                var tokenAccessor = new GuardedStringAccessor();
                basicConf.getRestPassword().access(tokenAccessor);
                String credentials = basicConf.getRestUsername() + ":" + tokenAccessor.getClearString();
                request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
                });
        addCustomizer(RestClientConfiguration.TokenAuthorization.class, (conf, request) -> {
            var tokenConf = conf.require(RestClientConfiguration.TokenAuthorization.class);
            var decryptor = new GuardedStringAccessor();
            tokenConf.getRestTokenValue().access(decryptor);
            request.header("Authorization", "Bearer " + decryptor.getClearString());
        });
        addCustomizer(RestClientConfiguration.OAuth2Authorization.class, new OAuth2AuthorizationCustomizer());
    }

    public void addCustomizer(Class<? extends RestClientConfiguration> clazz, AuthorizationCustomizer<RestClientConfiguration> customizer) {
        dispatcher.addCustomizer(clazz, customizer);
    }

    @Override
    public RestBuilder rest(Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, rest());
    }

    RestBuilder rest() {
        return new RestBuilder() {
            @Override
            public AuthorizationCustomizer<RestClientConfiguration> customizer(Class<? extends RestClientConfiguration> type, Closure<?> o) {
                var customizer = new GroovyAuthorizationCustomizer(o);
                dispatcher.addCustomizer(type, customizer);
                return customizer;
            }
        };
    }

    static class GroovyAuthorizationCustomizer implements AuthorizationCustomizer<RestClientConfiguration> {
        private final Closure<?> closure;

        public GroovyAuthorizationCustomizer(Closure<?> closure) {
            this.closure = closure;
        }

        @Override
        public void customize(RestClientConfiguration configuration, HttpRequestDTO request) {
            GroovyClosures.copyAndCall(closure, new Context(request, configuration));
        }

        private record Context(HttpRequestDTO request,
                               RestClientConfiguration configuration) implements AuthenticationCustomizationBuilder.CustomizationContext {


            @Override
            public RestClientConfiguration getConfiguration() {
                return configuration;
            }

            @Override
            public HttpRequestDTO getRequest() {
                return request;
            }
        }
    }

    private static class OAuth2AuthorizationCustomizer implements RestContext.AuthorizationCustomizer {

        private final OAuth2TokenManager tokenManager = new OAuth2TokenManager();

        @Override
        public void customize(RestClientConfiguration configuration, HttpRequestDTO request) {
            var context = new Context(request, configuration, tokenManager);
            var oauth2Conf = context.getConfiguration().require(RestClientConfiguration.OAuth2Authorization.class);
            context.tokenManager().applyToken(oauth2Conf, context.getRequest());
        }

        private record Context(HttpRequestDTO request,
                               RestClientConfiguration configuration,
                               OAuth2TokenManager tokenManager) implements AuthenticationCustomizationBuilder.CustomizationContext {

            @Override
            public RestClientConfiguration getConfiguration() {
                return configuration;
            }

            @Override
            public HttpRequestDTO getRequest() {
                return request;
            }
        }
    }

    public AuthorizationCustomizer<RestClientConfiguration> build() {
        return dispatcher;
    }
}
