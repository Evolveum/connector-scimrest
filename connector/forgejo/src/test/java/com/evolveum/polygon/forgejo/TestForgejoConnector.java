/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.forgejo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class TestForgejoConnector {

    @Test
    public void testSchema() {
        var connector = new ForgejoConnector();
        var configuration = new ForgejoConfiguration();
        connector.init(configuration);

        var schema = connector.schema();

        assertNotNull(schema);
        var user = schema.findObjectClassInfo("User");
        assertNotNull(user);
        assertNotNull(user.getType());

        assertTrue(user.getAttributeInfo().stream().anyMatch(
                attr -> attr.getName().equals("organization") && attr.getType().equals(ConnectorObjectReference.class)));
    }

    private ForgejoConnector initializedConnector() {
        var connector = new ForgejoConnector();
        var configuration = new ForgejoConfiguration();

        configuration.setBaseAddress("...");
        configuration.setAuthorizationTokenName("...");
        configuration.setAuthorizationTokenValue(new GuardedString("...".toCharArray()));

        connector.init(configuration);
        return connector;
    }


    @Test(enabled = false)
    public void testSearchAllUsers() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }

    @Test(enabled = false)
    public void testSearchAllUOrganizations() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("Organization"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }

    @Test(enabled = false)
    public void testSearchAllTeams() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("Team"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }

    @Test(enabled = false)
    public void testSearchUserByUid() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid("1"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

    @Test(enabled = false)
    public void testSearchOrganizationByUidWithNameHint() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid("dfx.sk", new Name("dfx.sk")));
        connector.executeQuery(new ObjectClass("Organization"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        var firstResult = results.get(0);
        assertEquals(firstResult.getUid().getUidValue(), "dfx.sk");
        var members = firstResult.getAttributeByName("member");
        assertNotNull(members);
        assertFalse(members.getValue().isEmpty());

    }

    @Test(enabled = false)
    public void testSearchUserByNameContains() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new ContainsFilter(new Name("tony"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 2);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

    @Test(enabled = false)
    public void testSearchUserByNameEquals() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Name("tonydamage"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

    @Test(enabled = false)
    public void testSearchTeamByOrganizationName() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(AttributeBuilder.build("organization.name", List.of("dfx.sk")));
        connector.executeQuery(new ObjectClass("Team"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "3");
    }

    @Test(enabled = false)
    public void testSearchTeamByUid() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid("3"));
        connector.executeQuery(new ObjectClass("Team"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "3");
    }

    @Test(enabled = false)
    public void testOrganizationMembershipReturned() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Name("tonydamage"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");

        var organizations = results.get(0).getAttributeByName("organization");
        assertNotNull(organizations);
        assertFalse(organizations.getValue().isEmpty());
        assertTrue(organizations.getValue().stream().anyMatch(o -> o instanceof ConnectorObjectReference cor && cor.getValue() instanceof ConnectorObject co && co.getName().getNameValue().equals("vaia-test")));

    }

}
