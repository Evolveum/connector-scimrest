package scim.schema.dev

objectClass("User") {
    // SCIM specific mappings for whole User schema (customizations)
    scim {
    // This  also could shortcut for creating container enterprise with attributes inside
        extension("enterprise", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    }
    // Here could be attribute customizations
}

/** Custom association between User and Office (custom SCIM type) **/
relationship("OfficeEmployment") {
    subject("User") {
        attribute "office" {
            type EMULATED // Denotes that attribute does not exists in user schema, but will be emulated by connector.
        }
    }
    object("Office") {
        attribute "employees"
    }
    // No need to specify anything more -> details could be partially derived from SCIM schema
    // but association is not described there.
}
