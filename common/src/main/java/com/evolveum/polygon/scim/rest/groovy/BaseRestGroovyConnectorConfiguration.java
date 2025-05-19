package com.evolveum.polygon.scim.rest.groovy;

import com.evolveum.polygon.scim.rest.config.HttpClientConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public abstract class BaseRestGroovyConnectorConfiguration extends BaseGroovyConnectorConfiguration implements HttpClientConfiguration {

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
