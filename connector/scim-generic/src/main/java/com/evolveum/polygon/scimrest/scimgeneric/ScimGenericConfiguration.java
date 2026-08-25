/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.scimgeneric;

import com.evolveum.polygon.scimrest.groovy.impl.ReadOnlyConfiguration;
import org.identityconnectors.framework.spi.ConfigurationClass;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * Configuration for the SCIM2 Generic connector.
 *
 * <p>Extends the shared read-only manifest configuration and defaults the per-family
 * {@code SCIM Mapping} flatten flags to {@code true} (still overridable by an explicit value);
 * the shared {@link ReadOnlyConfiguration} leaves them unset, i.e. {@code false}.
 */
@ConfigurationClass(overrideFile = "configurationOverride.properties")
public class ScimGenericConfiguration extends ReadOnlyConfiguration {

    private Boolean flattenNameAttribute;
    private Boolean flattenEmails;
    private Boolean flattenPhoneNumbers;
    private Boolean flattenAddresses;

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.mapping", order = 810, required = false)
    public Boolean getScimFlattenNameAttribute() {
        return flattenNameAttribute != null ? flattenNameAttribute : Boolean.TRUE;
    }

    public void setScimFlattenNameAttribute(Boolean flattenNameAttribute) {
        this.flattenNameAttribute = flattenNameAttribute;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.mapping", order = 820, required = false)
    public Boolean getScimFlattenEmails() {
        return flattenEmails != null ? flattenEmails : Boolean.TRUE;
    }

    public void setScimFlattenEmails(Boolean flattenEmails) {
        this.flattenEmails = flattenEmails;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.mapping", order = 830, required = false)
    public Boolean getScimFlattenPhoneNumbers() {
        return flattenPhoneNumbers != null ? flattenPhoneNumbers : Boolean.TRUE;
    }

    public void setScimFlattenPhoneNumbers(Boolean flattenPhoneNumbers) {
        this.flattenPhoneNumbers = flattenPhoneNumbers;
    }

    @Override
    @ConfigurationProperty(groupMessageKey = "scim.mapping", order = 840, required = false)
    public Boolean getScimFlattenAddresses() {
        return flattenAddresses != null ? flattenAddresses : Boolean.TRUE;
    }

    public void setScimFlattenAddresses(Boolean flattenAddresses) {
        this.flattenAddresses = flattenAddresses;
    }
}
