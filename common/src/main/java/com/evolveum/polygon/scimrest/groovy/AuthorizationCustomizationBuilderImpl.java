package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthImplementationContext;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.JwtAssertionBuilder;
import com.evolveum.polygon.scimrest.impl.rest.JdkHttpRequestConverter;
import com.evolveum.polygon.scimrest.impl.rest.OAuth2TokenManager;
import groovy.lang.Closure;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuthorizationCustomizationBuilderImpl implements AuthenticationCustomizationBuilder {

    private final DispatchingAuthorizationCustomizer dispatcher = new DispatchingAuthorizationCustomizer();
    private final GroovyOAuth2TokenManager oauth2TokenManager = new GroovyOAuth2TokenManager();
    private final DispatchingScimAuthorizationCustomizer scimDispatcher = new DispatchingScimAuthorizationCustomizer();
    private final GroovyOAuth2TokenManager scimOAuth2TokenManager = new GroovyOAuth2TokenManager();

    private List<Class<? extends RestClientConfiguration>> restPreferenceOrder = List.of();
    private List<Class<? extends ScimClientConfiguration>> scimPreferenceOrder = List.of();

    public AuthorizationCustomizationBuilderImpl() {
        dispatcher.addBuiltinCustomizer(RestClientConfiguration.BasicAuthorization.class, (conf, request) -> {
            var basicConf = conf.require(RestClientConfiguration.BasicAuthorization.class);
            var tokenAccessor = new GuardedStringAccessor();
            basicConf.getRestPassword().access(tokenAccessor);
            String credentials = basicConf.getRestUsername() + ":" + tokenAccessor.getClearString();
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        });
        dispatcher.addBuiltinCustomizer(RestClientConfiguration.BearerTokenAuthorization.class, (conf, request) -> {
            var tokenConf = conf.require(RestClientConfiguration.BearerTokenAuthorization.class);
            var accessor = new GuardedStringAccessor();
            tokenConf.getRestTokenValue().access(accessor);
            request.header("Authorization", "Bearer " + accessor.getClearString());
        });
        dispatcher.addBuiltinCustomizer(RestClientConfiguration.JwtBearerAuthorization.class, (conf, request) -> {
            var jwtConf = conf.require(RestClientConfiguration.JwtBearerAuthorization.class);
            var accessor = new GuardedStringAccessor();
            jwtConf.getRestJwtSecret().access(accessor);
            String token = new JwtAssertionBuilder()
                    .claimsFromJson(jwtConf.getRestJwtPayload())
                    .sign(jwtConf.getRestJwtAlgorithm(), accessor.getClearString(), jwtConf.getRestJwtSecretBase64Encoded());
            if ("query".equalsIgnoreCase(jwtConf.getRestJwtLocalization())) {
                request.queryParameter(jwtConf.getRestJwtTokenName(), token);
            } else {
                request.header("Authorization", jwtConf.getRestJwtTokenName() + " " + token);
            }
        });
        dispatcher.addBuiltinCustomizer(RestClientConfiguration.ApiKeyAuthorization.class, (conf, request) -> {
            var apiKeyConf = conf.require(RestClientConfiguration.ApiKeyAuthorization.class);
            var accessor = new GuardedStringAccessor();
            apiKeyConf.getRestApiKey().access(accessor);
            if ("query".equalsIgnoreCase(apiKeyConf.getRestApiKeyLocation())) {
                request.queryParameter(apiKeyConf.getRestApiKeyName(), accessor.getClearString());
            } else {
                request.header(apiKeyConf.getRestApiKeyName(), accessor.getClearString());
            }
        });
        dispatcher.addBuiltinCustomizer(RestClientConfiguration.OAuth2ClientCredentialsAuthorization.class, (conf, request) -> {
            var oauth2Conf = conf.require(RestClientConfiguration.OAuth2ClientCredentialsAuthorization.class);
            oauth2TokenManager.applyToken(OAuth2TokenManager.OAuth2Config.from(oauth2Conf), request);
        });
        dispatcher.addBuiltinCustomizer(RestClientConfiguration.OAuth2JwtBearerAuthorization.class, (conf, request) -> {
            var oauth2Conf = conf.require(RestClientConfiguration.OAuth2JwtBearerAuthorization.class);
            oauth2TokenManager.applyToken(OAuth2TokenManager.OAuth2Config.from(oauth2Conf), request);
        });

        scimDispatcher.addBuiltinCustomizer(ScimClientConfiguration.OAuth2ClientCredentialsAuthorization.class, (conf, request) -> {
            var oauth2Conf = conf.require(ScimClientConfiguration.OAuth2ClientCredentialsAuthorization.class);
            scimOAuth2TokenManager.applyToken(OAuth2TokenManager.OAuth2Config.from(oauth2Conf), request);
        });
        scimDispatcher.addBuiltinCustomizer(ScimClientConfiguration.OAuth2JwtBearerAuthorization.class, (conf, request) -> {
            var oauth2Conf = conf.require(ScimClientConfiguration.OAuth2JwtBearerAuthorization.class);
            scimOAuth2TokenManager.applyToken(OAuth2TokenManager.OAuth2Config.from(oauth2Conf), request);
        });
        scimDispatcher.addBuiltinCustomizer(ScimClientConfiguration.BearerTokenAuthorization.class, (conf, request) -> {
            var tokenConf = conf.require(ScimClientConfiguration.BearerTokenAuthorization.class);
            var accessor = new GuardedStringAccessor();
            tokenConf.getScimTokenValue().access(accessor);
            request.header("Authorization", "Bearer " + accessor.getClearString());
        });
        scimDispatcher.addBuiltinCustomizer(ScimClientConfiguration.JwtBearerAuthorization.class, (conf, request) -> {
            var jwtConf = conf.require(ScimClientConfiguration.JwtBearerAuthorization.class);
            var accessor = new GuardedStringAccessor();
            jwtConf.getScimJwtSecret().access(accessor);
            String token = new JwtAssertionBuilder()
                    .claimsFromJson(jwtConf.getScimJwtPayload())
                    .sign(jwtConf.getScimJwtAlgorithm(), accessor.getClearString(), jwtConf.getScimJwtSecretBase64Encoded());
            if ("query".equalsIgnoreCase(jwtConf.getScimJwtLocalization())) {
                request.queryParameter(jwtConf.getScimJwtTokenName(), token);
            } else {
                request.header("Authorization", jwtConf.getScimJwtTokenName() + " " + token);
            }
        });
        scimDispatcher.addBuiltinCustomizer(ScimClientConfiguration.ApiKeyAuthorization.class, (conf, request) -> {
            var apiKeyConf = conf.require(ScimClientConfiguration.ApiKeyAuthorization.class);
            var accessor = new GuardedStringAccessor();
            apiKeyConf.getScimApiKey().access(accessor);
            if ("query".equalsIgnoreCase(apiKeyConf.getScimApiKeyLocation())) {
                request.queryParameter(apiKeyConf.getScimApiKeyName(), accessor.getClearString());
            } else {
                request.header(apiKeyConf.getScimApiKeyName(), accessor.getClearString());
            }
        });
        scimDispatcher.addBuiltinCustomizer(ScimClientConfiguration.HttpBasic.class, (conf, request) -> {
            var basic = conf.require(ScimClientConfiguration.HttpBasic.class);
            var accessor = new GuardedStringAccessor();
            basic.getScimPassword().access(accessor);
            String credentials = basic.getScimUsername() + ":" + accessor.getClearString();
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        });
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
        public void withImplementation(Class<? extends ScimClientConfiguration> type, Closure<?> o) {
            var builder = new ImplementationBuilderImpl();
            GroovyClosures.callAndReturnDelegate(o, builder);
            if (builder.implementationPrototype != null) {
                scimDispatcher.addCustomizer(type, new GroovyScimImplementationCustomizer(builder.implementationPrototype));
            }
        }

        @Override
        public void oauth2ClientCredentials(Closure<?> o) {
            var builder = new OAuth2BuilderImpl(scimOAuth2TokenManager);
            Closure<?> copy = (Closure<?>) o.clone();
            copy.setDelegate(builder);
            copy.setResolveStrategy(Closure.DELEGATE_FIRST);
            copy.call(scimOAuth2TokenManager.getAuthContext());
            if (builder.implementationPrototype != null) {
                scimDispatcher.addCustomizer(ScimClientConfiguration.OAuth2ClientCredentialsAuthorization.class,
                        new GroovyScimImplementationCustomizer(builder.implementationPrototype));
            } else {
                builder.apply();
            }
        }

        @Override
        public void oauth2JwtBearer(Closure<?> o) {
            var builder = new OAuth2BuilderImpl(scimOAuth2TokenManager);
            Closure<?> copy = (Closure<?>) o.clone();
            copy.setDelegate(builder);
            copy.setResolveStrategy(Closure.DELEGATE_FIRST);
            copy.call(scimOAuth2TokenManager.getAuthContext());
            if (builder.implementationPrototype != null) {
                scimDispatcher.addCustomizer(ScimClientConfiguration.OAuth2JwtBearerAuthorization.class,
                        new GroovyScimImplementationCustomizer(builder.implementationPrototype));
            } else {
                builder.apply();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void preference(Class<? extends ScimClientConfiguration>... types) {
            scimPreferenceOrder = List.of(types);
        }
    }

    class RestBuilderImpl implements RestBuilder {

        @Override
        public void withImplementation(Class<? extends RestClientConfiguration> type, Closure<?> o) {
            var builder = new ImplementationBuilderImpl();
            GroovyClosures.callAndReturnDelegate(o, builder);
            if (builder.implementationPrototype != null) {
                dispatcher.addCustomizer(type, new GroovyRestImplementationCustomizer(builder.implementationPrototype));
            }
        }

        @Override
        public void oauth2ClientCredentials(Closure<?> o) {
            var builder = new OAuth2BuilderImpl(oauth2TokenManager);
            Closure<?> copy = (Closure<?>) o.clone();
            copy.setDelegate(builder);
            copy.setResolveStrategy(Closure.DELEGATE_FIRST);
            copy.call(oauth2TokenManager.getAuthContext());
            if (builder.implementationPrototype != null) {
                dispatcher.addCustomizer(RestClientConfiguration.OAuth2ClientCredentialsAuthorization.class,
                        new GroovyRestImplementationCustomizer(builder.implementationPrototype));
            } else {
                builder.apply();
            }
        }

        @Override
        public void oauth2JwtBearer(Closure<?> o) {
            var builder = new OAuth2BuilderImpl(oauth2TokenManager);
            Closure<?> copy = (Closure<?>) o.clone();
            copy.setDelegate(builder);
            copy.setResolveStrategy(Closure.DELEGATE_FIRST);
            copy.call(oauth2TokenManager.getAuthContext());
            if (builder.implementationPrototype != null) {
                dispatcher.addCustomizer(RestClientConfiguration.OAuth2JwtBearerAuthorization.class,
                        new GroovyRestImplementationCustomizer(builder.implementationPrototype));
            } else {
                builder.apply();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void preference(Class<? extends RestClientConfiguration>... types) {
            restPreferenceOrder = List.of(types);
        }
    }

    RestBuilder rest() {
        return new RestBuilderImpl();
    }

    static class ImplementationBuilderImpl implements AuthenticationCustomizationBuilder.ImplementationBuilder {
        Closure<?> implementationPrototype;

        @Override
        public void implementation(Closure<?> closure) {
            this.implementationPrototype = closure;
        }
    }

    static class GroovyRestImplementationCustomizer implements AuthorizationCustomizer<RestClientConfiguration> {

        private final Closure<?> implementationPrototype;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        GroovyRestImplementationCustomizer(Closure<?> implementationPrototype) {
            this.implementationPrototype = implementationPrototype;
        }

        @Override
        public void customize(RestClientConfiguration configuration, HttpRequestSpecification request) {
            GroovyClosures.copyAndCall(implementationPrototype, new ExecutionContext(configuration, request));
        }

        class ExecutionContext implements AuthImplementationContext {
            private final RestClientConfiguration configuration;
            private final HttpRequestSpecification request;

            ExecutionContext(RestClientConfiguration configuration, HttpRequestSpecification request) {
                this.configuration = configuration;
                this.request = request;
            }

            @Override
            public Object getConfiguration() {
                return configuration;
            }

            @Override
            public HttpRequestSpecification getRequest() {
                return request;
            }

            @Override
            public Map<String, Object> getCtx() {
                return state;
            }

            @Override
            public String decrypt(GuardedString gs) {
                if (gs == null) return null;
                var accessor = new GuardedStringAccessor();
                gs.access(accessor);
                return accessor.getClearString();
            }

            @Override
            public HttpRequestSpecification newRequest(String url) {
                return new HttpRequestSpecification(url);
            }

            @Override
            public JwtAssertionBuilder newJwt() {
                return new JwtAssertionBuilder();
            }

            @Override
            public HttpResponse<String> execute(HttpRequestSpecification spec) {
                try {
                    var httpRequest = new JdkHttpRequestConverter().convert(spec);
                    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    throw new ConnectorException("customAuth: HTTP request failed: " + e.getMessage(), e);
                }
            }

            @Override
            public Object parseJson(String text) {
                return new groovy.json.JsonSlurper().parseText(text);
            }
        }
    }

    static class GroovyScimImplementationCustomizer implements AuthorizationCustomizer<ScimClientConfiguration> {

        private final Closure<?> implementationPrototype;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        GroovyScimImplementationCustomizer(Closure<?> implementationPrototype) {
            this.implementationPrototype = implementationPrototype;
        }

        @Override
        public void customize(ScimClientConfiguration configuration, HttpRequestSpecification request) {
            GroovyClosures.copyAndCall(implementationPrototype, new ExecutionContext(configuration, request));
        }

        class ExecutionContext implements AuthImplementationContext {
            private final ScimClientConfiguration configuration;
            private final HttpRequestSpecification request;

            ExecutionContext(ScimClientConfiguration configuration, HttpRequestSpecification request) {
                this.configuration = configuration;
                this.request = request;
            }

            @Override
            public Object getConfiguration() {
                return configuration;
            }

            @Override
            public HttpRequestSpecification getRequest() {
                return request;
            }

            @Override
            public Map<String, Object> getCtx() {
                return state;
            }

            @Override
            public String decrypt(GuardedString gs) {
                if (gs == null) return null;
                var accessor = new GuardedStringAccessor();
                gs.access(accessor);
                return accessor.getClearString();
            }

            @Override
            public HttpRequestSpecification newRequest(String url) {
                return new HttpRequestSpecification(url);
            }

            @Override
            public JwtAssertionBuilder newJwt() {
                return new JwtAssertionBuilder();
            }

            @Override
            public HttpResponse<String> execute(HttpRequestSpecification spec) {
                try {
                    var httpRequest = new JdkHttpRequestConverter().convert(spec);
                    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    throw new ConnectorException("customAuth: HTTP request failed: " + e.getMessage(), e);
                }
            }

            @Override
            public Object parseJson(String text) {
                return new groovy.json.JsonSlurper().parseText(text);
            }
        }
    }

    static class OAuth2BuilderImpl implements AuthenticationCustomizationBuilder.OAuth2Builder {

        private final GroovyOAuth2TokenManager tokenManager;

        private Closure<?> buildTokenRequestHook;
        private Closure<?> parseTokenResponseHook;
        private Closure<?> validateTokenHook;
        private Closure<?> applyTokenHook;
        Closure<?> implementationPrototype;

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

        @Override
        public AuthenticationCustomizationBuilder.OAuth2Builder implementation(Closure<?> closure) {
            this.implementationPrototype = closure;
            return this;
        }

        void apply() {
            if (buildTokenRequestHook != null) tokenManager.setBuildTokenRequestHook(buildTokenRequestHook);
            if (parseTokenResponseHook != null) tokenManager.setParseTokenResponseHook(parseTokenResponseHook);
            if (validateTokenHook != null) tokenManager.setValidateTokenHook(validateTokenHook);
            if (applyTokenHook != null) tokenManager.setApplyTokenHook(applyTokenHook);
        }
    }

    public AuthorizationCustomizer<RestClientConfiguration> restCustomizer() {
        if (restPreferenceOrder.isEmpty()) return dispatcher;
        return new AuthPreferenceManager<RestClientConfiguration>(
                restPreferenceOrder,
                dispatcher::applyForType,
                RestClientConfiguration::getBaseAddress,
                RestClientConfiguration::getRestTestEndpoint,
                conf -> Boolean.TRUE.equals(conf.getTrustAllCertificates())
        );
    }

    public AuthorizationCustomizer<ScimClientConfiguration> scimCustomizer() {
        if (scimPreferenceOrder.isEmpty()) return scimDispatcher;
        return new AuthPreferenceManager<ScimClientConfiguration>(
                scimPreferenceOrder,
                scimDispatcher::applyForType,
                ScimClientConfiguration::getScimBaseUrl,
                conf -> "/Schemas",
                conf -> conf instanceof RestClientConfiguration rc && Boolean.TRUE.equals(rc.getTrustAllCertificates())
        );
    }

    public List<Class<? extends RestClientConfiguration>> getRestPreferenceOrder() {
        return restPreferenceOrder;
    }

    public List<Class<? extends ScimClientConfiguration>> getScimPreferenceOrder() {
        return scimPreferenceOrder;
    }

}
