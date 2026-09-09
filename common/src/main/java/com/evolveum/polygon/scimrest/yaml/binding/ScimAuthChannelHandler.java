/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.binding.LocatedNode;
import com.evolveum.polygon.conndev.yaml.binding.YamlBinder;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;

import java.util.Map;

/**
 * Binds the {@code scim:} channel of an {@code authentication} block onto the live
 * {@link AuthenticationCustomizationBuilder.ScimBuilder}.
 */
public final class ScimAuthChannelHandler extends AuthChannelHandler {

    @Override
    @SuppressWarnings("unchecked")
    protected void bind(YamlBinder binder, AuthenticationCustomizationBuilder auth, LocatedNode channel) {
        var scim = auth.scim();
        for (var entry : channel.entries()) {
            var key = entry.key();
            var method = entry.value();
            switch (key) {
                case "apiKey" -> implementation(binder, method, scim, scim.getApiKey());
                case "basic" -> implementation(binder, method, scim, scim.getBasic());
                case "bearer" -> implementation(binder, method, scim, scim.getBearer());
                case "jwtBearer" -> implementation(binder, method, scim, scim.getJwtBearer());
                case "oauth2ClientCredentials" -> scim.oauth2ClientCredentials(oauth2Driver(binder, method));
                case "oauth2JwtBearer" -> scim.oauth2JwtBearer(oauth2Driver(binder, method));
                case "oauth2Password" -> scim.oauth2Password(oauth2Driver(binder, method));
                case "oauth2Saml" -> scim.oauth2Saml(oauth2Driver(binder, method));
                case "preference" -> scim.preference(preference(scim, method));
                default -> throw unknownMethod(method, key);
            }
        }
    }

    private void implementation(YamlBinder binder, LocatedNode method,
                                AuthenticationCustomizationBuilder.ScimBuilder scim,
                                Class<? extends ScimClientConfiguration> type) {
        var impl = block(method, "implementation");
        if (impl != null) {
            scim.implementation(type, binder.compileClosure(impl));
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends ScimClientConfiguration>[] preference(
            AuthenticationCustomizationBuilder.ScimBuilder scim, LocatedNode list) {
        var types = Map.<String, Class<? extends ScimClientConfiguration>>of(
                "apiKey", scim.getApiKey(),
                "basic", scim.getBasic(),
                "bearer", scim.getBearer(),
                "jwtBearer", scim.getJwtBearer(),
                "oauth2ClientCredentials", scim.getOauth2ClientCredentials(),
                "oauth2JwtBearer", scim.getOauth2JwtBearer(),
                "oauth2Password", scim.getOauth2Password(),
                "oauth2Saml", scim.getOauth2Saml(),
                "awsSignature", scim.getAwsSignature());
        var classes = list.elements().stream().map(LocatedNode::text).map(name -> {
            var type = types.get(name);
            if (type == null) {
                throw new IllegalArgumentException("Unknown auth method in preference: " + name);
            }
            return type;
        }).toArray(Class[]::new);
        return (Class<? extends ScimClientConfiguration>[]) classes;
    }
}
