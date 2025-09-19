/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
import org.identityconnectors.framework.common.objects.ConnectorObject
import org.json.JSONArray

import java.nio.charset.StandardCharsets


objectClass("Membership") {
    search {
        normalize {
            toSingleValue "roles"
            rewriteUid {
                if(value == null
                ){
                    return original;
                }
                def ref = (ConnectorObject) value.getValue()
                return original + ":" + ref.uid.uidValue
            }
            rewriteName {
                if(value == null
                ){
                    return original
                }
                def ref = (ConnectorObject) value.getValue()
                return original + ":" + ref.name.nameValue
            }
            restoreUid {
                return original.substring(0, original.indexOf(':'));
            }
            restoreName {

                return original.substring(0, original.indexOf(':'));
            }
        }

        endpoint("memberships/") {
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

            supportedFilter(attribute("principal").eq().anySingleValue()) {

                var valList = value.value.uid.getValue();
                var val =  valList.get(0);

                String filter = "[{ \"principal\": { \"operator\": \"=\", \"values\": [\"${val}\"] } }]"
                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
            }

        }
        endpoint("memberships/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}