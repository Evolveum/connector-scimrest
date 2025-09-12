/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Group") {

    // TODO maybe custom search would be a better idea if sorting, ordering and some other filters are needed....
    search {
        endpoint("groups/") {
            objectExtractor {
                return response.body().get("_embedded").get("elements");
            }
            emptyFilterSupported true
            supportedFilter(attribute("name").eq().anySingleValue()) {
            }
        }
        endpoint("groups/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}