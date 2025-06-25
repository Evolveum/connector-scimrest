/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package openProject

objectClass("Role") {
    attribute("id") {
        jsonType "integer";
        openApiFormat "int64";
        creatable false;
        updateable false;
        description "Role id";
    }
    attribute("name") {
        jsonType "string";
        creatable false;
        updateable false;
        description "Role name";
    }
}