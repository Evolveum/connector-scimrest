/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.scimrest.config.RestClientConfiguration;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.groovy.handler.GroovyRestHandlerBuilder;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Script validation of {@code relationship(...)} definitions (bug #12425): a participant block
 * that declares no reference attribute fails with a {@code ConfigurationException} message —
 * relationship name, offending participant, and the fix hint — instead of the raw
 * {@code NullPointerException} from the post-execution wiring.
 */
public class RelationshipValidationTest {

    private static final String DEPLOYED_SCHEMA =
            "objectClass('User') { attribute('id').connId().type(String.class) }";

    /** The bug #12425 shape: the object participant block declares no attribute. */
    private static final String OBJECT_WITHOUT_ATTRIBUTE = """
            relationship("MembershipProject") {
                subject("Membership") {
                    attribute("project") {
                    }
                }
                object("Project") {
                    // No reverse (object) attribute is defined (objectAttribute is empty).
                }
            }
            """;

    /** Symmetric shape: the subject participant block declares no attribute. */
    private static final String SUBJECT_WITHOUT_ATTRIBUTE = """
            relationship("MembershipProject") {
                subject("Membership") {
                    // No reference attribute declared.
                }
                object("Project") {
                    attribute("memberships") {
                    }
                }
            }
            """;

    /** The supported shape: both participants declare a reference attribute. */
    private static final String TWO_SIDED_RELATIONSHIP = """
            relationship("MembershipProject") {
                subject("Membership") {
                    attribute("project") {
                    }
                }
                object("Project") {
                    attribute("memberships") {
                    }
                }
            }
            """;

    /** owner(...) after the attribute is declared — the supported ordering. */
    private static final String OWNER_AFTER_ATTRIBUTE = """
            relationship("MembershipProject") {
                subject("Membership") {
                    attribute("project") {
                    }
                    owner(true)
                }
                object("Project") {
                    attribute("memberships") {
                    }
                }
            }
            """;

    /** owner(...) before the attribute is declared — a configuration error. */
    private static final String OWNER_BEFORE_ATTRIBUTE = """
            relationship("MembershipProject") {
                subject("Membership") {
                    owner(true)
                    attribute("project") {
                    }
                }
                object("Project") {
                    attribute("memberships") {
                    }
                }
            }
            """;

    /** The exact original (uncustomized) script from the bug #12425 ticket. */
    private static final String BUG_ORIGINAL_SCRIPT = """
            import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
            import org.identityconnectors.framework.common.objects.ConnectorObjectReference
            import org.identityconnectors.framework.common.objects.ObjectClass

            relationship("membership_to_project") {
                subject("Membership") {
                    attribute("project") {
                        // The Membership stores a reference (link) to the Project.
                        owner()                       // This side owns the relation data.
                        resolver {
                            resolutionType PER_OBJECT
                            search {
                                // Resolve the Project instance referenced by the Membership.
                                attributeFilter("project").eq(value)
                            }
                        }
                    }
                }
                object("Project") {
                    // No reverse (object) attribute is defined (objectAttribute is empty).
                }
            }
            """;

    public static class TestConfiguration extends BaseGroovyConnectorConfiguration implements RestClientConfiguration {
        @Override public String getBaseAddress()      { return "http://localhost"; }
        @Override public String getRestTestEndpoint() { return null; }
        @Override public Boolean getTrustAllCertificates() { return true; }
    }

    public static class TestConnector extends AbstractGroovyRestConnector {
        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load(DEPLOYED_SCHEMA);
        }

        @Override
        protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {}
    }

    @Test
    public void objectParticipantWithoutAttribute_reportsConfigurationExceptionWithRelationshipContext() {
        var firstError = firstSchemaError(validateSchema(OBJECT_WITHOUT_ATTRIBUTE));

        assertEquals(firstError.get("phase"), "evaluate", "Unexpected result: " + firstError);
        var message = (String) firstError.get("message");
        assertTrue(message.contains("Relationship 'MembershipProject'"), "Unexpected message: " + message);
        assertTrue(message.contains("the object participant ('Project') declares no reference attribute"),
                "Unexpected message: " + message);
        assertTrue(message.contains("attribute('<name>')"), "Unexpected message: " + message);
        assertFalse(message.contains("NullPointerException"), "Unexpected message: " + message);
        assertFalse(message.contains("Cannot invoke"), "Unexpected message: " + message);
    }

    @Test
    public void subjectParticipantWithoutAttribute_reportsConfigurationExceptionWithRelationshipContext() {
        var firstError = firstSchemaError(validateSchema(SUBJECT_WITHOUT_ATTRIBUTE));

        assertEquals(firstError.get("phase"), "evaluate", "Unexpected result: " + firstError);
        var message = (String) firstError.get("message");
        assertTrue(message.contains("Relationship 'MembershipProject'"), "Unexpected message: " + message);
        assertTrue(message.contains("the subject participant ('Membership') declares no reference attribute"),
                "Unexpected message: " + message);
        assertFalse(message.contains("NullPointerException"), "Unexpected message: " + message);
        assertFalse(message.contains("Cannot invoke"), "Unexpected message: " + message);
    }

    @Test
    public void twoSidedRelationship_passesValidation() {
        var result = validateSchema(TWO_SIDED_RELATIONSHIP);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void ownerAfterAttributeDeclared_passesValidation() {
        var result = validateSchema(OWNER_AFTER_ATTRIBUTE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void ownerBeforeAttributeDeclared_reportsConfigurationException() {
        var firstError = firstSchemaError(validateSchema(OWNER_BEFORE_ATTRIBUTE));

        assertEquals(firstError.get("phase"), "evaluate", "Unexpected result: " + firstError);
        var message = (String) firstError.get("message");
        assertTrue(message.contains("owner() requires the reference attribute to be declared first"),
                "Unexpected message: " + message);
        assertFalse(message.contains("NullPointerException"), "Unexpected message: " + message);
    }

    @Test
    public void bug12425_originalScript_reportsConfigurationExceptionInsteadOfNpe() {
        var firstError = firstSchemaError(validateSchema(BUG_ORIGINAL_SCRIPT));

        assertEquals(firstError.get("phase"), "evaluate", "Unexpected result: " + firstError);
        var message = (String) firstError.get("message");
        assertTrue(message.contains("Relationship 'membership_to_project'"), "Unexpected message: " + message);
        assertTrue(message.contains("the object participant ('Project') declares no reference attribute"),
                "Unexpected message: " + message);
        assertFalse(message.contains("NullPointerException"), "Unexpected message: " + message);
        assertFalse(message.contains("Cannot invoke"), "Unexpected message: " + message);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static TestConnector connector() {
        var configuration = new TestConfiguration();
        configuration.setDevelopmentMode(true);
        var connector = new TestConnector();
        connector.init(configuration);
        return connector;
    }

    private static Map<String, Object> validateSchema(String script) {
        ScriptContext context = new ScriptContext("groovy", script, Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, ScriptValidationRequest.SCRIPT_OPERATION_BUILD,
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, "schema"));
        return (Map<String, Object>) connector().runScriptOnResource(context, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstSchemaError(Map<String, Object> result) {
        assertEquals(result.get("status"), "error", "Expected a validation error, got: " + result);
        return (Map<String, Object>) ((List<?>) result.get("errors")).getFirst();
    }
}
