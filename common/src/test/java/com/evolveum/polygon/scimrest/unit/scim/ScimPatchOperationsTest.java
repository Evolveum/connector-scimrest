/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.scim;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.scimrest.groovy.connector.AbstractGroovyRestConnector;
import com.evolveum.polygon.scimrest.impl.scim.ScimPatchOperations;
import com.evolveum.polygon.scimrest.schema.RestObjectClassDefinition;
import com.evolveum.polygon.scimrest.schema.RestSchemaBuilderImpl;
import com.unboundid.scim2.common.messages.PatchOpType;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.testng.annotations.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link ScimPatchOperations}: the ConnId delta to SCIM PATCH operation mapping,
 * the per-attribute limitations (operations, maxPerRequest, pinned value), and the request-body
 * rendering.
 */
public class ScimPatchOperationsTest {

    private static final JsonNodeFactory FACTORY = new JsonNodeFactory();

    private RestObjectClassDefinition buildObjectClass() {
        var schema = new RestSchemaBuilderImpl(AbstractGroovyRestConnector.class, null);
        var loader = new GroovySchemaLoader(new GroovyContext(), schema);
        loader.load("""
                objectClass("User") {
                    attribute("userName") { scim { path("userName"); type "string" } }
                    attribute("displayName") { scim { path("displayName"); type "string" } }
                    attribute("active") { scim { path("active"); type "boolean" } }
                    attribute("emails") { scim { path("emails"); type "string" } }
                    attribute("givenName") { scim { path("name.givenName"); type "string" } }
                }
                """);
        return schema.objectClass("User").build();
    }

    @Test
    public void replaceDeltaBecomesReplaceOp() {
        var oc = buildObjectClass();
        var requests = ScimPatchOperations.build(
                List.of(AttributeDeltaBuilder.build("displayName", List.of("New Name"))), oc, null);

        assertEquals(requests.size(), 1);
        var op = requests.get(0).get(0);
        assertEquals(op.op(), PatchOpType.REPLACE);
        assertEquals(op.path(), "displayName");
        assertEquals(op.value().asString(), "New Name");
    }

    @Test
    public void booleanReplaceBecomesReplaceOp() {
        var oc = buildObjectClass();
        var requests = ScimPatchOperations.build(
                List.of(AttributeDeltaBuilder.build("active", List.of(true))), oc, null);

        var op = requests.get(0).get(0);
        assertEquals(op.op(), PatchOpType.REPLACE);
        assertEquals(op.path(), "active");
        assertEquals(op.value().asBoolean(), true);
    }

    @Test
    public void addDeltaBecomesAddOpWithArrayValue() {
        var oc = buildObjectClass();
        var delta = new AttributeDeltaBuilder().setName("emails").addValueToAdd("a@x", "b@x").build();
        var requests = ScimPatchOperations.build(List.of(delta), oc, null);

        var op = requests.get(0).get(0);
        assertEquals(op.op(), PatchOpType.ADD);
        assertEquals(op.path(), "emails");
        assertTrue(op.value().isArray());
        assertEquals(op.value().size(), 2);
    }

    @Test
    public void removeDeltaBecomesRemoveOpWithValue() {
        var oc = buildObjectClass();
        var delta = new AttributeDeltaBuilder().setName("emails").addValueToRemove("old@x").build();
        var requests = ScimPatchOperations.build(List.of(delta), oc, null);

        var op = requests.get(0).get(0);
        assertEquals(op.op(), PatchOpType.REMOVE);
        assertEquals(op.path(), "emails");
        assertEquals(op.value().asString(), "old@x");
    }

    @Test
    public void emptyReplaceBecomesClearRemoveWithoutValue() {
        var oc = buildObjectClass();
        var requests = ScimPatchOperations.build(
                List.of(AttributeDeltaBuilder.build("active", new ArrayList<>())), oc, null);

        var op = requests.get(0).get(0);
        assertEquals(op.op(), PatchOpType.REMOVE);
        assertEquals(op.path(), "active");
        assertNull(op.value());
    }

    @Test
    public void nestedScimPathIsUsed() {
        var oc = buildObjectClass();
        var requests = ScimPatchOperations.build(
                List.of(AttributeDeltaBuilder.build("givenName", List.of("Jane"))), oc, null);

        var op = requests.get(0).get(0);
        assertEquals(op.op(), PatchOpType.REPLACE);
        assertEquals(op.path(), "name.givenName");
        assertEquals(op.value().asString(), "Jane");
    }

