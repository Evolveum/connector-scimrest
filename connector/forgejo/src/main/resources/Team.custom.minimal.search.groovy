objectClass("Team") {
    search {
        custom {
            emptyFilterSupported true
            implementation {
                def orgs = objectClass("Organization").search()
                for (def org :orgs) {
                    def teamFilter = attributeFilter("organization.name").eq(org.name.nameValue)
                    objectClass("Team").search(teamFilter, resultHandler)
                }
            }
        }
    }
}