package com.evolveum.polygon.forgejo;

import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

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
    }



}
