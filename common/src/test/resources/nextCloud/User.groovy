/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package nextCloud

objectClass("User") {
    attribute("userid") {
        jsonType "string";
        description "the required username for the new user";
    }
    attribute("password") {
        jsonType "string";
        description "the password for the new user, leave empty to send welcome mail";
    }
    attribute("displayName") {
        jsonType "string";
        description "the display name for the new user";
    }
    attribute("email") {
        jsonType "string";
        description "the email for the new user, required if password empty";
    }
    attribute("groups") {
        jsonType "array";
        description "the groups for the new user";
    }
    attribute("subadmin") {
        jsonType "array";
        description "the groups in which the new user is subadmin";
    }
    attribute("quota") {
        jsonType "string";
        description "quota for the new user";
    }
    attribute("language") {
        jsonType "string";
        description "language for the new user";
    }
}