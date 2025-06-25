/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.config.GroovyConfiguration;
import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.identityconnectors.framework.spi.StatefulConfiguration;

public abstract class BaseGroovyConnectorConfiguration implements StatefulConfiguration, GroovyConfiguration {

    private ConnectorMessages messages;

    private GroovyContext groovyContext;

    @Override
    public ConnectorMessages getConnectorMessages() {
        return messages;
    }

    @Override
    public void setConnectorMessages(ConnectorMessages messages) {
        this.messages = messages;
    }

    @Override
    public void release() {

    }

    public GroovyContext groovyContext() {
        if (groovyContext == null) {
            synchronized (this) {
                if (groovyContext == null) {
                    groovyContext = new GroovyContext();
                }
            }
        }
        return groovyContext;
    }

    @Override
    public void validate() {

    }
}
