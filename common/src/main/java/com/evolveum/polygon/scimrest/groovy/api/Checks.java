/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
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
