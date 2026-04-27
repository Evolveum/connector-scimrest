/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.api.AuthorizationCustomizer;
import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import org.identityconnectors.framework.spi.ConnectorClass;

@ConnectorClass(displayNameKey = "forgejo.rest.display", configurationClass = ForgejoConfiguration.class)
public class ForgejoConnector extends ManifestBasedConnector {

    @Override
    protected AuthorizationCustomizer<RestClientConfiguration> authorizationCustomizer() {
        return (c,request) -> {
            if (c instanceof RestClientConfiguration.TokenAuthorization tokenAuth) {
                var tokenAccessor = new GuardedStringAccessor();
                tokenAuth.getRestTokenValue().access(tokenAccessor);
                request.header("Authorization", "token " + tokenAccessor.getClearString());
            }
        };
    }
}
