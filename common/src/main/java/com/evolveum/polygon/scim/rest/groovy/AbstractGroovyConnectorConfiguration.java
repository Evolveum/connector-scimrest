package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.ObjectClassHandler;
import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.StatefulConfiguration;

import java.util.Map;

public abstract class AbstractGroovyConnectorConfiguration implements StatefulConfiguration {

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
