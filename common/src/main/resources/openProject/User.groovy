objectClass("User") {
    attribute("id") {
        jsonType "integer";
        openApiFormat "int64";
        creatable false;
        updateable false;
        description "User’s id";
    }
    attribute("login") {
        jsonType "string";
        description "User’s login name";
    }
    attribute("firstName") {
        jsonType "string";
        description "User’s first name";
    }
    attribute("lastName") {
        jsonType "string";
        description "User’s last name";
    }
    attribute("name") {
        jsonType "string";
        creatable false;
        updateable false;
        description "User’s full name, formatting depends on instance settings";
    }
    attribute("email") {
        jsonType "string";
        openApiFormat "email";
        description "User’s email address";
    }
    attribute("admin") {
        jsonType "boolean";
        description "Flag indicating whether or not the user is an admin";
    }
    attribute("avatar") {
        jsonType "string";
        creatable false;
        updateable false;
        description "URL to user’s avatar";
    }
    attribute("status") {
        jsonType "string";
        creatable false;
        updateable false;
        description "The current activation status of the user (see below)";
    }
    attribute("language") {
        jsonType "string";
        description "User’s language";
    }
    attribute("password") {
        jsonType "string";
        readable false;
        description "User’s password for the default password authentication";
    }
    attribute("identity_url") {
        jsonType "string";
        description "User’s identity_url for OmniAuth authentication";
    }
    attribute("createdAt") {
        jsonType "string";
        openApiFormat "dateTime";
        creatable false;
        updateable false;
        description "Time of creation";
    }
    attribute("updatedAt") {
        jsonType "string";
        openApiFormat "dateTime";
        creatable false;
        updateable false;
        description "Time of the most recent change to the user";
    }
}

// JSON example
//{
//  "login": "j.sheppard",
//  "password": "idestroyedsouvereign",
//  "firstName": "John",
//  "lastName": "Sheppard",
//  "email": "shep@mail.com",
//  "admin": true,
//  "status": "active",
//  "language": "en"
//}