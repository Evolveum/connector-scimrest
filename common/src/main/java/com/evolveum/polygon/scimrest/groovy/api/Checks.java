package com.evolveum.polygon.scimrest.groovy.api;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;

import java.net.URI;
import java.net.URISyntaxException;

public class Checks {

    public static void checkConfigurationBaseUri(String baseUri) throws ConfigurationException {
        try {
            // Lets verify baseUri once more
            new URI(baseUri);
        } catch (URISyntaxException ex) {
            throw new ConfigurationException("Base URI  is not valid valid URI", ex);
        }
    }
}
