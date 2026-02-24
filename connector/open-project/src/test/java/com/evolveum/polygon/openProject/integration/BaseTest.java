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
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.test.common.TestHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.*;

public class BaseTest {

    protected ConnectorFacade initializedConnector() {
        OpenProjectConfiguration config = new OpenProjectConfiguration();

        config.setBaseAddress("http://127.0.0.1:8080/api/v3");
        config.setRestUsername("");
        config.setRestPassword(new GuardedString("".toCharArray()));

        return initializedConnector(config);
    }

    private ConnectorFacade initializedConnector(OpenProjectConfiguration config) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();


        APIConfiguration apiConfiguration = TestHelpers.createTestConfiguration(OpenProjectConnector.class, config);
        apiConfiguration.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);
        apiConfiguration.getResultsHandlerConfiguration().setEnableCaseInsensitiveFilter(false);
        apiConfiguration.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        apiConfiguration.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);
        apiConfiguration.getResultsHandlerConfiguration().setFilteredResultsHandlerInValidationMode(false);

        return factory.newInstance(apiConfiguration);
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
        connector.search(new ObjectClass(objectType), null,
                results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }


    public ArrayList<ConnectorObject> testSearchByUid(String objectType, String id) {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid(id));
        connector.search(new ObjectClass(objectType), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), id);

        return results;
    }


    public void testSearchByValue(Filter filter, String objectType, String attrName, Object attrVal,
                                  Integer assertSize, String assertId) {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();

        connector.search(new ObjectClass(objectType), filter, results::add, new OperationOptions(Map.of()));
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

    public void testCreate(String objectType, Set<Attribute> attributeSet) {

        var connector = initializedConnector();
        Uid uid = connector.create(new ObjectClass(objectType), attributeSet,
                new OperationOptions(Map.of()) );
        assertNotNull(uid);
    }

    public void testUpdate(String objectType, Uid uid , Set<AttributeDelta> attributeDeltaSet) {

        var connector = initializedConnector();
        connector.updateDelta(new ObjectClass(objectType), uid, attributeDeltaSet,
                new OperationOptions(Map.of()));
        //TODO assertion
    }
}
