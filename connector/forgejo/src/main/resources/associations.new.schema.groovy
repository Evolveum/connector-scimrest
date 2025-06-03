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
