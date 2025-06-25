/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
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