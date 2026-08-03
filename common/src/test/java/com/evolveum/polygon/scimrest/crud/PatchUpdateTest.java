/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the update operation with a PATCH endpoint: {@code endpoint(PATCH, ...)} sends
 * the changed attributes as a JSON body via the PATCH HTTP method.
 */
public class PatchUpdateTest extends AbstractCrudConnectorTest {

    private static final String SCRIPT_TEMPLATE = """
        objectClass("Account") {
            search {
                endpoint("accounts/{id}") {
                    singleResult()
                    supportedFilter(attribute("id").eq().anySingleValue()) {
                        request.pathParameter("id", value)
                    }
                }
            }
            update {
                %s
            }
        }
        """;

    private static final String SCRIPT = """
            endpoint(PATCH, "accounts/{id}") {
                request { contentType APPLICATION_JSON }
            }
            """;;

    private static final String SCRIPT_UPDATE_W_PATH_PARAM_IDENTITY = """
            endpoint(PATCH, "accounts/{attribute}") {
                pathParameter("attribute")
                request { contentType APPLICATION_JSON }
            }
            """;
    private static final String SCRIPT_UPDATE_W_PATH_PARAM_EXTRACTOR = """
            endpoint(PATCH, "accounts/{attribute}") {
                pathParameter("attribute", "__NAME__")
                request { contentType APPLICATION_JSON }
            }
            """;
    private static final String SCRIPT_UPDATE_W_PATH_PARAM_INCORRECT = """
            endpoint(PATCH, "accounts/{attribute}") {
                pathParameter("attribute", "abc")
                request { contentType APPLICATION_JSON }
            }
            """;

    @Test
    public void updateIsSentAsPatchWithJsonBody() {
        updateAsPatchWithJsonBody(SCRIPT_TEMPLATE.formatted(SCRIPT), ACCOUNT_BY_ID_PATH);
    }

    @Test
    public void updateIsSentAsPatchWithJsonBodyPathParameterImplicitIdentity() {
        updateAsPatchWithJsonBody(SCRIPT_TEMPLATE.formatted(SCRIPT_UPDATE_W_PATH_PARAM_IDENTITY), ACCOUNT_BY_ID_PATH);
    }

    @Test
    public void updateIsSentAsPatchWithJsonBodyPathParameterAttributeExtractor() {
        updateAsPatchWithJsonBody(SCRIPT_TEMPLATE.formatted(SCRIPT_UPDATE_W_PATH_PARAM_EXTRACTOR), ACCOUNT_BY_OLD_NAME_PATH);
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void updateIsSentAsPatchWithJsonBodyPathParameterIncorrectAttribute() {
        updateAsPatchWithJsonBody(SCRIPT_TEMPLATE.formatted(SCRIPT_UPDATE_W_PATH_PARAM_INCORRECT), ACCOUNT_BY_ID_PATH);
    }

    public void updateAsPatchWithJsonBody(String script, String path){
        wireMockServer.stubFor(get(urlEqualTo(path))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"old-name\"}")));
        wireMockServer.stubFor(get(urlEqualTo(ACCOUNT_BY_ID_PATH))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"old-name\"}")));
        wireMockServer.stubFor(patch(urlPathMatching(ACCOUNTS_PATTERN))
                .willReturn(okJson("{\"id\":\"123\",\"name\":\"new-name\"}")));

        initConnector(script).updateDelta(new ObjectClass("Account"),
                new Uid("123"),
                Set.of(AttributeDeltaBuilder.build(Name.NAME, List.of("new-name"))),
                new OperationOptionsBuilder().build());

        assertEquals(wireMockServer.findAll(patchRequestedFor(urlEqualTo(path))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("new-name")))).size(), 1);
    }
}
