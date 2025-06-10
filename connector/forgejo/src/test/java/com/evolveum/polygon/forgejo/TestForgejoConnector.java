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

//708f9b2d9ece1b996073e1edb83ce54b09081ee3

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

        configuration.setBaseAddress("https://git.dfx.sk/api/v1");
        configuration.setAuthorizationTokenName("forgejo-read-only");
        configuration.setAuthorizationTokenValue(new GuardedString("69dccdac818e14af47cb3984c41be1eebf847511".toCharArray()));

        connector.init(configuration);
        return connector;
    }


    @Test
    public void testSearchAllUsers() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }

    @Test
    public void testSearchAllUOrganizations() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("Organization"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }

    @Test
    public void testSearchAllTeams() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("Team"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }

    @Test
    public void testSearchUserByUid() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid("1"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

    @Test
    public void testSearchOrganizationByUidWithNameHint() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid("4", new Name("dfx.sk")));
        connector.executeQuery(new ObjectClass("Organization"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "4");
    }

    @Test
    public void testSearchUserByNameContains() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new ContainsFilter(new Name("tony"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 2);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

    @Test()
    public void testSearchUserByNameEquals() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Name("tonydamage"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

    @Test
    public void testSearchTeamByOrganizationName() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(AttributeBuilder.build("organization.name", List.of("dfx.sk")));
        connector.executeQuery(new ObjectClass("Team"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "3");
    }

    @Test
    public void testSearchTeamByUid() {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid("3"));
        connector.executeQuery(new ObjectClass("Team"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "3");
    }

    @Test
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
