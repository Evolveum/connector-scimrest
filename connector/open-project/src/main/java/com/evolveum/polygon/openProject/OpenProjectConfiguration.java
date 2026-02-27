/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.openProject;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.BaseRestGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class OpenProjectConfiguration extends BaseRestGroovyConnectorConfiguration implements RestClientConfiguration.BasicAuthorization {

    private String userName;
    private GuardedString password;

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.auth.userName",helpMessageKey = "rest.auth.userName.help",required = false)
    public String getRestUsername() {
        return userName;
    }

    @Override
    @ConfigurationProperty(displayMessageKey = "rest.auth.password",helpMessageKey = "rest.auth.password.help", required = true)
    public GuardedString getRestPassword() {
        return password;
    }

    public void setRestUsername(String userName) {
        this.userName = userName;
    }

    public void setRestPassword(GuardedString password) {
        this.password = password;
    }
}
