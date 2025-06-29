/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("User") {
    search {

        endpoint("/users/search") {
            objectExtractor {
                return response.body().get("data")
            }
            pagingSupport {
                request.queryParameter("limit", paging.pageSize)
                       .queryParameter("page", paging.pageOffset)
            }
            emptyFilterSupported true

            supportedFilter(attribute("id").eq().anySingleValue()) {
                // API weirdness of Forgejo - attribute is called id, but query parameter is called uid
                request.queryParameter("uid",value)
            }

            supportedFilter(attribute("login").contains().anySingleValue()) {
                request.queryParameter("q", value)
            }
        }

        // This is actually get user by username
        endpoint("/users/{username}") {
            singleResult()
            supportedFilter(attribute("login").eq().anySingleValue()) {
                request.pathParameter("username", value)
            }
        }

        /*
        endpoint("teams/{id}/members") {
            responseFormat JSON_ARRAY
            supportedFilter(attribute("teams").eq().anySingleValue()) {
                request.pathParameter("id", value.value.uid)
            }
            pagingSupport {
                request.queryParameter("limit", paging.pageSize)
                        .queryParameter("page", paging.pageOffset)
            }
        }
        */
    }
}