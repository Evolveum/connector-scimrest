/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

import java.nio.charset.StandardCharsets

objectClass("User") {

 // Return projects without the "role" information
//    search {
//        endpoint("projects/") {
//            responseFormat JSON_ARRAY
//            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
//                request.queryParameter("limit", paging.pageSize)
//                        .queryParameter("page", paging.pageOffset)
//            }
//            supportedFilter(attribute("project").eq().anySingleValue()) {
//
//                String filter = "[{ \"principal\": { \"operator\": \"=\", \"values\": [\"${value.value.uid}\"] } }]"
//                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
//            }
//        }
//    }


    // This should return a membership, it should be a complex reference which has a relationship to a project, relation can be deduced from the ID (or name ?)
    search {
        endpoint("memberships/") {
            responseFormat JSON_ARRAY
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                        .queryParameter("page", paging.pageOffset)
            }
            supportedFilter(attribute("project").eq().anySingleValue()) {

                String filter = "[{ \"principal\": { \"operator\": \"=\", \"values\": [\"${value.value.uid}\"] } }]"
                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
            }
        }
    }
}