/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;

/**
 * ConnId defines {@code pagedResultsOffset} as the 1-based index of the first requested
 * record, not as a page number. These tests run searches the way midPoint issues them
 * against a remote that holds 311 users and serves them page by page.
 */
public class PagedResultsOffsetTest extends AbstractCrudConnectorTest {

    private static final int TOTAL_USERS = 311;

    private static final String SCRIPT_TEMPLATE = """
            objectClass("Account") {
                search {
                    endpoint("accounts") {
                        responseFormat JSON_ARRAY
                        emptyFilterSupported true
                        %s
                        pagingSupport {
                            request.queryParameter("pageSize", paging.pageSize)
                                   .queryParameter("page", paging.pageOffset)
                        }
                    }
                }
            }
            """;

    @Test
    public void offsetOneReturnsFirstPage() {
        stubRemote(50);

        assertEquals(searchIds(50, 1), range(1, 50));
    }

    @Test
    public void offsetAtPageBoundaryReturnsNextPage() {
        stubRemote(50);

        assertEquals(searchIds(50, 51), range(51, 100));
        assertEquals(requestCount("2"), 1);
        assertEquals(requestCount("51"), 0);
    }

    @Test
    public void offsetInsideFirstPageReturnsShiftedWindow() {
        stubRemote(50);

        assertEquals(searchIds(50, 2), range(2, 51));
    }

    @Test
    public void offsetInsideLaterPageReturnsShiftedWindow() {
        stubRemote(50);

        assertEquals(searchIds(50, 76), range(76, 125));
    }

    @Test
    public void lastShortPageIsReturned() {
        stubRemote(50);

        assertEquals(searchIds(50, 301), range(301, 311));
    }

    @Test
    public void windowOverlappingEndOfResultsIsTruncated() {
        stubRemote(50);

        assertEquals(searchIds(50, 290), range(290, 311));
    }

    @Test
    public void offsetPastEndReturnsNothing() {
        stubRemote(50);

        assertEquals(searchIds(50, 400), List.of());
    }

    @Test
    public void smallWindowNotAlignedToPagesIsCutFromTheMiddle() {
        stubRemote(10);

        assertEquals(searchIds(10, 46), range(46, 55));
    }

    @Test
    public void remotePageCapSmallerThanWindowAtBoundary() {
        stubRemote(20);

        assertEquals(searchIds(50, 51, 20), range(51, 100));
    }

    @Test
    public void remotePageCapSmallerThanWindowWithShift() {
        stubRemote(20);

        assertEquals(searchIds(50, 2, 20), range(2, 51));
    }

    @Test
    public void remotePageCapIsRespectedInEveryRequest() {
        stubRemote(20);

        searchIds(50, 51, 20);

        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", equalTo("20"))).size(), 3);
        assertEquals(wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("pageSize", notMatching("20"))).size(), 0);
    }

    @Test
    public void consecutivePagesCoverEveryUserExactlyOnce() {
        stubRemote(50);

        List<Integer> collected = new ArrayList<>();
        for (var offset = 1; offset <= TOTAL_USERS; offset += 50) {
            collected.addAll(searchIds(50, offset));
        }

        assertEquals(collected, range(1, TOTAL_USERS));
    }

    @Test
    public void consecutivePagesWithRemoteCapCoverEveryUserExactlyOnce() {
        stubRemote(20);

        List<Integer> collected = new ArrayList<>();
        for (var offset = 1; offset <= TOTAL_USERS; offset += 50) {
            collected.addAll(searchIds(50, offset, 20));
        }

        assertEquals(collected, range(1, TOTAL_USERS));
    }

    @Test
    public void unpagedSearchStillReturnsEveryUser() {
        stubRemote(25);

        var results = search(initConnector(script(null)), null, buildOptions());

        assertEquals(results.size(), TOTAL_USERS);
    }

    private List<Integer> searchIds(int pageSize, int offset) {
        return searchIds(pageSize, offset, null);
    }

    private List<Integer> searchIds(int pageSize, int offset, Integer maxPageSize) {
        var connector = initConnector(script(maxPageSize));
        return search(connector, null, buildOptions(buildPageEntries(pageSize, offset))).stream()
                .map(o -> Integer.valueOf(o.getUid().getUidValue()))
                .toList();
    }

    private void stubRemote(int remotePageSize) {
        var pages = (TOTAL_USERS + remotePageSize - 1) / remotePageSize;
        for (var page = 1; page <= pages + 1; page++) {
            var from = (page - 1) * remotePageSize + 1;
            var to = Math.min(page * remotePageSize, TOTAL_USERS);
            wireMockServer.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH))
                    .withQueryParam("pageSize", equalTo(String.valueOf(remotePageSize)))
                    .withQueryParam("page", equalTo(String.valueOf(page)))
                    .willReturn(okJson(usersJson(from, to))));
        }
    }

    private int requestCount(String page) {
        return wireMockServer.findAll(getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH))
                .withQueryParam("page", equalTo(page))).size();
    }

    private static String script(Integer maxPageSize) {
        return SCRIPT_TEMPLATE.formatted(maxPageSize != null ? "maxPageSize " + maxPageSize : "");
    }

    private static List<Integer> range(int fromInclusive, int toInclusive) {
        return IntStream.rangeClosed(fromInclusive, toInclusive).boxed().toList();
    }

    private static String usersJson(int fromId, int toId) {
        var sb = new StringBuilder("[");
        for (var i = fromId; i <= toId; i++) {
            if (i > fromId) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(i).append("\",\"name\":\"user-").append(i).append("\"}");
        }
        return sb.append(']').toString();
    }
}
