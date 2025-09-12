/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Project") {

    // TODO maybe custom search would be a better idea if sorting, ordering and some other filters are needed....
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
            supportedFilter(attribute("name").eq().anySingleValue()) {
            }
        }

        endpoint("projects/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}