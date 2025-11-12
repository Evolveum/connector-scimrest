/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.openProject.integration;

import com.evolveum.polygon.openProject.OpenProjectConfiguration;
import com.evolveum.polygon.openProject.OpenProjectConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;

import java.util.ArrayList;
import java.util.Map;

import static org.testng.Assert.*;

public class BaseTest {

    private OpenProjectConnector initializedConnector() {
        var connector = new OpenProjectConnector();
        var configuration = new OpenProjectConfiguration();

        configuration.setBaseAddress("");
        configuration.setRestUsername("");
        configuration.setRestPassword(new GuardedString("".toCharArray()));

        connector.init(configuration);
        return connector;
    }

    public void testSchema(String objectType) {
        var connector = new OpenProjectConnector();
        var configuration = new OpenProjectConfiguration();
        connector.init(configuration);
        var schema = connector.schema();

        assertNotNull(schema);
        var o = schema.findObjectClassInfo(objectType);
        assertNotNull(o);
        assertNotNull(o.getType());
    }


    public void testSearchAll(String objectType) {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass(objectType), null,
                results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }


    public ArrayList<ConnectorObject> testSearchByUid(String objectType, String id) {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid(id));
        connector.executeQuery(new ObjectClass(objectType), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), id);

        return results;
    }


    public void testSearchByValue(Filter filter, String objectType, String attrName, Object attrVal,
                                  Integer assertSize, String assertId) {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();

        connector.executeQuery(new ObjectClass(objectType), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);

        if (assertSize != null) {

            assertEquals(results.size(), assertSize);
        }

        if (assertId != null) {
            assertEquals(results.get(0).getUid().getUidValue(), assertId);
        }
    }

    public void testSearchContainsValue(String objectType, String attrName, Object attrVal,
                                        String assertId, Integer assertSize) {
        var filter = (ContainsFilter) FilterBuilder.contains(AttributeBuilder.build(attrName,
                attrVal));
        testSearchByValue(filter, objectType, attrName, attrVal, assertSize, assertId);
    }

    public void testSearchContainsValue(String objectType, String attrName, Object attrVal,
                                        Integer assertSize) {
        var filter = (ContainsFilter) FilterBuilder.contains(AttributeBuilder.build(attrName,
                attrVal));
        testSearchByValue(filter, objectType, attrName, attrVal, assertSize, null);
    }

    public void testSearchContainsValue(String objectType, String attrName, Object attrVal,
                                        String assertId) {
        var filter = (ContainsFilter) FilterBuilder.contains(AttributeBuilder.build(attrName,
                attrVal));
        testSearchByValue(filter, objectType, attrName, attrVal, 1, assertId);
    }

    public void testSearchContainsValue(String objectType, String attrName, Object attrVal) {
        var filter = (ContainsFilter) FilterBuilder.contains(AttributeBuilder.build(attrName,
                attrVal));
        testSearchByValue(filter, objectType, attrName, attrVal, null, null);
    }

    public void testSearchEqualsValue(String objectType, String attrName, Object attrVal,
                                      String assertId, Integer assertSize) {
        var filter = (EqualsFilter) FilterBuilder.equalTo(AttributeBuilder.build(attrName,
                attrVal));
        testSearchByValue(filter, objectType, attrName, attrVal,  assertSize, assertId);
    }

    public void testSearchEqualsValue(String objectType, String attrName, Object attrVal,
                                      String assertId) {
        var filter = (EqualsFilter) FilterBuilder.equalTo(AttributeBuilder.build(attrName,
                attrVal));
        testSearchByValue(filter, objectType, attrName, attrVal, 1, assertId);
    }

    public void testSearchEqualsValue(String objectType, String attrName, Object attrVal) {
        var filter = (EqualsFilter) FilterBuilder.equalTo(AttributeBuilder.build(attrName,
                attrVal));
        testSearchByValue(filter, objectType, attrName, attrVal, null, null);
    }

    public void testSearchEqualsValue(String objectType, String attrName, Object attrVal, Integer asserSize) {
        var filter = (EqualsFilter) FilterBuilder.equalTo(AttributeBuilder.build(attrName,
                attrVal));
        testSearchByValue(filter, objectType, attrName, attrVal, asserSize, null);
    }

    public boolean compareReferencedName(Object o, String name) {
        return o instanceof ConnectorObjectReference cor && cor.getValue() instanceof
                ConnectorObject co && co.getName().getNameValue().equals(name);
    }
}
