/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */


import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.json.JSONArray

import java.nio.charset.StandardCharsets

objectClass("User") {
    search {
        endpoint("users/") {
            objectExtractor {
                if (response.body() == null) {
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
        }

        custom {
//            // This search supports empty filter
            emptyFilterSupported false
            supportedFilter(FilterSpecification.attribute("admin").eq().anySingleValue());
            supportedFilter(FilterSpecification.attribute("language").eq().anySingleValue());

            implementation {

                def filter = filter();
                if (filter instanceof EqualsFilter) {

                    Set<String> handledAttributes = Set.of("admin", "language");

                    def attrName = ((EqualsFilter) filter).getName();
                    if (handledAttributes.contains(attrName)) {
                        def attr = ((EqualsFilter) filter).getAttribute();
                        def valList = attr.getValue();
                        def val;
                        if (valList.size() == 1) {
                            val = valList.get(0);
                        }
                        def Users = objectClass("User").search();
                        for (def user : Users) {
                            def userAttrs = user.getAttributes();
                            for (Attribute attribute : userAttrs) {
                                if (attribute.getName().equals(attrName)) {
                                    def isAdmin = attribute.getValue().get(0);
                                    if (isAdmin == val) {
                                        resultHandler().handle(user)
                                    }
                                }
                            }
                        }
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