    @Test
    public void addAndRemoveInOneDeltaProduceTwoOps() {
        var oc = buildObjectClass();
        var delta = new AttributeDeltaBuilder().setName("emails")
                .addValueToAdd("new@x").addValueToRemove("old@x").build();
        var requests = ScimPatchOperations.build(List.of(delta), oc, null);

        assertEquals(requests.size(), 2);
        assertEquals(requests.get(0).get(0).op(), PatchOpType.REMOVE);
        assertEquals(requests.get(1).get(0).op(), PatchOpType.ADD);
    }

    @Test
    public void unsupportedOperationIsRejected() {
        var oc = buildObjectClass();
        var config = Map.of("emails", new ScimPatchOperations.PatchAttrConfig(Set.of(PatchOpType.ADD), 1, null));
        var replaceDelta = AttributeDeltaBuilder.build("emails", List.of("a@x"));

        assertThrows(ConnectorException.class,
                () -> ScimPatchOperations.build(List.of(replaceDelta), oc, config));
    }

    @Test
    public void supportedOperationIsAccepted() {
        var oc = buildObjectClass();
        var config = Map.of("emails", new ScimPatchOperations.PatchAttrConfig(Set.of(PatchOpType.ADD, PatchOpType.REMOVE), 1, null));
        var addDelta = new AttributeDeltaBuilder().setName("emails").addValueToAdd("a@x").build();

        var requests = ScimPatchOperations.build(List.of(addDelta), oc, config);
        assertEquals(requests.get(0).get(0).op(), PatchOpType.ADD);
    }

    @Test
    public void maxPerRequestSplitsAddIntoMultipleRequests() {
        var oc = buildObjectClass();
        var config = Map.of("emails", new ScimPatchOperations.PatchAttrConfig(null, 1, null));
        var delta = new AttributeDeltaBuilder().setName("emails").addValueToAdd("a@x", "b@x", "c@x").build();

        var requests = ScimPatchOperations.build(List.of(delta), oc, config);
        assertEquals(requests.size(), 3);
        for (var request : requests) {
            assertEquals(request.get(0).op(), PatchOpType.ADD);
            assertEquals(request.get(0).value().size(), 1);
        }
    }

    @Test
    public void replaceOverflowBecomesClearThenChunkedAdd() {
        var oc = buildObjectClass();
        var config = Map.of("emails", new ScimPatchOperations.PatchAttrConfig(null, 2, null));
        var delta = AttributeDeltaBuilder.build("emails", List.of("a@x", "b@x", "c@x", "d@x", "e@x"));

        var requests = ScimPatchOperations.build(List.of(delta), oc, config);
        // 1 clear + ceil(5/2) = 3 chunked adds
        assertEquals(requests.size(), 4);
        assertEquals(requests.get(0).get(0).op(), PatchOpType.REMOVE);
        assertNull(requests.get(0).get(0).value());
        assertEquals(requests.get(1).get(0).op(), PatchOpType.ADD);
        assertEquals(requests.get(1).get(0).value().size(), 2);
        assertEquals(requests.get(2).get(0).value().size(), 2);
        assertEquals(requests.get(3).get(0).value().size(), 1);
    }

    @Test
    public void pinnedValueOverridesDeltaValue() {
        var oc = buildObjectClass();
        var config = Map.of("emails", new ScimPatchOperations.PatchAttrConfig(null, 1, List.of("pinned@x")));
        var delta = AttributeDeltaBuilder.build("emails", List.of("other@x"));

        var requests = ScimPatchOperations.build(List.of(delta), oc, config);
        var op = requests.get(0).get(0);
        assertEquals(op.op(), PatchOpType.REPLACE);
        assertEquals(op.value().asString(), "pinned@x");
    }

    @Test
    public void toPatchBodyRendersSchemasAndOperations() {
        var ops = List.of(
                new ScimPatchOperations.PatchOp(PatchOpType.REPLACE, "active", FACTORY.booleanNode(true)),
                new ScimPatchOperations.PatchOp(PatchOpType.REMOVE, "emails", null));
        var body = ScimPatchOperations.toPatchBody(ops);

        assertEquals(body.get("schemas").get(0).asString(), "urn:ietf:params:scim:api:messages:2.0:PatchOp");
        var operations = body.get("Operations");
        assertEquals(operations.size(), 2);
        assertEquals(operations.get(0).get("op").asString(), "replace");
        assertEquals(operations.get(0).get("path").asString(), "active");
        assertEquals(operations.get(0).get("value").asBoolean(), true);
        assertEquals(operations.get(1).get("op").asString(), "remove");
        assertEquals(operations.get(1).get("path").asString(), "emails");
        assertNull(operations.get(1).get("value"));
    }
}
