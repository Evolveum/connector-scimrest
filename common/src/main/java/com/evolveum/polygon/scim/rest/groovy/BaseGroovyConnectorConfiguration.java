package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.config.GroovyConfiguration;
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
