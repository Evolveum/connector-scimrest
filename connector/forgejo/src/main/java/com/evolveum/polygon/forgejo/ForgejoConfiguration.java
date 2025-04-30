package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.scim.rest.config.HttpBasicAuthorizationConfiguration;
import com.evolveum.polygon.scim.rest.groovy.AbstractGroovyConnectorConfiguration;
import com.evolveum.polygon.scim.rest.groovy.AbstractGroovyRestConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.Configuration;

public class ForgejoConfiguration extends AbstractGroovyConnectorConfiguration implements HttpBasicAuthorizationConfiguration {


    private String username;
    private GuardedString password;

    @Override
    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public GuardedString getPassword() {
        return this.password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

}
