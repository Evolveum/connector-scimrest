/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.scimgeneric;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * The SCIM2 Generic connector defaults the per-family {@code SCIM Mapping} flatten flags to
 * {@code true} (unlike every other connector, which defaults them to {@code false}), while still
 * honouring an explicit configuration value.
 */
public class ScimGenericConfigurationDefaultsTest {

    @Test
    public void flattenFlagsDefaultToTrue() {
        var configuration = new ScimGenericConfiguration();
        assertEquals(configuration.getScimFlattenNameAttribute(), Boolean.TRUE, "name flatten defaults to true");
        assertEquals(configuration.getScimFlattenEmails(), Boolean.TRUE, "email flatten defaults to true");
        assertEquals(configuration.getScimFlattenPhoneNumbers(), Boolean.TRUE, "phone flatten defaults to true");
        assertEquals(configuration.getScimFlattenAddresses(), Boolean.TRUE, "address flatten defaults to true");
    }

    @Test
    public void explicitValuesAreHonored() {
        var configuration = new ScimGenericConfiguration();
        configuration.setScimFlattenNameAttribute(Boolean.FALSE);
        configuration.setScimFlattenEmails(Boolean.FALSE);
        configuration.setScimFlattenPhoneNumbers(Boolean.FALSE);
        configuration.setScimFlattenAddresses(Boolean.FALSE);

        assertEquals(configuration.getScimFlattenNameAttribute(), Boolean.FALSE);
        assertEquals(configuration.getScimFlattenEmails(), Boolean.FALSE);
        assertEquals(configuration.getScimFlattenPhoneNumbers(), Boolean.FALSE);
        assertEquals(configuration.getScimFlattenAddresses(), Boolean.FALSE);
    }
}
