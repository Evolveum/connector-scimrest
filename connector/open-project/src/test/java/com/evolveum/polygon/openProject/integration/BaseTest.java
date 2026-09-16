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
import java.util.concurrent.ThreadLocalRandom;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class BaseTest {

    private static final Integer defaultPageSizeValue = 20;
    private static final Integer defaultPageOffset = 1;
    static Map.Entry<String, Integer> _OP_ENTRY_DEFAULT_PAGE_SIZE = Map.entry(OperationOptions.OP_PAGE_SIZE, defaultPageSizeValue);
    static Map.Entry<String, Integer> _OP_ENTRY_DEFAULT_PAGED_RESULT_OFFSET = Map.entry(OperationOptions.OP_PAGED_RESULTS_OFFSET, defaultPageOffset);


    protected ConnectorFacade initializedConnector() {
        OpenProjectConfiguration config = new OpenProjectConfiguration();

        config.setBaseAddress("https://localhost:8443/api/v3");
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
        testSearchAll(objectType, new OperationOptions(Map.of()));
    }

    public void testSearchAll(String objectType, OperationOptions options) {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.search(new ObjectClass(objectType), null,
                results::add, options);
        assertNotNull(results);
    }


    public ArrayList<ConnectorObject> testSearchByUid(String objectType, String id) {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid(id));
        connector.search(new ObjectClass(objectType), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().getUid().getUidValue(), id);

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
            assertEquals(results.getFirst().getUid().getUidValue(), assertId);
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
    public void testDelete(String objectType, Uid uid) {

        var connector = initializedConnector();
        connector.delete(new ObjectClass(objectType), uid,
                new OperationOptions(Map.of()));
        //TODO assertion
    }

    public static String generateRandomFiveDigitCode() {
        return String.format("%05d",
                ThreadLocalRandom.current().nextInt(0, 100_000));
    }
    @SafeVarargs
    public static OperationOptions buildOptions(Map.Entry<String, ?>... options){
        return new OperationOptions(Map.ofEntries(options));
    }

    public static Map.Entry<String, ?> [] buildPageEntries(Integer pageSize, Integer pageOffset){

        return new Map.Entry[] {
                pageSize != null
                        ? Map.entry(OperationOptions.OP_PAGE_SIZE, pageSize)
                        : _OP_ENTRY_DEFAULT_PAGE_SIZE,
                pageOffset != null
                        ? Map.entry(OperationOptions.OP_PAGED_RESULTS_OFFSET, pageOffset)
                        : _OP_ENTRY_DEFAULT_PAGED_RESULT_OFFSET
        };
    }

    public static Map.Entry<String, ?> []  buildPageEntries(){

        return buildPageEntries(null,null);
    }

}
