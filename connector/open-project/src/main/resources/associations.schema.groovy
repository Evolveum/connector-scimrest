/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
relationship("UserGroupMembership") {
    subject("User") {
        attribute("group") {
            resolver {
                resolutionType PER_OBJECT
                search {
                    attributeFilter("member").eq(value)
                }
            }
        }
    }
    object("Group") {
        attribute("member") {
            resolver {
                resolutionType PER_OBJECT
                search {
                    attributeFilter("group").eq(value)
                }
            }
        }
    }
}
