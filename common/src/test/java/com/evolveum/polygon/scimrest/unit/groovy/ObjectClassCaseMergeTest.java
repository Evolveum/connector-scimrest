/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.GroovyRestHandlerBuilder;
import com.evolveum.polygon.scimrest.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.spi.CreateOperation;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.testng.annotations.Test;

/**
 * ConnId {@link ObjectClass} identity is case-insensitive (see its {@code is()}/{@code equals()}
 * javadoc), but {@code RestHandlerBuilder.handlers} keys handler builders by raw, case-sensitive
 * Java {@code String}. Two operation scripts that target the same object class with different
 * casing ("Account" vs "account") must therefore still merge into a single handler - not silently
 * clobber each other when the final {@code Map<ObjectClass, ObjectClassHandler>} is assembled.
 */
public class ObjectClassCaseMergeTest {

    private static final String SCHEMA_SCRIPT =
            "objectClass('Account') { attribute('id').connId().type(String.class) }";

    // Same logical object class, declared with different casing, each contributing a
    // different operation. Both must end up on the one handler for ObjectClass("Account").
    private static final String HANDLER_SCRIPT =
            "objectClass('Account') { \n" +
            "  create { endpoint('accounts') { } } \n" +
            "} \n" +
            "objectClass('account') { \n" +
            "  search { endpoint('accounts') { emptyFilterSupported true } } \n" +
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
    public void createAndSearchDeclaredWithDifferentCaseMergeIntoOneHandler() {
        var connector = new TestConnector();
        connector.init(new TestConfiguration());

        var handler = connector.handlerFor(new ObjectClass("Account"));

        // Today exactly one of these throws UnsupportedOperationException: the "Account" and
        // "account" scripts build two separate handlers that both claim the ObjectClass("Account")
        // key, and whichever is inserted last into the final HashMap silently wins.
        handler.checkSupported(CreateOperation.class);
        handler.checkSupported(ExecuteQueryProcessor.class);
    }
}
