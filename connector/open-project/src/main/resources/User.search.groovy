/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */


import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ConnectorObject
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.json.JSONArray


objectClass("User") {
    var LOG = Log.getLog(this.getClass())

    search {
        endpoint("users/") {
            objectExtractor {

                if (response.body() == null) {
                    return new JSONArray()
                }
                var jsonArray = response.body().get("_embedded").get("elements")
                return jsonArray
            }
            pagingSupport {
                request.queryParameter("pageSize", paging.pageSize)
                        .queryParameter("offset", paging.pageOffset)
            }
            emptyFilterSupported true
            supportedFilter(attribute("name").eq().anySingleValue()) {
                String filter = "[{ \"name\": { \"operator\": \"=\", \"values\": [\"${value}\"] } }]"
                request.queryParameter("filters", filter)
            }
            supportedFilter(attribute("name").contains().anySingleValue()) {

                String filter = "[{ \"name\": { \"operator\": \"~\", \"values\": [\"${value}\"] } }]"
                request.queryParameter("filters", filter)
            }
        }

        custom {

            emptyFilterSupported false
            supportedFilter(FilterSpecification.attribute("admin").eq().anySingleValue())
            supportedFilter(FilterSpecification.attribute("language").eq().anySingleValue())

            implementation {
                if(LOG.isInfo()){

                    LOG.info("Query not supported by the remote system, handling explicitly in connector.")
                }
                EqualsFilter filter = filter() as EqualsFilter
                if(LOG.isInfo()){
                    LOG.info("Handling 'Equals Filter' based query for the attribute {0}",  filter.getName())
                }
                
                List<ConnectorObject> users = objectClass("User").search() as List<ConnectorObject>
                for (def user: users) {
                    if (filter.accept(user)) {
                        resultHandler().handle(user)
                    }
                }
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