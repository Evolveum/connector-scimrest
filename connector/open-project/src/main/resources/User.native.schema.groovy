/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("User") {
    attribute("admin") {
        jsonType "boolean";
        updateable true;
        creatable false;
        readable true;
        returnedByDefault true;
        description "Flag indicating whether or not the user is an admin";
    }
    attribute("avatar") {
        jsonType "string";
        openApiFormat "uri";
        updateable false;
        creatable false;
        readable true;
        returnedByDefault true;
        description "URL to user’s avatar";
    }

    attribute("created_at") {
        jsonType "string";
        openApiFormat "date-time";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        description "Time of creation";
    }
    attribute("email") {
        jsonType "string";
        openApiFormat "email";
        updateable true;
        creatable false;
        readable true;
        returnedByDefault true;
        description "User’s email address";
    }
    attribute("firstName") {
        jsonType "string";
        updateable true;
        creatable true;
        readable true;
        returnedByDefault true;
        description "User’s first name";
    }

    attribute("id") {
        jsonType "integer";
        readable true;
        returnedByDefault true;
        required true;
        description "User’s id";
    }

    attribute("identity_url") {
        jsonType "string";
        updateable true;
        creatable true;
        readable true;
        returnedByDefault true;
        description "User’s identity_url for OmniAuth authentication";
    }
    attribute("language") {
        jsonType "string";
        creatable true;
        updateable true;
        readable true;
        returnedByDefault true;
        description "User’s language";
    }
    attribute("lastName") {
        jsonType "string";
        creatable true;
        updateable true;
        readable true;
        returnedByDefault true;
        description "User’s last name";
    }

    attribute("login") {
        jsonType "string";
        creatable true;
        updateable true;
        readable true;
        returnedByDefault true;
        required true;
        description "User’s login name";
    }
    //TODO association ....
//    attribute("memberships") {
//        jsonType "reference";
//        description "An href to the collection of the principal's memberships.";
//    }
    attribute("name") {
        jsonType "string";
        readable true;
        returnedByDefault true;
        description "User’s full name, formatting depends on instance settings";
    }
    attribute("password") {
        jsonType "string";
        updateable true;
        creatable true;
        readable true;
        returnedByDefault false;
        description "User’s password for the default password authentication";
    }
    //TODO complex type??? ....
//    attribute("status") {
//        jsonType "status";
//        readable true;
//        returnedByDefault true;
//        description "The current activation status of the user (see below)";
//    }
    attribute("updated_at") {
        jsonType "string";
        openApiFormat "date-time"
        readable true;
        returnedByDefault true;
        description "Time of the most recent change to the user";
    }
}
