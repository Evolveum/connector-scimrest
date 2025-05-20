package com.evolveum.polygon.forgejo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.testng.annotations.Test;

import java.util.ArrayList;
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
    }

    private ForgejoConnector initializedConnector() {
        var connector = new ForgejoConnector();
        var configuration = new ForgejoConfiguration();

        configuration.setBaseAddress("https://git.dfx.sk/api/v1");
        configuration.setAuthorizationTokenName("forgejo-read-only");
        configuration.setAuthorizationTokenValue(new GuardedString("708f9b2d9ece1b996073e1edb83ce54b09081ee3".toCharArray()));

        connector.init(configuration);
        return connector;
    }


    @Test
    public void testSearchAllUsers() {
        var connector = initializedConnector();
        var configuration = new ForgejoConfiguration();

        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }

    @Test
    public void testSearchAllUOrganizations() {
        var connector = initializedConnector();
        var configuration = new ForgejoConfiguration();

        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("Organization"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }



    @Test
    public void testSearchUserByUid() {
        var connector = initializedConnector();
        var configuration = new ForgejoConfiguration();

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
        var configuration = new ForgejoConfiguration();

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
        var configuration = new ForgejoConfiguration();

        var results = new ArrayList<ConnectorObject>();
        var filter = new ContainsFilter(new Name("tony"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

    @Test(enabled = false)
    public void testSearchUserByNameEquals() {
        var connector = initializedConnector();
        var configuration = new ForgejoConfiguration();

        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Name("tonydamage"));
        connector.executeQuery(new ObjectClass("User"), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), "1");
    }

}
