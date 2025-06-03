package scim.slack

objectClass("User") {
    scim {
        // Only listed attributes are supported
        onlyExplicitlyListed true
        // FIXME: define format how to map extension to container (short names are not part of protocol)
        extension("enterprise", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    }

    attribute("userName")
    attribute("nickName")
    attribute("displayName")
    attribute("title")
    attribute("email") {
        // FIXME: Define format for SCIM path
        scimPath "emails[0]['value']"
    }
    attribute("profile_photo") {
        // FIXME: Define format for SCIM path
        scimPath "photos[0]['values']"
    }
    attribute("password")
    reference("groups")

    /** Slack Extension Attributes **/


}