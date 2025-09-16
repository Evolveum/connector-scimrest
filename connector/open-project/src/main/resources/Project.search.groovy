/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
import java.nio.charset.StandardCharsets


objectClass("Project") {

    search {
        endpoint("projects/") {
            objectExtractor {
                var jsonArray = response.body().get("_embedded").get("elements");
                return jsonArray;
            }
            pagingSupport {
                request.queryParameter("pageSize", paging.pageSize)
                        .queryParameter("offset", paging.pageOffset)
            }
            emptyFilterSupported true

            // TODO connid error, operations not supported for __UID attr
//            supportedFilter(attribute("id").contains().anySingleValue()) {
//
//                String filter = "[{ \"id\": { \"operator\": \"&=\", \"values\": [\"${value}\"] } }]"
//                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
//            }
        }

        endpoint("projects/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}