/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.exception;

import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.connector.BaseRestGroovyConnectorConfiguration;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.scimrest.impl.scim.ScimHttpErrorException;
import com.evolveum.polygon.scimrest.support.WireMockTestSupport;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.net.URI;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Guarantees the top-level ConnId boundary translates a {@link ScimHttpErrorException} into a
 * standard built-in ICF exception, even if an inner handler forgets to do so. The ConnId base
 * rethrows any {@code ConnectorException} verbatim, so without this guarantee the custom carrier
 * would reach the ConnId layer as an unrecognized type.
 */
public class ScimBoundaryTranslationTest extends WireMockTestSupport {

    @AfterMethod
    public void tearDown() {
        tearDownWireMock();
    }

    @Test
    public void leakingScim401BecomesInvalidCredentialAtBoundary() {
        setUpWireMock();
        var config = new TestConfiguration(wireMockServer.port());
        var connector = new LeakingHandlerConnector(401, "unauthorized");
        connector.init(config);

        var e = expectThrows(ConnectorException.class,
                () -> connector.executeQuery(new ObjectClass("Account"), null, r -> true,
                        new OperationOptionsBuilder().build()));
        assertFalse(e instanceof ScimHttpErrorException,
                "The custom ScimHttpErrorException must not reach the ConnId layer, got: " + e.getClass().getName());
        assertTrue(e instanceof InvalidCredentialException,
                "A 401 must surface as InvalidCredentialException, got: " + e.getClass().getName());
    }

    /** A handler that deliberately forgets to translate the SCIM HTTP error (simulating a bug). */
    private static final class LeakingHandlerConnector extends AbstractGroovyRestConnector {
        private final int status;
        private final String detail;

        LeakingHandlerConnector(int status, String detail) {
            this.status = status;
            this.detail = detail;
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            // empty schema is sufficient for this test
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
        }

        @Override
        public ObjectClassHandler handlerFor(ObjectClass objectClass) {
            return new ObjectClassHandler() {
                @Override
                @SuppressWarnings("unchecked")
                public <T extends ObjectClassOperation> T checkSupported(Class<T> operationType) {
                    if (operationType == ObjectSearchOperation.class) {
                        return (T) (ObjectSearchOperation) (context, filter, handler, options) -> {
                            // Deliberately throw the raw custom carrier (no translation).
                            throw new ScimHttpErrorException(status, detail, null,
                                    URI.create("http://localhost/scim/v2/Users"));
                        };
                    }
                    throw new UnsupportedOperationException("Operation not supported: " + operationType);
                }

                @Override
                public ObjectClass objectClass() {
                    return new ObjectClass("Account");
                }
            };
        }
    }

    /** Plain REST configuration (no SCIM), so no discovery request is made. */
    private static final class TestConfiguration extends BaseRestGroovyConnectorConfiguration {
        private final int port;

        TestConfiguration(int port) {
            this.port = port;
        }

        @Override
        public String getBaseAddress() {
            return "http://localhost:" + port;
        }
    }
}
