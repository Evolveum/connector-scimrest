/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("User") {
    reference("organization") {
        subtype "UserOrganizationMembership"
        returnedByDefault false
        multiValued true
        description "Virtual relationship shortcut to organization (determined by teams membership)"
        updateable false
        //creatable false
        role SUBJECT
    }

    reference("teams") {
        subtype "UserTeamMembership"
        returnedByDefault false
        multiValued true
        updateable true
        //creatable false
        role SUBJECT
    }

}
objectClass("Organization") {

    reference("member") {
        objectClass "User"
        subtype "UserOrganizationMembership"
        returnedByDefault false
        multiValued true
        role OBJECT
    }
    reference("teams") {
        objectClass "Team"
        subtype "TeamOrganizationMembership"
        returnedByDefault false
        multiValued true
        role OBJECT
    }
}

objectClass("Team") {
    reference("organization") {
        objectClass "Organization"
        subtype "TeamOrganizationMembership"
        returnedByDefault true
        required true
        role SUBJECT;
    }

    reference("members") {
        objectClass "User"
        subtype "UserTeamMembership"
        returnedByDefault false
        multiValued true
        role OBJECT;
    }
}

