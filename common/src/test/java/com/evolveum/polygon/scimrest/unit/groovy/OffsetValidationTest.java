/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.unit.groovy;

import com.evolveum.polygon.scimrest.groovy.search.DeclarativePagingHandler;
import com.evolveum.polygon.scimrest.groovy.search.EndpointBasedSearchBuilder.PagingParameter;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class OffsetValidationTest {

    @DataProvider
    public Object[][] invalidMappings() {
        var offset = new PagingParameter("offset", "query", "start");
        var size = new PagingParameter("pageSize", "query", "limit");
        return new Object[][] {
                {List.of(new PagingParameter("cursor", "query", "cursor")), "owner", "Unknown paging token 'cursor'"},
                {List.of(new PagingParameter("offset", "cookie", "start")), "owner", "Unknown paging location 'cookie'"},
                {List.of(new PagingParameter("offset", "body", "start")), "owner", "requires the endpoint httpOperation to be POST"},
                {List.of(new PagingParameter("offset", "query", ""), size), "owner", "paging parameter must be a nonempty request parameter name"},
                {List.of(offset, size), "owner&admin", "sourceParameter must be a nonempty request parameter name"},
                {List.of(offset, size, offset), "owner", "duplicate paging token 'offset'"},
                {List.of(offset, size), "start", "paging and source parameter destinations must be distinct"},
                {List.of(offset), "owner", "requires offset and pageSize paging tokens"}
        };
    }

    @Test(dataProvider = "invalidMappings")
    public void testErrorContext(List<PagingParameter> parameters, String sourceParameter, String expected) {
        // Given: Invalid mappings for a named attribute
        var attribute = "memberships";
        var endpoint = "/tenant/memberships";

        // When: Validate the relationship paging declaration
        var failure = expectThrows(ConfigurationException.class,
                () -> DeclarativePagingHandler.validateOffset(parameters, endpoint, sourceParameter, true, attribute));

        // Then: Include context and preserve original failure
        assertTrue(failure.getCause() instanceof ConfigurationException);
        assertTrue(failure.getCause().getMessage().contains(expected), failure.getCause().getMessage());
        assertEquals(failure.getMessage(), "Invalid offset paging for attribute 'memberships' at endpoint '/tenant/memberships': " + failure.getCause().getMessage());
    }
}
