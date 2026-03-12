/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package scim.aws

objectClass("User") {
    create {
        scim {
            /*
             *
             */
        }
    }

    update {
        scim {
            patch {
                supportedAttributes "externalId", "displayName", "nickName", "profileUrl", "title", "userType",
                        "preferredLanguage", "locale", "timezone", "name", "enterprise", "emails", "addresses", "phoneNumbers"

                supportedAttribute("userName") {
                    limitations {
                        operations ADD, REPLACE
                        maxPerRequest 1
                    }
                }

                supportedAttribute("active") {
                    limitations {
                        operations ADD, REPLACE
                        maxPerRequest 1
                    }
                }

            }

            put {

            }
        }
    }

    search {
        scim {
            limitations {
                supportedFilter attribute("externalId").eq().anySingleValue() // Here could go
                supportedFilter attribute("userName").eq().anySingleValue()
                supportedFilter attribute("groups").child("value").eq().anySingleValue()
            }
        }
    }
}

objectClass("Group") {
    create {
        scim {
            supportedAttributes "displayName", "externalId"
            // AWS supports adding max 100 members during create
            supportedAttribute("members") {
                limitations {
                    maxPerRequest 100
                }
            }
        }
    }

    update {
        scim {
            patch {
                supportedAttributes "displayName", "externalId"

                supportedAttribute("members") {
                    limitations {
                        maxPerRequest 100
                    }
                }
            }
        }
    }
}
