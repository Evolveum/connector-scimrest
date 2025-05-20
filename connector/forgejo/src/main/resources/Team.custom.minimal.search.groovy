objectClass("Team") {
    custom {
        emptyFilterSupported true
        implementation {
            def orgs = objectClass("Organization").search(null)
            for (def org :orgs) {
                objectClass("Team").search(filter("id").eq(org.get("id")), resultHandler)
            }
        }
    }
}