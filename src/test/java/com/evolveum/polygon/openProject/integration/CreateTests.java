package com.evolveum.polygon.openProject.integration;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CreateTests extends BaseTest {

    private static final Map<String, Object> propertiesGuybrushCreate = Map.of(
            "admin", false,
            "email", "guybrush.threepwood@evolveum.com",
            "firstName", "Guybrush",
            "language", "en",
            "lastName", "Threepwood",
            Name.NAME, "mightypirate",
            "status", "active",
            "password", "SwordfightInsultChampion123!"
    );

    @Test(enabled = true)
    public void test10CreateUser() {

        Set<Attribute> attributes = getTestAttributes(propertiesGuybrushCreate);
        testCreate("User", attributes);
    }

    private Set<Attribute> getTestAttributes(Map<String, Object> properties) {

        Set<Attribute> attributes = new HashSet<>();

        for (String attrName : properties.keySet()) {

            AttributeBuilder attributeBuilder = new AttributeBuilder();
            attributeBuilder.setName(attrName);
            attributeBuilder.addValue(properties.get(attrName));

            attributes.add(attributeBuilder.build());
        }

        return attributes;
    }
}
