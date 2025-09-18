package com.evolveum.polygon.scimrest.groovy.impl;

import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;

@ConnectorClass(displayNameKey = "manifest.connector.display", configurationClass = ReadOnlyConfiguration.class, messageCatalogPaths = "Messages")
public class ManifestBasedConnector extends AbstractGroovyRestConnector<ReadOnlyConfiguration> {

    private static final String CONNECTOR_MANIFEST = "/connector.manifest.json";
    private final ConnectorManifest manifest;

    public ManifestBasedConnector() {
        // Load connector manifest
        var resource = getClass().getResourceAsStream(CONNECTOR_MANIFEST);
        this.manifest = new ConnectorManifest(resource);
    }

    @Override
    public void init(Configuration cfg) {
        super.init(cfg);
    }

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        manifest.schemaScripts().forEach(loader::loadFromResource);
    }

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
        manifest.operationScripts().forEach(builder::loadFromResource);
    }
}
