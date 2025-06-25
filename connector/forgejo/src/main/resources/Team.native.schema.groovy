/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Team") {
    description "Team represents a team in an organization"

    attribute("id") {
        jsonType "integer"
        openApiFormat "int64"
    }
    attribute("name") {
        jsonType "string"
    }
    attribute("description") {
        jsonType "string"
    }
    attribute("visibility") {
        jsonType "string"
    }
    attribute("permission") {
        jsonType "string"
    }
    // FIXME: This should be nested object of organization and somehow allow filtering to specify it.
    attribute("organization.name") {
        jsonType "string"
    }
}