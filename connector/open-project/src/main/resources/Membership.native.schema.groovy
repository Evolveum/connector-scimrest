/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Membership") {
    attribute("id") {
        jsonType "integer";
        readable true;
        returnedByDefault true;
        required true;
        description "Membership id";
    }
    attribute("created_at") {
        jsonType "string";
        openApiFormat "date-time"
        readable true;
        returnedByDefault true;
        description "Time of creation";
    }

    attribute("updated_at") {
        jsonType "string";
        openApiFormat "date-time"
        readable true;
        returnedByDefault true;
        description "Time of latest update";
    }
}
