package com.evolveum.polygon.scim.rest.groovy;


import com.evolveum.polygon.scim.rest.config.ScimClientConfiguration;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.testng.Assert.*;

public class ScimDevTest {

    private final static String TOKEN_ENDPOINT = "https://idp.scim.dev/oauth/token";
    private final static String CLIENT_ID = "9f12c467-68e5-42ab-b1e6-b7d90ae8600f";
    private final static String CLIENT_SECRET = "BGCsIeoNnEabme5oMZGrCvU5cD8LgUoR5S1ntJGA";
    private final static String SCIM_BASE_URL = "https://api.scim.dev/scim/v2";
    private final static String BEARER_TOKEN = "yDVnbHg34ZyrfpgjiUGsnTiWzvDUYX89WwOvECftDUgeDuOYZw1ijtbUYPU3";

    private static final ObjectClass USER_OC = new ObjectClass("User");
    private static final ObjectClass OFFICE_OC = new ObjectClass("Office");


    private class TestConfiguration extends BaseRestGroovyConnectorConfiguration implements ScimClientConfiguration.BearerToken {


        @Override
        public GuardedString getScimBearerToken() {
            return new GuardedString(BEARER_TOKEN.toCharArray());
        }

        @Override
        public String getScimBaseUrl() {
            return SCIM_BASE_URL;
        }
    }

    private class Connector extends AbstractGroovyRestConnector<TestConfiguration> {

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            // No custom scripts
            loader.loadFromResource("/scim/schema/dev/ScimDev.schema.groovy");
        }

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
            // no custom scripts

        }
    }

    @Test
    void getSchemas() {
        var connector = initializedConnector();

        var schema = connector.schema();
        assertNotNull(schema);

        var userOc = schema.findObjectClassInfo("User");
        assertNotNull(userOc);

        var groupOc = schema.findObjectClassInfo("Group");
        assertNotNull(groupOc);
    }

    @Test
    public void getUsers() {
        var connector = initializedConnector();
        var users = new ArrayList<ConnectorObject>();
        connector.executeQuery(USER_OC, null, users::add, null);
        assertFalse(users.isEmpty(), "Users should not be empty");

        var randomUser = users.get(new Random().nextInt(users.size()));
        assertNotNull(randomUser);
        var maybeUser = new ExpectSingle();
        connector.executeQuery(USER_OC, FilterBuilder.equalTo(randomUser.getUid()), maybeUser, null);
        assertNotNull(maybeUser.result);
        assertEquals(randomUser.getUid(), maybeUser.result.getUid());
    }

    @Test
    public void getOffices() {
        var connector = initializedConnector();
        var users = new ArrayList<ConnectorObject>();
        connector.executeQuery(OFFICE_OC, null, users::add, null);
        assertFalse(users.isEmpty(), "Users should not be empty");

        var randomUser = users.get(new Random().nextInt(users.size()));
        assertNotNull(randomUser);
        var maybeUser = new ExpectSingle();
        connector.executeQuery(OFFICE_OC, FilterBuilder.equalTo(randomUser.getUid()), maybeUser, null);
        assertNotNull(maybeUser.result);
        assertEquals(randomUser.getUid(), maybeUser.result.getUid());
    }


    private Connector initializedConnector() {
        Connector connector  = new Connector();
        connector.init(new TestConfiguration());
        return connector;
    }

    private class ExpectSingle implements ResultsHandler {

        ConnectorObject result;

        @Override
        public boolean handle(ConnectorObject connectorObject) {
            if (result != null) {
                throw new IllegalStateException("Additional result: " + result);
            }
            result = connectorObject;
            return false;
        }
    }
}
