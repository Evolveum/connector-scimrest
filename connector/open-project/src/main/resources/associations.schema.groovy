/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
relationship("UserProjectPrincipal") {
    subject("User") {
        attribute("project") {
            resolver {
                resolutionType PER_OBJECT
                search {
                    attributeFilter("project").eq(value)
                }
            }
        }
    }
    object("Project") {
        attribute("member") {
            resolver {
                resolutionType PER_OBJECT
                search {
                    attributeFilter("member").eq(value)
                }
            }
        }
    }
}
