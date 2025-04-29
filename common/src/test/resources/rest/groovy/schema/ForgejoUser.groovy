objectClass("User") {
    attribute("active") {
        jsonType "boolean";
        updateable true;
        description "Is user active";
    }
    attribute("email") {
        jsonType "string";
        openApiFormat "email";
        updateable true;
        description "The user's email address";
    }
    attribute("full_name") {
        jsonType "string";
        creatable true;
        updateable true;
        description "the user's full name";
    }
    attribute("id") {
        jsonType "integer";
        openApiFormat "int64";
        description "The unique identifier for the user";
    }
    attribute("last_login") {
        jsonType "string";
        openApiFormat "date-time";
        description "The date and time when the user last logged in";
    }
    attribute("login") {
        jsonType "string";
        description "the user's username";
    }
    attribute("login_name") {
        jsonType "string";
        creatable true;
        updateable true;
        description "The user's authentication sign-in name.";
    }
    attribute("password") {
        jsonType "string";
        creatable true;
        updateable true;
        readable false;
    }
    attribute("website") {
        jsonType "string";
        description "the user's website";
    }

    connIdAttribute "UID" "id";
    connIdAttribute "NAME" "login";
    connIdAttribute "ENABLE" "active";
    connIdAttribute "LAST_LOGIN_DATE" "last_login_date";


}
