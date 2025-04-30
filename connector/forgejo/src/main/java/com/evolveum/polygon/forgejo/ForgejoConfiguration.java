package com.evolveum.polygon.forgejo;

import com.evolveum.polygon.scim.rest.config.HttpBasicAuthorizationConfiguration;
import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;
import com.evolveum.polygon.scim.rest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scim.rest.groovy.BaseRestGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;

public class ForgejoConfiguration extends BaseRestGroovyConnectorConfiguration implements HttpClientConfiguration.TokenAuthorization {


    private String tokenName;
    private GuardedString tokenValue;

    @Override
    public String getAuthorizationTokenName() {
        return tokenName;
    }

    @Override
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
