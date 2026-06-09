package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.Script;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface AuthenticationCustomizationBuilder {

    RestBuilder rest(@DelegatesTo(RestBuilder.class) @Script.Initialization Closure<?> closure);

    ScimBuilder scim(@DelegatesTo(ScimBuilder.class) @Script.Initialization Closure<?> closure);

    interface RestBuilder {

        void withImplementation(Class<? extends RestClientConfiguration> type,
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o);

        default void basic(
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o) {
            withImplementation(RestClientConfiguration.BasicAuthorization.class, o);
        }

        default void bearer(
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o) {
            withImplementation(RestClientConfiguration.BearerTokenAuthorization.class, o);
        }

        default void jwtBearer(
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o) {
            withImplementation(RestClientConfiguration.JwtBearerAuthorization.class, o);
        }

        default void apiKey(
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o) {
            withImplementation(RestClientConfiguration.ApiKeyAuthorization.class, o);
        }

        void oauth2ClientCredentials(
                @DelegatesTo(value = OAuth2Builder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o);

        void oauth2JwtBearer(
                @DelegatesTo(value = OAuth2Builder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o);

        void oauth2Password(
                @DelegatesTo(value = OAuth2Builder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o);

        @SuppressWarnings("unchecked")
        void preference(Class<? extends RestClientConfiguration>... types);

        default Class<? extends RestClientConfiguration> getBasic() {
            return RestClientConfiguration.BasicAuthorization.class;
        }

        default Class<? extends RestClientConfiguration> getBearer() {
            return RestClientConfiguration.BearerTokenAuthorization.class;
        }

        default Class<? extends RestClientConfiguration> getJwtBearer() {
            return RestClientConfiguration.JwtBearerAuthorization.class;
        }

        default Class<? extends RestClientConfiguration> getApiKey() {
            return RestClientConfiguration.ApiKeyAuthorization.class;
        }

        default Class<? extends RestClientConfiguration> getOauth2ClientCredentials() {
            return RestClientConfiguration.OAuth2ClientCredentialsAuthorization.class;
        }

        default Class<? extends RestClientConfiguration> getOauth2JwtBearer() {
            return RestClientConfiguration.OAuth2JwtBearerAuthorization.class;
        }

        default Class<? extends RestClientConfiguration> getOauth2Password() {
            return RestClientConfiguration.OAuth2PasswordAuthorization.class;
        }
    }

    interface ScimBuilder {

        void withImplementation(Class<? extends ScimClientConfiguration> type,
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o);

        default void bearer(
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o) {
            withImplementation(ScimClientConfiguration.BearerTokenAuthorization.class, o);
        }

        default void jwtBearer(
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o) {
            withImplementation(ScimClientConfiguration.JwtBearerAuthorization.class, o);
        }

        default void apiKey(
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o) {
            withImplementation(ScimClientConfiguration.ApiKeyAuthorization.class, o);
        }

        default void basic(
                @DelegatesTo(value = ImplementationBuilder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o) {
            withImplementation(ScimClientConfiguration.BasicAuthorization.class, o);
        }

        void oauth2ClientCredentials(
                @DelegatesTo(value = OAuth2Builder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o);

        void oauth2JwtBearer(
                @DelegatesTo(value = OAuth2Builder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o);

        void oauth2Password(
                @DelegatesTo(value = OAuth2Builder.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Initialization Closure<?> o);

        @SuppressWarnings("unchecked")
        void preference(Class<? extends ScimClientConfiguration>... types);

        default Class<? extends ScimClientConfiguration> getBearer() {
            return ScimClientConfiguration.BearerTokenAuthorization.class;
        }

        default Class<? extends ScimClientConfiguration> getJwtBearer() {
            return ScimClientConfiguration.JwtBearerAuthorization.class;
        }

        default Class<? extends ScimClientConfiguration> getApiKey() {
            return ScimClientConfiguration.ApiKeyAuthorization.class;
        }

        default Class<? extends ScimClientConfiguration> getBasic() {
            return ScimClientConfiguration.BasicAuthorization.class;
        }

        default Class<? extends ScimClientConfiguration> getOauth2ClientCredentials() {
            return ScimClientConfiguration.OAuth2ClientCredentialsAuthorization.class;
        }

        default Class<? extends ScimClientConfiguration> getOauth2JwtBearer() {
            return ScimClientConfiguration.OAuth2JwtBearerAuthorization.class;
        }

        default Class<? extends ScimClientConfiguration> getOauth2Password() {
            return ScimClientConfiguration.OAuth2PasswordAuthorization.class;
        }
    }

    interface OAuth2Builder {
        OAuth2Builder buildTokenRequest(@Script.Runtime Closure<?> hook);
        OAuth2Builder parseTokenResponse(@Script.Runtime Closure<?> hook);
        OAuth2Builder validateToken(@Script.Runtime Closure<?> hook);
        OAuth2Builder applyToken(@Script.Runtime Closure<?> hook);

        OAuth2Builder implementation(
                @DelegatesTo(value = AuthImplementationContext.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Runtime Closure<?> closure);
    }

    interface ImplementationBuilder {
        void implementation(
                @DelegatesTo(value = AuthImplementationContext.class, strategy = Closure.DELEGATE_FIRST)
                @Script.Runtime Closure<?> closure);
    }
}
