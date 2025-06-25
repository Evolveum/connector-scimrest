/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
relationship("UserOrganizationMembership") {
    subject("User") {
        attribute("organization") {
            resolver {
                resolutionType PER_OBJECT
                search {
                    attributeFilter("member").eq(value)
                }
            }
        }
    }
    object("Organization") {
        attribute("member") {
            resolver {
                resolutionType PER_OBJECT
                search {
                    attributeFilter("organization").eq(value)
                }
            }
        }
    }
}
