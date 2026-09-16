/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy.auth;

import com.evolveum.polygon.scimrest.api.HttpRequestSpecification;
import com.evolveum.polygon.scimrest.impl.rest.HttpExceptionMapper;
import com.evolveum.polygon.scimrest.impl.rest.OAuth2TokenManager;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Extends {@link OAuth2TokenManager} with Groovy script hook support.
 *
 * <p>Each of the lifecycle stages can be customized via a Groovy closure
 * captured from the {@code oauth2 { }} initialization block.
 */
public class GroovyOAuth2TokenManager extends OAuth2TokenManager {

    private Closure<?> buildTokenRequestHook;
    private Closure<?> parseTokenResponseHook;
    private Closure<?> validateTokenHook;
    private Closure<?> applyTokenHook;
    private Closure<?> onResponseHook;

    public void setBuildTokenRequestHook(Closure<?> hook)  { this.buildTokenRequestHook = hook; }
    public void setParseTokenResponseHook(Closure<?> hook) { this.parseTokenResponseHook = hook; }
    public void setValidateTokenHook(Closure<?> hook)      { this.validateTokenHook = hook; }
    public void setApplyTokenHook(Closure<?> hook)         { this.applyTokenHook = hook; }
    public void setOnResponseHook(Closure<?> hook)         { this.onResponseHook = hook; }

    @Override
    protected boolean validateToken() {
        if (validateTokenHook != null) {
            return Boolean.TRUE.equals(callHook("validateToken", validateTokenHook));
        }
        return super.validateToken();
    }

    @Override
    protected void customizeBuildTokenRequest(HttpRequestSpecification request) {
        if (buildTokenRequestHook != null) {
            callHook("buildTokenRequest", buildTokenRequestHook, request);
        } else {
            super.customizeBuildTokenRequest(request);
        }
    }

    @Override
    protected void processTokenResponse(Map<String, Object> response) {
        if (parseTokenResponseHook != null) {
            callHook("parseTokenResponse", parseTokenResponseHook, response);
            if (getAuthContext().get(ACCESS_TOKEN) == null) {
                // The hook ran but left the context without a token — a script bug, i.e. a
                // connector-configuration problem, not a transient I/O failure.
                throw new ConfigurationException(
                        "OAuth2 'parseTokenResponse' hook did not set 'access_token' on the context");
            }
        } else {
            super.processTokenResponse(response);
        }
    }

    @Override
    protected void applyTokenToRequest(HttpRequestSpecification request) {
        if (applyTokenHook != null) {
            callHook("applyToken", applyTokenHook, request);
        } else {
            super.applyTokenToRequest(request);
        }
    }

    @Override
    public void handleResponse(HttpResponse<?> response) {
        if (onResponseHook != null) {
            callHook("onResponse", onResponseHook, response);
        } else {
            super.handleResponse(response);
        }
    }

    private Object callHook(String hookName, Closure<?> hook, Object... args) {
        try {
            Closure<?> copy = (Closure<?>) hook.clone();
            copy.setDelegate(getAuthContext());
            copy.setResolveStrategy(Closure.DELEGATE_FIRST);
            return args.length == 0 ? copy.call() : args.length == 1 ? copy.call(args[0]) : copy.call(args);
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            // A typo or logic error in one of the connector's oauth2 hooks is a configuration
            // problem; the hook name is included so the author knows which block to fix.
            throw new ConfigurationException(
                    "OAuth2 '" + hookName + "' hook failed: " + HttpExceptionMapper.causeMessage(e), e);
        }
    }
}
