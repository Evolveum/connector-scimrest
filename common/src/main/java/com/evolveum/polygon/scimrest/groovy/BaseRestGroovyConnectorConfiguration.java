/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public abstract class BaseRestGroovyConnectorConfiguration extends BaseGroovyConnectorConfiguration implements RestClientConfiguration {

    private String baseAddress;
    private Boolean trustAllCertificates;


    @Override
    @ConfigurationProperty(groupMessageKey = "rest.baseAddress", required = true)
    public String getBaseAddress() {
        return baseAddress;
    }

    public void setBaseAddress(String baseAddress) {
        this.baseAddress = baseAddress;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "rest.trustAllCertificates", required = false)
    public Boolean getTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(Boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }
}
