/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
relationship("_UserGroupMembership") {
    subject("User") {
        attribute ("groups") {
            scim {
                mapping {
                    // or reference
                    // Here should be mapping how to create ObjectReference from properties
                    // UID from $ref (transformation) or value (directly referenced)
                    // objectClass (can be constant)
                }
            }
        }
    }
    object("Group") {
        attribute("members")
    }
}

relationship("_GroupGroupMembership") {
    subject("Group") {
        attribute ("groups") {
            resolver {
                resolutionType PER_OBJECT
                // If not specified, we can assume search is performed on other side of relation
                search {
                    attributeFilter("members").eq(value)
                }
            }
        }
    }
    object("Group") {
        attribute("members")
    }
}