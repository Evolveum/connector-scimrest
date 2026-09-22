/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.openProject;

import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;

@ConnectorClass(displayNameKey = "openProject.rest.display", configurationClass = OpenProjectConfiguration.class,  messageCatalogPaths = "Messages")
public class OpenProjectConnector extends AbstractGroovyRestConnector
        implements PoolableConnector {

    public OpenProjectConnector() {
        super(false);
    }

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
        loader.loadFromResource("/Formattable.native.schema.groovy");
        loader.loadFromResource("/Principal.native.schema.groovy");
        loader.loadFromResource("/Membership.native.schema.groovy");
        loader.loadFromResource("/Membership.connid.schema.groovy");
        loader.loadFromResource("/associations.schema.groovy");
    }

    @Override
    protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

    @Override
    protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
        builder.loadFromResource("/User.search.groovy");
        builder.loadFromResource("/Group.search.groovy");
        builder.loadFromResource("/Project.search.groovy");
        builder.loadFromResource("/Role.search.groovy");
        builder.loadFromResource("/Membership.search.groovy");
        builder.loadFromResource("/User.op.groovy");
        builder.loadFromResource("/User.create.op.groovy");
        builder.loadFromResource("/User.update.op.groovy");
//        builder.loadFromResource("/User.delete.op.groovy");
    }

    @Override
    public void checkAlive() throws ConnectionBrokenException {
    }
}
