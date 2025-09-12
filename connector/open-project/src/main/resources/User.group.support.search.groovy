import org.identityconnectors.framework.common.objects.ConnectorObjectReference

/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("User") {
    search {
        attributeResolver {
            attribute "group"
            resolutionType PER_OBJECT
            implementation {
                var userGroups = new ArrayList()
                var uFilter = objectClass("User").attributeFilter("id").eq(value)
                // Let's get all user organizations
                var user = objectClass("User").search(uFilter)

                for (var u : users) {

                    // TODO pseudocode, i.e. complex attr value fetch
                    def hFilter =  u.memberships.href;
                    def filter = hFilter.substring(hFilter.indexOf("?") + 1)

                    memberships = objectClass("Membership").search(filter)
                    // For each team we need to get it's all members, because there is no endpoint which allows us
                    // to search teams by it's member.
                    for (var membership : memberships) {

                        def principalHref = membership.principal.href;
//                        var teamMembersFilter = objectClass("User").attributeFilter("team")
//                                .eq(team);
//                        var usersInTeam = objectClass("User").search(teamMembersFilter)
//                        if (usersInTeam.any {it.uid.equals(value.build().uid)}) {
//                            userTeams.add(new ConnectorObjectReference(team))
//                        }
                    }
                }
                value.addAttribute("group", userGroups)
            }
        }
    }
}


objectClass("Group") {
    search {
        endpoint("users/") {
            responseFormat JSON_ARRAY
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                        .queryParameter("page", paging.pageOffset)
            }
            supportedFilter(attribute("group").eq().anySingleValue()) {
                //TODO why value.value.name???
                request.queryParameter("group", value.value.name)
            }
        }
    }
}