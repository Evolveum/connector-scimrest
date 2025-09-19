import org.json.JSONArray

/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Role") {

    search {
        endpoint("roles/") {
            objectExtractor {

                if(response.body()==null){
                    return new JSONArray();
                }

                var jsonArray = response.body().get("_embedded").get("elements");
                return jsonArray;
            }
            emptyFilterSupported true
        }

        endpoint("roles/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}