/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Organization") {
    search {
        endpoint("orgs") {
            responseFormat JSON_ARRAY
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                       .queryParameter("page", paging.pageOffset)
            }
        }

        endpoint("orgs/{org}") {
            singleResult()
            supportedFilter(attribute("name").eq().anySingleValue()) {
                request.pathParameter("org", value)
            }
        }
    }
}