/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
objectClass("User") {
    attribute("admin") {
        jsonType "boolean";
        readable true;
        updateable true;
        creatable true;
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
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        description "User’s email address";
    }
    attribute("firstName") {
        jsonType "string";
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        description "User’s first name";
    }

    attribute("id") {
        jsonType "integer";
        readable true;
        updateable false;
        creatable false;
        returnedByDefault true;
        required true;
        description "User’s id";
    }

    attribute("identity_url") {
        jsonType "string";
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        description "User’s identity_url for OmniAuth authentication";
    }
    attribute("language") {
        jsonType "string";
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        description "User’s language";
    }
    attribute("lastName") {
        jsonType "string";
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        description "User’s last name";
    }

    attribute("login") {
        jsonType "string";
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required true;
        description "User’s login name";
    }

    attribute("status") {
        jsonType "string";
        readable true;
        updateable true;
        creatable true;
        returnedByDefault true;
        required true;
        description "Status";
    }

    //TODO association ....
//    attribute("memberships") {
//        jsonType "reference";
//        description "An href to the collection of the principal's memberships.";
//    }
    attribute("name") {
        jsonType "string";
        readable true;
        creatable false;
        updatable false;
        returnedByDefault true;
        description "User’s full name, formatting depends on instance settings";
    }
    attribute("password") {
        jsonType "string";
        updateable true;
        creatable true;
        readable false;
        description "User’s password for the default password authentication";
    }

    attribute("updated_at") {
        jsonType "string";
        openApiFormat "date-time"
        readable true;
        creatable false;
        updatable false;
        returnedByDefault true;
        description "Time of the most recent change to the user";
    }
}
