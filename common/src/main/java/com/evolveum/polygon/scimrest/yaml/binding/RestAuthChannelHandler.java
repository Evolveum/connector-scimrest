/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.binding.LocatedNode;
import com.evolveum.polygon.conndev.yaml.binding.YamlBinder;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.api.AuthenticationCustomizationBuilder;

import java.util.Map;

/**
 * Binds the {@code rest:} channel of an {@code authentication} block onto the live
 * {@link AuthenticationCustomizationBuilder.RestBuilder}.
 */
public final class RestAuthChannelHandler extends AuthChannelHandler {

    @Override
    @SuppressWarnings("unchecked")
    protected void bind(YamlBinder binder, AuthenticationCustomizationBuilder auth, LocatedNode channel) {
        var rest = auth.rest();
        for (var entry : channel.entries()) {
            var key = entry.key();
            var method = entry.value();
            switch (key) {
                case "apiKey" -> implementation(binder, method, rest, rest.getApiKey());
                case "basic" -> implementation(binder, method, rest, rest.getBasic());
                case "bearer" -> implementation(binder, method, rest, rest.getBearer());
                case "jwtBearer" -> implementation(binder, method, rest, rest.getJwtBearer());
                case "oauth2ClientCredentials" -> rest.oauth2ClientCredentials(oauth2Driver(binder, method));
                case "oauth2JwtBearer" -> rest.oauth2JwtBearer(oauth2Driver(binder, method));
                case "oauth2Password" -> rest.oauth2Password(oauth2Driver(binder, method));
                case "oauth2Saml" -> rest.oauth2Saml(oauth2Driver(binder, method));
                case "preference" -> rest.preference(preference(rest, method));
                default -> throw unknownMethod(method, key);
            }
        }
    }

    private void implementation(YamlBinder binder, LocatedNode method,
                                AuthenticationCustomizationBuilder.RestBuilder rest,
                                Class<? extends RestClientConfiguration> type) {
        var impl = block(method, "implementation");
        if (impl != null) {
            rest.implementation(type, binder.compileClosure(impl));
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends RestClientConfiguration>[] preference(
            AuthenticationCustomizationBuilder.RestBuilder rest, LocatedNode list) {
        var types = Map.<String, Class<? extends RestClientConfiguration>>of(
                "apiKey", rest.getApiKey(),
                "basic", rest.getBasic(),
                "bearer", rest.getBearer(),
                "jwtBearer", rest.getJwtBearer(),
                "oauth2ClientCredentials", rest.getOauth2ClientCredentials(),
                "oauth2JwtBearer", rest.getOauth2JwtBearer(),
                "oauth2Password", rest.getOauth2Password(),
                "oauth2Saml", rest.getOauth2Saml(),
                "awsSignature", rest.getAwsSignature());
        var classes = list.elements().stream().map(LocatedNode::text).map(name -> {
            var type = types.get(name);
            if (type == null) {
                throw new IllegalArgumentException("Unknown auth method in preference: " + name);
            }
            return type;
        }).toArray(Class[]::new);
        return (Class<? extends RestClientConfiguration>[]) classes;
    }
}
