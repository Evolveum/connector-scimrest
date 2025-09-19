import org.json.JSONArray

/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Group") {

    search {
        endpoint("groups/") {
            objectExtractor {

                if(response.body()==null){
                    return new JSONArray();
                }

                return response.body().get("_embedded").get("elements");
            }
            emptyFilterSupported true
        }
        endpoint("groups/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}