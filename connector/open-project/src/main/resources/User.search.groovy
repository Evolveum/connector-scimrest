/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

import org.json.JSONArray

import java.nio.charset.StandardCharsets
objectClass("User") {
    search {

        endpoint("users/") {
            objectExtractor {
                if(response.body()==null){
                    return new JSONArray();
                }

                var jsonArray = response.body().get("_embedded").get("elements");
                return jsonArray;
            }
            pagingSupport {
                request.queryParameter("pageSize", paging.pageSize)
                        .queryParameter("offset", paging.pageOffset)
            }
            emptyFilterSupported true
            supportedFilter(attribute("name").eq().anySingleValue()) {
                String filter = "[{ \"name\": { \"operator\": \"=\", \"values\": [\"${value}\"] } }]"
                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
            }
            supportedFilter(attribute("name").contains().anySingleValue()) {

                String filter = "[{ \"name\": { \"operator\": \"~\", \"values\": [\"${value}\"] } }]"
                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
            }
// TODO connid error, operations not supported for __NAME attr
//            supportedFilter(attribute("login").eq().anySingleValue()) {
//
//                String filter = "[{ \"login\": { \"operator\": \"=\", \"values\": [\"${value}\"] } }]"
//                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
//            }
//            supportedFilter(attribute("login").contains().anySingleValue()) {
//
//                String filter = "[{ \"login\": { \"operator\": \"&=\", \"values\": [\"${value}\"] } }]"
//                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
//            }

// TODO complex attrs
//            supportedFilter(attribute("status").contains().anySingleValue()) {
//                request.queryParameter("filters",value)
//            }
//            supportedFilter(attribute("status").eq().anySingleValue()) {
//                request.queryParameter("filters",value)
//            }
//            supportedFilter(attribute("group").eq().anySingleValue()) {
//                request.queryParameter("filters",value)
//            }
//            supportedFilter(attribute("group").contains().anySingleValue()) {
//                request.queryParameter("filters",value)
//            }
        }
        endpoint("users/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}