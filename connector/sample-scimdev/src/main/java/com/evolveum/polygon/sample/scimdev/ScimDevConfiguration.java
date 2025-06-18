package com.evolveum.polygon.sample.scimdev;

import com.evolveum.polygon.scim.rest.config.ScimClientConfiguration;
import com.evolveum.polygon.scim.rest.groovy.BaseGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class ScimDevConfiguration extends BaseGroovyConnectorConfiguration implements ScimClientConfiguration.BearerToken {

    private GuardedString tokenValue;

    public void setScimBearerToken(GuardedString tokenValue) {
        this.tokenValue = tokenValue;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.auth.tokenValue", required = true)
    public GuardedString getScimBearerToken() {
        return tokenValue;
    }

    @Override
    public String getScimBaseUrl() {
        return "https://api.scim.dev/scim/v2";
    }
}
