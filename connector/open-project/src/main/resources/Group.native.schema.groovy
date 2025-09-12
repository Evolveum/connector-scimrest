/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Group") {
    description "Team represents a team in an organization"

    attribute("id") {
        jsonType "integer";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        required true;
        description "User’s id";
    }
    attribute("name") {
        jsonType "string"
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required true;
        description "Group’s full name, formatting depends on instance settings";
    }
    attribute("created_at") {
        jsonType "string"
        openApiFormat "date-time";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        required false;
        description "Time of creation";
    }
    attribute("updated_at") {
        jsonType "string"
        openApiFormat "date-time";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        required false;
        description "Time of the most recent change to the user";
    }
}