/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Organization") {
    attribute("avatar_url") {
        jsonType "string"
    }
    attribute("description") {
        jsonType "string"
    }
    attribute("email") {
        jsonType "string"
    }
    attribute("full_name") {
        jsonType "string"
    }
    attribute("id") {
        jsonType "integer"
        openApiFormat "int64"
    }
    attribute("location") {
        jsonType "string"
    }
    attribute("name") {
        jsonType "string"
    }
    attribute("username") {
        jsonType "string"
    }
    attribute("visibility") {
        jsonType "string"
    }
    attribute("website") {
        jsonType "string"
    }
    attribute("repo_admin_change_team_access") {
        jsonType "boolean"
    }
}