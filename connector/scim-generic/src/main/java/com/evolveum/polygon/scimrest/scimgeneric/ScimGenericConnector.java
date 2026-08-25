/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.scimgeneric;

import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.framework.spi.ConnectorClass;

/**
 * SCIM2 Generic connector. Behaves exactly like the shared {@link ManifestBasedConnector} but is
 * backed by {@link ScimGenericConfiguration}, which defaults the per-family {@code SCIM Mapping}
 * flatten flags to {@code true}.
 */
@ConnectorClass(displayNameKey = "manifest.connector.display",
        configurationClass = ScimGenericConfiguration.class,
        messageCatalogPaths = "Messages")
public class ScimGenericConnector extends ManifestBasedConnector {
}
