/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("User") {
    search {

        endpoint("users/") {
            objectExtractor {
                var jsonArray = response.body().get("_embedded").get("elements");
                return jsonArray;
            }
            pagingSupport {
                request.queryParameter("pageSize", paging.pageSize)
                       .queryParameter("offset", paging.pageOffset)
            }
            emptyFilterSupported true

            supportedFilter(attribute("name").eq().anySingleValue()) {
                request.queryParameter("filters",value)
            }
            supportedFilter(attribute("name").contains().anySingleValue()) {
                request.queryParameter("filters",value)
            }
            supportedFilter(attribute("login").eq().anySingleValue()) {
                request.queryParameter("filters",value)
            }
            supportedFilter(attribute("login").contains().anySingleValue()) {
                request.queryParameter("filters",value)
            }
        }
        endpoint("users/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}