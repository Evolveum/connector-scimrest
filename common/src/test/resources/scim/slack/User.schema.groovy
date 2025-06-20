package scim.slack

objectClass("User") {
    scim {
        // Only listed attributes are supported
        onlyExplicitlyListed true
        // FIXME: define format how to map extension to container (short names are not part of protocol)
        extension("slack", "urn:ietf:params:scim:schemas:extension:slack:profile:2.0:User")
        extension("enterprise", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    }

    attribute("userName")
    attribute("nickName")
    attribute("displayName")
    attribute("title")
    attribute("email") {
        scim {
            path attribute("emails").firstValue().child("value")
        }
    }
    attribute("profile_photo") {
        scim {
            path attribute("photos").firstValue().child("values")
        }
    }
    attribute("password")
    reference("groups")

    /** Slack Extension Attributes **/


}