/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

objectClass("Principal") {
    description("Principals are the superclass of users, groups and placeholder users")
    embedded true
    attribute("name") {
        jsonType "string";
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

//    attribute("href") {
//        jsonType "string";
//        readable true;
//        returnedByDefault true;
//    }
//    attribute("title") {
//        jsonType "string";
//        readable true;
//        returnedByDefault true;
//        required true;
//        description "User’s id";
//    }
}
