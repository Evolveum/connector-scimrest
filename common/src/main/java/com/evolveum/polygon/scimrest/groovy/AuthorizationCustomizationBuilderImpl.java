package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestDTO;
import com.evolveum.polygon.common.GuardedStringAccessor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import groovy.lang.Closure;

public class AuthorizationCustomizationBuilderImpl implements AuthenticationCustomizationBuilder {

    private final DispatchingAuthorizationCustomizer dispatcher = new DispatchingAuthorizationCustomizer();
    private final GroovyOAuth2TokenManager oauth2TokenManager = new GroovyOAuth2TokenManager();

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
        addCustomizer(RestClientConfiguration.OAuth2Authorization.class, (conf, request) -> {
            var oauth2Conf = conf.require(RestClientConfiguration.OAuth2Authorization.class);
            oauth2TokenManager.applyToken(oauth2Conf, request);
        });
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

            @Override
            public void oauth2(Closure<?> o) {
                var builder = new OAuth2BuilderImpl(oauth2TokenManager);
                Closure<?> copy = (Closure<?>) o.clone();
                copy.setDelegate(builder);
                copy.setResolveStrategy(Closure.DELEGATE_FIRST);
                copy.call(oauth2TokenManager.getOauth2Context());
                builder.apply();
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

    static class OAuth2BuilderImpl implements AuthenticationCustomizationBuilder.OAuth2Builder {

        private final GroovyOAuth2TokenManager tokenManager;

        private Closure<?> buildTokenRequestHook;
        private Closure<?> parseTokenResponseHook;
        private Closure<?> validateTokenHook;
        private Closure<?> applyTokenHook;

        OAuth2BuilderImpl(GroovyOAuth2TokenManager tokenManager) {
            this.tokenManager = tokenManager;
        }

        @Override
        public AuthenticationCustomizationBuilder.OAuth2Builder buildTokenRequest(Closure<?> hook) {
            this.buildTokenRequestHook = hook;
            return this;
        }

        @Override
        public AuthenticationCustomizationBuilder.OAuth2Builder parseTokenResponse(Closure<?> hook) {
            this.parseTokenResponseHook = hook;
            return this;
        }

        @Override
        public AuthenticationCustomizationBuilder.OAuth2Builder validateToken(Closure<?> hook) {
            this.validateTokenHook = hook;
            return this;
        }

        @Override
        public AuthenticationCustomizationBuilder.OAuth2Builder applyToken(Closure<?> hook) {
            this.applyTokenHook = hook;
            return this;
        }

        void apply() {
            if (buildTokenRequestHook != null)  tokenManager.setBuildTokenRequestHook(buildTokenRequestHook);
            if (parseTokenResponseHook != null) tokenManager.setParseTokenResponseHook(parseTokenResponseHook);
            if (validateTokenHook != null)      tokenManager.setValidateTokenHook(validateTokenHook);
            if (applyTokenHook != null)         tokenManager.setApplyTokenHook(applyTokenHook);
        }
    }

    public AuthorizationCustomizer<RestClientConfiguration> build() {
        return dispatcher;
    }
}
