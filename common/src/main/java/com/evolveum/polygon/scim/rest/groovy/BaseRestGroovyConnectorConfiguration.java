package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;

public abstract class BaseRestGroovyConnectorConfiguration extends BaseGroovyConnectorConfiguration implements HttpClientConfiguration {

    private String baseAddress;
    private boolean trustAllCertificates;

    @Override
    public String getBaseAddress() {
        return baseAddress;
    }

    public void setBaseAddress(String baseAddress) {
        this.baseAddress = baseAddress;
    }

    @Override
    public boolean getTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }
}
