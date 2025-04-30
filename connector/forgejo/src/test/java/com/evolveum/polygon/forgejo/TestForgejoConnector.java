package com.evolveum.polygon.forgejo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.testng.Assert.assertNotNull;

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

    @Test
    public void testSearchUsers() {
        var connector = new ForgejoConnector();
        var configuration = new ForgejoConfiguration();

        configuration.setBaseAddress("https://git.dfx.sk/api/v1");
        configuration.setAuthorizationTokenName("forgejo-read-only");
        configuration.setAuthorizationTokenValue(new GuardedString("708f9b2d9ece1b996073e1edb83ce54b09081ee3".toCharArray()));

        connector.init(configuration);
        connector.test();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass("User"), null, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }

}
