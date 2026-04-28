package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNotNull;

/**
 * Verifies that initializeObjectClassHandler runs after the schema is built,
 * so handler scripts can reference schema attributes (e.g. via attribute("id"))
 * without a NullPointerException.
 */
public class InitializationOrderTest {

    private static final String SCHEMA_SCRIPT =
            "objectClass('User') { attribute('id').connId().type(String.class) }";

    private static final String HANDLER_SCRIPT =
            "objectClass('User') { " +
            "  search { " +
            "    endpoint('/test') { " +
            "      supportedFilter(attribute('id').eq()) {} " +
            "    } " +
            "  } " +
            "}";

    public static class TestConfiguration extends BaseGroovyConnectorConfiguration implements RestClientConfiguration {
        @Override public String getBaseAddress()      { return "http://localhost"; }
        @Override public String getRestTestEndpoint() { return null; }
        @Override public Boolean getTrustAllCertificates() { return true; }
    }

    public static class TestConnector extends AbstractGroovyRestConnector<TestConfiguration> {
        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(SCHEMA_SCRIPT);
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

        @Override
        protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {
            builder.loadFromString(HANDLER_SCRIPT);
        }
    }

    @Test
    public void handlerScriptCanReferenceSchemaAttributesDuringInit() {
        var connector = new TestConnector();
        connector.init(new TestConfiguration());

        var schema = connector.schema();

        assertNotNull(schema.findObjectClassInfo("User"));
        assertNotNull(connector.handlerFor(new ObjectClass("User")));
    }
}
