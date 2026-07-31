/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.openProject.integration;

import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UpdateTests extends BaseTest{

    private static final Uid UID_GUYBRUSH = new Uid("5");
    private static final Map<String, Object> propertiesGuybrushUpdateStandard = Map.of(
            "admin", true,
            "email", "cp.guybrush.threepwood@evolveum.com",
            "firstName", "Captain Guybrush",
            "lastName", "Threepwood The Mighty",
            "language", "sk",
            Name.NAME, "mightypiratecaptain"
    );

    @Test(enabled = true)
    public void test10UpdateUser() {

        Set<AttributeDelta> attributeDeltas = getTestAttributeDeltas(propertiesGuybrushUpdateStandard);
        testUpdate("User", UID_GUYBRUSH, attributeDeltas);
    }

    @Test(enabled = true)
    public void test20UpdateUserLock() {

        Set<AttributeDelta> attributeDeltas = getTestAttributeDeltas(Map.of("status", "locked"));
        testUpdate("User", UID_GUYBRUSH, attributeDeltas);
    }

    @Test(enabled = true)
    public void test30UpdateUserUnLock() {

        Set<AttributeDelta> attributeDeltas = getTestAttributeDeltas(Map.of("status", "active"));
        testUpdate("User", UID_GUYBRUSH, attributeDeltas);
    }

    private Set<AttributeDelta> getTestAttributeDeltas(Map<String, Object> properties) {

        Set<AttributeDelta> attributeDeltas = new HashSet<>();

        for (String property: properties.keySet()){

            AttributeDeltaBuilder attributeDeltaBuilder = new AttributeDeltaBuilder();
            attributeDeltaBuilder.setName(property).addValueToReplace(properties.get(property));
            attributeDeltas.add(attributeDeltaBuilder.build());
        }

        return attributeDeltas;
    }
}
