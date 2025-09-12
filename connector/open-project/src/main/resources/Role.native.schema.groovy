/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("Role") {
    description "When principals (groups or users) are assigned to a project, they are receive roles in that project."

    attribute("id") {
        jsonType "integer";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        required true;
        description "Role id";
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
}