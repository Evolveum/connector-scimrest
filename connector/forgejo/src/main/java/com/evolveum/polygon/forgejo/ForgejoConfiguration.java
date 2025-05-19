package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;
import com.evolveum.polygon.scim.rest.groovy.BaseRestGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class ForgejoConfiguration extends BaseRestGroovyConnectorConfiguration implements HttpClientConfiguration.TokenAuthorization {


    private String tokenName;
    private GuardedString tokenValue;

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.auth.tokenName", required = false)
    public String getAuthorizationTokenName() {
        return tokenName;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.auth.tokenValue", required = true)
    public GuardedString getAuthorizationTokenValue() {
        return tokenValue;
    }

    public void setAuthorizationTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public void setAuthorizationTokenValue(GuardedString tokenValue) {
        this.tokenValue = tokenValue;
    }
}
