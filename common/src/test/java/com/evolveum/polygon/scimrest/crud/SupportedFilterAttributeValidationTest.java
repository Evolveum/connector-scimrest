/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies build-phase validation of attribute names referenced from
 * {@code supportedFilter} and {@code supportedAttribute} definitions: an unknown
 * name must fail with a descriptive {@link ConfigurationException} (naming the
 * attribute, the object class and the available attributes) instead of a
 * {@code NullPointerException}.
 */
public class SupportedFilterAttributeValidationTest extends AbstractCrudConnectorTest {

    @Test
    public void searchEndpointWithUnknownFilterAttributeFails() {
        var script = """
                objectClass("Account") {
                    search {
                        endpoint("accounts") {
                            supportedFilter(attribute("login").contains().anySingleValue()) {
                                request.queryParameter("q", value)
                            }
                        }
                    }
                }
                """;
        var connector = initConnector(script);

        var exception = Assert.expectThrows(Exception.class, () -> searchAccounts(connector));

        var cause = firstCause(exception, ConfigurationException.class);
        assertNotNull(cause);
        assertTrue(cause.getMessage().contains("Attribute 'login' not found in object class 'Account'"),
                "Unexpected message: " + cause.getMessage());
        assertTrue(cause.getMessage().contains("when defining a search filter for endpoint 'accounts'"));
        assertTrue(cause.getMessage().contains("Available attributes"));
    }

    @Test
    public void createEndpointWithUnknownSupportedAttributeFails() {
        var script = """
                objectClass("Account") {
                    create {
                        endpoint("accounts") {
                            supportedAttribute("login")
                        }
                    }
                }
                """;
        var connector = initConnector(script);

        var exception = Assert.expectThrows(Exception.class, () -> createAccount(connector));

        var cause = firstCause(exception, ConfigurationException.class);
        assertNotNull(cause);
        assertTrue(cause.getMessage().contains("Attribute 'login' not found in object class 'Account'"),
                "Unexpected message: " + cause.getMessage());
        assertTrue(cause.getMessage().contains("when defining supported attributes for create endpoint 'accounts'"));
        assertTrue(cause.getMessage().contains("Available attributes"));
    }

    @Test
    public void updateEndpointWithUnknownSupportedAttributeFails() {
        var script = """
                objectClass("Account") {
                    update {
                        endpoint(PUT, "accounts/{id}") {
                            request { contentType APPLICATION_JSON }
                            supportedAttribute("login")
                        }
                    }
                }
                """;
        var connector = initConnector(script);

        var exception = Assert.expectThrows(Exception.class, () -> updateAccount(connector));

        var cause = firstCause(exception, ConfigurationException.class);
        assertNotNull(cause);
        assertTrue(cause.getMessage().contains("Attribute 'login' not found in object class 'Account'"),
                "Unexpected message: " + cause.getMessage());
        assertTrue(cause.getMessage().contains("when defining supported attributes for update endpoint 'accounts/{id}'"));
        assertTrue(cause.getMessage().contains("Available attributes"));
    }

    private static Throwable firstCause(Throwable throwable, Class<? extends Throwable> type) {
        for (var cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return cause;
            }
        }
        return null;
    }
}
