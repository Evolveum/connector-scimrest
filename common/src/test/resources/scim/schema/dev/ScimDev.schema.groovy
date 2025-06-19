package scim.schema.dev

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.ConnectorObjectReference
import org.identityconnectors.framework.common.objects.Name
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.Uid

objectClass("User") {
    // SCIM specific mappings for whole User schema (customizations)
    scim {
    // This  also could shortcut for creating container enterprise with attributes inside
        extension("enterprise", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    }
    // Here could be attribute customizations

    attribute("givenName") {
        // FIXME: This will be derived from SCIM Schema
        connId().type(String)
        scim {
            type "string"
            path attribute("name").child("givenName")
        }
    }
    attribute("familyName") {
        connId().type(String) // FIXME: This will be derived from SCIM Schema
        scim {
            type "string" // FIXME: THis will be derived from SCIM Schema
            path attribute("name").child("familyName")
        }
    }
    attribute("formattedName") {
        scim {
            type "string" // FIXME: THis will be derived from SCIM Schema based on path
            path attribute("name").child("formatted")
        }
    }
    attribute("primaryEmail") {
        scim {
            type "string" // FIXME: THis will be derived from SCIM Schema based on path
            path attribute("emails").valueFilter("primary", true).child("value")
        }
    }

    attribute("employeeNumber") {
        scim {
            type "string" // FIXME: THis will be derived from SCIM Schema based on path
            path extension("enterprise").child("employeeNumber")

        }
    }
}

objectClass("Office") {
    connIdAttribute("NAME", "name")
}


/** Custom association between User and Office (custom SCIM type) **/
relationship("OfficeEmployment") {
    subject("User") {
        attribute("office") {
            // Emulated true is implied by presence of resolver
            resolver {
                resolutionType PER_OBJECT
                // If not specified, we can assume search is performed on other side of relation
                search {
                    attributeFilter("employees").eq(value)
                }
            }
        }
    }
    object("Office") {
        attribute("employees") {
            scim {
                implementation {
                    deserialize {
                        //println(it)
                        var object = new ConnectorObjectBuilder();
                        object.setObjectClass(new ObjectClass("User"))
                        object.setUid(new Uid(it.get("id").asText()))
                        object.setName(new Name(it.get("display").asText()))
                        new ConnectorObjectReference(object.build())
                    }
                    serialize {

                    }
                }

            }
        }
    }
    // No need to specify anything more -> details could be partially derived from SCIM schema
    // but association is not described there.
}
