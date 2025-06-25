/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.framework.spi.ConnectorClass;

@ConnectorClass(displayNameKey = "forgejo.rest.display", configurationClass = ForgejoConfiguration.class)
public class ForgejoConnector extends AbstractGroovyRestConnector<ForgejoConfiguration> {

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/User.native.schema.groovy");
        loader.loadFromResource("/User.connid.schema.groovy");
        loader.loadFromResource("/Organization.native.schema.groovy");
        loader.loadFromResource("/Organization.connid.schema.groovy");
        loader.loadFromResource("/Team.native.schema.groovy");
        loader.loadFromResource("/Team.connid.schema.groovy");
        loader.loadFromResource("/associations.new.schema.groovy");


    }

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        builder.loadFromResource("/User.search.groovy");
        builder.loadFromResource("/Organization.search.groovy");
        builder.loadFromResource("/Team.search.groovy");
        builder.loadFromResource("/Team.custom.minimal.search.groovy");
        // Contains both organization and user search necessary for providing user-sided part of associations.
        builder.loadFromResource("/User.organization.support.search.groovy");
    }

    @Override
    protected RestContext.AuthorizationCustomizer authorizationCustomizer() {
        return (c,request) -> {
            if (c instanceof RestClientConfiguration.TokenAuthorization tokenAuth) {
                var tokenAccessor = new GuardedStringAccessor();
                tokenAuth.getRestTokenValue().access(tokenAccessor);
                request.header("Authorization", "token " + tokenAccessor.getClearString());
            }
        };
    }
}
