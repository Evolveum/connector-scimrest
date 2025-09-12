/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

// TODO change also in VAIA foundry, params not right?
objectClass("Principal") {
    attribute("_hint") {
        jsonType "String";
        readable true;
        returnedByDefault true;
    }
    attribute("_type") {
        jsonType "String";
        readable true;
        returnedByDefault true;
    }
    attribute("id") {
        jsonType "integer";
        readable true;
        returnedByDefault true;
        required true;
        description "User’s id";
    }
}
