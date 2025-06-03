import org.identityconnectors.framework.common.objects.ConnectorObjectReference

objectClass("User") {
    search {
        attributeResolver {
            attribute "team"
            resolutionType PER_OBJECT
            implementation {
                var userTeams = new ArrayList()
                var orgFilter = objectClass("Organization").attributeFilter("member").eq(value)
                // Let's get all user organizations
                var orgs = objectClass("Organization").search(orgFilter)

                // For each organization we need to find teams
                for (var org : orgs) {
                    def teamFilter = attributeFilter("organization.name").eq(org.name.nameValue)
                    teamsInOrg = objectClass("Team").search(teamFilter, resultHandler)
                    // For each team we need to get it's all members, because there is no endpoint which allows us
                    // to search teams by it's member.
                    for (var team : teamsInOrg) {
                        var teamMembersFilter = objectClass("User").attributeFilter("team")
                            .eq(team);
                        var usersInTeam = objectClass("User").search(teamMembersFilter)
                        if (usersInTeam.any {it.uid.equals(value.build().uid)}) {
                            userTeams.add(new ConnectorObjectReference(team))
                        }
                    }
                }
                value.addAttribute("team", userTeams)
            }
        }
    }
}
