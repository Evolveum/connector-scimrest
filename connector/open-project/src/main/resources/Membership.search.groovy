/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
import org.identityconnectors.framework.common.objects.ConnectorObject


objectClass("Membership") {
    search {
        normalize {
            toSingleValue "roles"
            rewriteUid {
                def ref = (ConnectorObject) value.getValue()
                return original + ":" + ref.uid.uidValue
            }
            rewriteName {
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
                var jsonArray = response.body().get("_embedded").get("elements");
                return jsonArray;
            }
            pagingSupport {
                request.queryParameter("pageSize", paging.pageSize)
                        .queryParameter("offset", paging.pageOffset)
            }
            emptyFilterSupported true

        }
        endpoint("memberships/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value)
            }
        }
    }
}