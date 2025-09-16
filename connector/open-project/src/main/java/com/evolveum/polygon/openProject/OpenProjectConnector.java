/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.openProject;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.impl.rest.RestContext;
import org.identityconnectors.framework.spi.ConnectorClass;

import java.util.Base64;

@ConnectorClass(displayNameKey = "openProject.rest.display", configurationClass = OpenProjectConfiguration.class)
public class OpenProjectConnector extends AbstractGroovyRestConnector<OpenProjectConfiguration> {
    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/User.native.schema.groovy");
        loader.loadFromResource("/User.connid.schema.groovy");
        loader.loadFromResource("/Group.native.schema.groovy");
        loader.loadFromResource("/Group.connid.schema.groovy");
        loader.loadFromResource("/Project.native.schema.groovy");
        loader.loadFromResource("/Project.connid.schema.groovy");
        loader.loadFromResource("/Role.native.schema.groovy");
        loader.loadFromResource("/Role.connid.schema.groovy");
//        loader.loadFromResource("/Principal.native.schema.groovy");
//        loader.loadFromResource("/association.schema.groovy");
    }

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        builder.loadFromResource("/User.search.groovy");
        builder.loadFromResource("/Group.search.groovy");
        builder.loadFromResource("/Project.search.groovy");
        builder.loadFromResource("/Role.search.groovy");
    }

    @Override
    protected RestContext.AuthorizationCustomizer authorizationCustomizer() {
        return (c,request) -> {
            if (c instanceof RestClientConfiguration.BasicAuthorization basicAuthorization) {
                var tokenAccessor = new GuardedStringAccessor();
                basicAuthorization.getRestPassword().access(tokenAccessor);
                String basicValueB64 = Base64.getEncoder().encodeToString(
                        (basicAuthorization.getRestUsername()+":"+tokenAccessor.getClearString()).getBytes());

                request.header("Authorization", "Basic " + basicValueB64);
            }
        };
    }
}
