/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

import java.nio.charset.StandardCharsets

/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

// TODO pseudocode
//objectClass("User") {
//    search {
//        attributeResolver {
//            attribute "group"
//            resolutionType PER_OBJECT
//            implementation {
//                var groups = objectClass("Group").searchAll()
//                var userGroups  = new ArrayList()
//                for (var g : groups) {
//
//                    // TODO pseudocode, i.e. complex attr value fetch
//                    var oId =  g.members.id;
//                    var oType =  g.members._type;
//
//                    if("User".equals(oType)){
//                        if(value.value.uid.equals(oId)){
//                            userGroups.add(g);
//                            continue;
//                        }
//                    }
//                }
//                value.addAttribute("group", userGroups)
//            }
//        }
//    }
//}


objectClass("Group") {
    search {
        endpoint("users/") {
            responseFormat JSON_ARRAY
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                        .queryParameter("page", paging.pageOffset)
            }
            supportedFilter(attribute("group").eq().anySingleValue()) {
                String filter = "[{ \"group\": { \"operator\": \"=\", \"values\": [\"${value.value.uid}\"] } }]"
                request.queryParameter("filters", URLEncoder.encode(filter, StandardCharsets.UTF_8.toString()))
            }
        }
    }
}