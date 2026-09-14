/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Configuration;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import javax.net.ssl.SSLContext;

/**
 * Registers {@link ScimApacheConnector} as the JAX-RS transport for the SCIM client, replacing the
 * JDK {@code HttpURLConnection} connector that cannot issue {@code PATCH} requests. The SSL context
 * is reused from the client so custom (e.g. trust-all) TLS configuration is preserved.
 */
public class ScimApacheConnectorProvider implements ConnectorProvider {

    @Override
    public Connector getConnector(Client client, Configuration configuration) {
        SSLContext sslContext;
        try {
            sslContext = client.getSslContext();
        } catch (Exception e) {
            sslContext = null;
        }
        return new ScimApacheConnector(sslContext);
    }
}
