/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.RestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder.OAuth2Builder;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder.RestBuilder;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder.ScimBuilder;
import com.evolveum.polygon.scimrest.yaml.model.YamlAuthMethod;
import com.evolveum.polygon.scimrest.yaml.model.YamlAuthMethods;
import com.evolveum.polygon.scimrest.yaml.model.YamlAuthentication;
import com.evolveum.polygon.scimrest.yaml.model.YamlOAuth2;
import groovy.lang.Closure;

import java.util.function.Consumer;

/**
 * Maps the typed {@code authentication} tree onto the closure-free auth builder entry points, for both
 * the {@code rest} and {@code scim} channels. Reuses the Groovy runtime: every block (implementation,
 * OAuth2 token-flow hooks) is compiled into a {@code Closure} by {@link GroovyScriptCompiler}. OAuth2
 * methods are driven by handing the existing closure-taking {@code oauth2*(...)} a small driver closure
 * that sets the hooks on the {@link OAuth2Builder}, so the builder's own finalization still runs.
 */
final class YamlAuthenticationHandler {

    private final RestHandlerBuilder builder;
    private final GroovyScriptCompiler scriptCompiler;

    YamlAuthenticationHandler(RestHandlerBuilder builder, GroovyScriptCompiler scriptCompiler) {
        this.builder = builder;
        this.scriptCompiler = scriptCompiler;
    }

    void load(YamlAuthentication authentication) {
        if (authentication == null) {
            return;
        }
        AuthenticationCustomizationBuilder auth = builder.authentication();
        if (authentication.rest != null) {
            loadRestAuth(auth.rest(), authentication.rest);
        }
        if (authentication.scim != null) {
            loadScimAuth(auth.scim(), authentication.scim);
        }
    }

    // --- rest channel --------------------------------------------------------

    private void loadRestAuth(RestBuilder rest, YamlAuthMethods restAuth) {
        registerRest(rest, rest.getApiKey(), restAuth.apiKey);
        registerRest(rest, rest.getBasic(), restAuth.basic);
        registerRest(rest, rest.getBearer(), restAuth.bearer);
        registerRest(rest, rest.getJwtBearer(), restAuth.jwtBearer);
        loadOAuth2(rest::oauth2ClientCredentials, restAuth.oauth2ClientCredentials);
        loadOAuth2(rest::oauth2JwtBearer, restAuth.oauth2JwtBearer);
        loadOAuth2(rest::oauth2Password, restAuth.oauth2Password);
        loadOAuth2(rest::oauth2Saml, restAuth.oauth2Saml);
        if (restAuth.preference != null) {
            rest.preference(restAuth.preference.stream().map(m -> restPreferenceType(rest, m)).toArray(Class[]::new));
        }
    }

    private void registerRest(RestBuilder rest, Class<? extends RestClientConfiguration> type, YamlAuthMethod method) {
        if (method != null && method.implementation != null) {
            rest.implementation(type, scriptCompiler.compile(method.implementation));
        }
    }

    private Class<? extends RestClientConfiguration> restPreferenceType(RestBuilder rest, String method) {
        return switch (method) {
            case "apiKey" -> rest.getApiKey();
            case "basic" -> rest.getBasic();
            case "bearer" -> rest.getBearer();
            case "jwtBearer" -> rest.getJwtBearer();
            case "oauth2ClientCredentials" -> rest.getOauth2ClientCredentials();
            case "oauth2JwtBearer" -> rest.getOauth2JwtBearer();
            case "oauth2Password" -> rest.getOauth2Password();
            case "oauth2Saml" -> rest.getOauth2Saml();
            case "awsSignature" -> rest.getAwsSignature();
            default -> throw new IllegalArgumentException("Unknown auth method in preference: " + method);
        };
    }

    // --- scim channel --------------------------------------------------------

    private void loadScimAuth(ScimBuilder scim, YamlAuthMethods scimAuth) {
        registerScim(scim, scim.getApiKey(), scimAuth.apiKey);
        registerScim(scim, scim.getBasic(), scimAuth.basic);
        registerScim(scim, scim.getBearer(), scimAuth.bearer);
        registerScim(scim, scim.getJwtBearer(), scimAuth.jwtBearer);
        loadOAuth2(scim::oauth2ClientCredentials, scimAuth.oauth2ClientCredentials);
        loadOAuth2(scim::oauth2JwtBearer, scimAuth.oauth2JwtBearer);
        loadOAuth2(scim::oauth2Password, scimAuth.oauth2Password);
        loadOAuth2(scim::oauth2Saml, scimAuth.oauth2Saml);
        if (scimAuth.preference != null) {
            scim.preference(scimAuth.preference.stream().map(m -> scimPreferenceType(scim, m)).toArray(Class[]::new));
        }
    }

    private void registerScim(ScimBuilder scim, Class<? extends ScimClientConfiguration> type, YamlAuthMethod method) {
        if (method != null && method.implementation != null) {
            scim.implementation(type, scriptCompiler.compile(method.implementation));
        }
    }

    private Class<? extends ScimClientConfiguration> scimPreferenceType(ScimBuilder scim, String method) {
        return switch (method) {
            case "apiKey" -> scim.getApiKey();
            case "basic" -> scim.getBasic();
            case "bearer" -> scim.getBearer();
            case "jwtBearer" -> scim.getJwtBearer();
            case "oauth2ClientCredentials" -> scim.getOauth2ClientCredentials();
            case "oauth2JwtBearer" -> scim.getOauth2JwtBearer();
            case "oauth2Password" -> scim.getOauth2Password();
            case "oauth2Saml" -> scim.getOauth2Saml();
            case "awsSignature" -> scim.getAwsSignature();
            default -> throw new IllegalArgumentException("Unknown auth method in preference: " + method);
        };
    }

    // --- OAuth2 (shared by both channels) ------------------------------------

    private void loadOAuth2(Consumer<Closure<?>> oauth2Method, YamlOAuth2 oauth2) {
        if (oauth2 == null) {
            return;
        }
        oauth2Method.accept(oauth2Driver(oauth2));
    }

    /**
     * A driver closure handed to the existing {@code oauth2*(Closure)} method: when invoked (with the
     * {@link OAuth2Builder} as delegate) it sets each configured token-flow hook from a compiled snippet.
     * The hooks receive their argument by name ({@code request}/{@code response}) and the OAuth2 context
     * as the closure delegate.
     */
    private Closure<?> oauth2Driver(YamlOAuth2 oauth2) {
        GroovyScriptCompiler compiler = scriptCompiler;
        return new Closure<Object>(this) {
            public Object doCall(Object oauth2Context) {
                OAuth2Builder b = (OAuth2Builder) getDelegate();
                if (oauth2.buildTokenRequest != null) {
                    b.buildTokenRequest(compiler.compile(oauth2.buildTokenRequest, "request"));
                }
                if (oauth2.parseTokenResponse != null) {
                    b.parseTokenResponse(compiler.compile(oauth2.parseTokenResponse, "response"));
                }
                if (oauth2.validateToken != null) {
                    b.validateToken(compiler.compile(oauth2.validateToken, "token"));
                }
                if (oauth2.applyToken != null) {
                    b.applyToken(compiler.compile(oauth2.applyToken, "request"));
                }
                if (oauth2.onResponse != null) {
                    b.onResponse(compiler.compile(oauth2.onResponse, "response"));
                }
                if (oauth2.implementation != null) {
                    b.implementation(compiler.compile(oauth2.implementation));
                }
                return null;
            }
        };
    }
}
