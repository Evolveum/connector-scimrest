package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.common.GuardedStringAccessor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.impl.rest.OAuth2Context;
import groovy.lang.Closure;

public class AuthorizationCustomizationBuilderImpl implements AuthenticationCustomizationBuilder {

    private final DispatchingAuthorizationCustomizer dispatcher = new DispatchingAuthorizationCustomizer();
    private final GroovyOAuth2TokenManager oauth2TokenManager = new GroovyOAuth2TokenManager();
    private final DispatchingScimAuthorizationCustomizer scimDispatcher = new DispatchingScimAuthorizationCustomizer();
    private final GroovyOAuth2TokenManager scimOAuth2TokenManager = new GroovyOAuth2TokenManager();

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
            oauth2TokenManager.applyToken(OAuth2Context.Config.from(oauth2Conf), request);
        });

        addScimCustomizer(ScimClientConfiguration.OAuth2Authorization.class, (conf, request) -> {
            var oauth2Conf = conf.require(ScimClientConfiguration.OAuth2Authorization.class);
            scimOAuth2TokenManager.applyToken(OAuth2Context.Config.from(oauth2Conf), request);
        });
        addScimCustomizer(ScimClientConfiguration.BearerToken.class, (conf, request) -> {
            var bearer = conf.require(ScimClientConfiguration.BearerToken.class);
            var accessor = new GuardedStringAccessor();
            bearer.getScimBearerToken().access(accessor);
            request.header("Authorization", "Bearer " + accessor.getClearString());
        });
        addScimCustomizer(ScimClientConfiguration.HttpBasic.class, (conf, request) -> {
            var basic = conf.require(ScimClientConfiguration.HttpBasic.class);
            var accessor = new GuardedStringAccessor();
            basic.getScimPassword().access(accessor);
            String credentials = basic.getScimUsername() + ":" + accessor.getClearString();
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        });
    }

    public void addCustomizer(Class<? extends RestClientConfiguration> clazz, AuthorizationCustomizer<RestClientConfiguration> customizer) {
        dispatcher.addCustomizer(clazz, customizer);
    }

    public void addScimCustomizer(Class<? extends ScimClientConfiguration> clazz, AuthorizationCustomizer<ScimClientConfiguration> customizer) {
        scimDispatcher.addCustomizer(clazz, customizer);
    }

    @Override
    public RestBuilder rest(Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, rest());
    }

    @Override
    public ScimBuilder scim(Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, scim());
    }

    ScimBuilder scim() {
        return new ScimBuilderImpl();
    }

    class ScimBuilderImpl implements ScimBuilder {
        @Override
        public AuthorizationCustomizer<ScimClientConfiguration> customizer(Class<? extends ScimClientConfiguration> type, Closure<?> o) {
            var customizer = new GroovyScimAuthorizationCustomizer(o);
            scimDispatcher.addCustomizer(type, customizer);
            return customizer;
        }

        @Override
        public void oauth2(Closure<?> o) {
            var builder = new OAuth2BuilderImpl(scimOAuth2TokenManager);
            Closure<?> copy = (Closure<?>) o.clone();
            copy.setDelegate(builder);
            copy.setResolveStrategy(Closure.DELEGATE_FIRST);
            copy.call(scimOAuth2TokenManager.getOauth2Context());
            builder.apply();
        }
    }

    static class GroovyScimAuthorizationCustomizer implements AuthorizationCustomizer<ScimClientConfiguration> {
        private final Closure<?> closure;

        public GroovyScimAuthorizationCustomizer(Closure<?> closure) {
            this.closure = closure;
        }

        @Override
        public void customize(ScimClientConfiguration configuration, HttpRequestSpecification request) {
            GroovyClosures.copyAndCall(closure, new Context(request, configuration));
        }

        private record Context(HttpRequestSpecification request, ScimClientConfiguration configuration)
                implements AuthenticationCustomizationBuilder.ScimCustomizationContext {
            @Override
            public ScimClientConfiguration getConfiguration() { return configuration; }
            @Override
            public HttpRequestSpecification getRequest() { return request; }
        }
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
        public void customize(RestClientConfiguration configuration, HttpRequestSpecification request) {
            GroovyClosures.copyAndCall(closure, new Context(request, configuration));
        }

        private record Context(HttpRequestSpecification request,
                               RestClientConfiguration configuration) implements AuthenticationCustomizationBuilder.CustomizationContext {

            @Override
            public RestClientConfiguration getConfiguration() {
                return configuration;
            }

            @Override
            public HttpRequestSpecification getRequest() {
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

    public AuthorizationCustomizer<RestClientConfiguration> restCustomizer() {
        return dispatcher;
    }

    public AuthorizationCustomizer<ScimClientConfiguration> scimCustomizer() {
        return scimDispatcher;
    }
}
