/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.framework.spi.ConnectorClass;

@ConnectorClass(displayNameKey = "forgejo.rest.display", configurationClass = ForgejoConfiguration.class, messageCatalogPaths = "Messages")
public class ForgejoConnector extends ManifestBasedConnector {
}
