/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package nextCloud

objectClass("Group") {
    attribute("search") {
        jsonType "string";
        description "optional search string";
    }
    attribute("limit") {
        jsonType "int";
        openApiFormat "int64";
        description "optional limit value";
    }
    attribute("offset") {
        jsonType "int";
        openApiFormat "int64";
        description "optional offset value";
    }
}