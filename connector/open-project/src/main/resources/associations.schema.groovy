/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
relationship("UserProjectMembership") {
    subject("User") {
        attribute("membership") {
            resolver {
                resolutionType PER_OBJECT
                search {
                    attributeFilter("principal").eq(value)
                }
            }
        }
    }
    object("Membership") {
        attribute("principal")
    }
}
