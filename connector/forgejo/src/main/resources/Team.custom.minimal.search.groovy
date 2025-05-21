objectClass("Team") {
    search {
        custom {
            emptyFilterSupported true
            implementation {
                def orgs = objectClass("Organization").search(null)
                for (def org :orgs) {
                    objectClass("Team").search(attributeFilter("organization.name").eq(org.get("name")), resultHandler)
                }
            }
        }
    }
}