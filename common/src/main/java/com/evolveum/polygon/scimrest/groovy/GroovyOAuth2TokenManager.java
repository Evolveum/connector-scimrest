/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.impl.rest.OAuth2TokenManager;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

import java.util.Map;

/**
 * Extends {@link OAuth2TokenManager} with Groovy script hook support.
 *
 * <p>Each of the four lifecycle stages can be customized via a Groovy closure
 * captured from the {@code oauth2 { }} initialization block.
 */
public class GroovyOAuth2TokenManager extends OAuth2TokenManager {

    private Closure<?> buildTokenRequestHook;
    private Closure<?> parseTokenResponseHook;
    private Closure<?> validateTokenHook;
    private Closure<?> applyTokenHook;

    public void setBuildTokenRequestHook(Closure<?> hook)  { this.buildTokenRequestHook = hook; }
    public void setParseTokenResponseHook(Closure<?> hook) { this.parseTokenResponseHook = hook; }
    public void setValidateTokenHook(Closure<?> hook)      { this.validateTokenHook = hook; }
    public void setApplyTokenHook(Closure<?> hook)         { this.applyTokenHook = hook; }

    @Override
    protected boolean validateToken() {
        if (validateTokenHook != null) {
            return Boolean.TRUE.equals(callHook(validateTokenHook));
        }
        return super.validateToken();
    }

    @Override
    protected void customizeBuildTokenRequest(HttpRequestSpecification request) {
        if (buildTokenRequestHook != null) {
            callHook(buildTokenRequestHook, request);
        } else {
            super.customizeBuildTokenRequest(request);
        }
    }

    @Override
    protected void processTokenResponse(Map<String, Object> response) {
        if (parseTokenResponseHook != null) {
            callHook(parseTokenResponseHook, response);
            if (getOauth2Context().accessToken() == null) {
                throw new ConnectorIOException(
                        "OAuth2 parseTokenResponse hook did not set 'access_token' on the context");
            }
        } else {
            super.processTokenResponse(response);
        }
    }

    @Override
    protected void applyTokenToRequest(HttpRequestSpecification request) {
        if (applyTokenHook != null) {
            callHook(applyTokenHook, request);
        } else {
            super.applyTokenToRequest(request);
        }
    }

    private Object callHook(Closure<?> hook, Object... args) {
        Closure<?> copy = (Closure<?>) hook.clone();
        copy.setDelegate(getOauth2Context());
        copy.setResolveStrategy(Closure.DELEGATE_FIRST);
        return args.length == 0 ? copy.call() : args.length == 1 ? copy.call(args[0]) : copy.call(args);
    }
}
