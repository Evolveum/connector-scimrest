objectClass("User") {
    attribute("active") {
        jsonType "boolean";
        updateable true;
        description "Is user active";
    }
    attribute("avatar_url") {
        jsonType "string";
        description "URL to the user's avatar";
    }
    attribute("created_at") {
        jsonType "string";
        openApiFormat "date-time";
        creatable true;
        description "The date and time when the user was created";
    }
    attribute("description") {
        jsonType "string";
        description "the user's description";
    }
    attribute("email") {
        jsonType "string";
        openApiFormat "email";
        updateable true;
        description "The user's email address";
    }
    attribute("followers_count") {
        jsonType "integer";
        openApiFormat "int64";
        description "Number of users following this user";
    }
    attribute("following_count") {
        jsonType "integer";
        openApiFormat "int64";
        description "Number of users this user is following";
    }
    attribute("full_name") {
        jsonType "string";
        creatable true;
        updateable true;
        description "the user's full name";
    }
    attribute("html_url") {
        jsonType "string";
        description "URL to the user's profile page";
    }
    attribute("id") {
        jsonType "integer";
        openApiFormat "int64";
        description "The unique identifier for the user";
    }
    attribute("is_admin") {
        jsonType "boolean";
        description "Is the user an administrator";
    }
    attribute("language") {
        jsonType "string";
        description "UserSchema locale";
    }
    attribute("last_login") {
        jsonType "string";
        openApiFormat "date-time";
        description "The date and time when the user last logged in";
    }
    attribute("location") {
        jsonType "string";
        updateable true;
        description "the user's location";
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
    attribute "must_change_password" {
        jsonType "boolean";
        creatable true;
        updateable true;
        readable false;
    }
    attribute "password" {
        jsonType "string";
        creatable true;
        updateable true;
        readable false;
    }
    attribute "prohibit_login" {
        jsonType "boolean";
        updateable true;
        description "Is user login prohibited";
    }
    attribute "pronouns" {
        jsonType "string";
        updateable true;
        description "the user's pronouns";
    }
    attribute "restricted" {
        jsonType "boolean";
        creatable true;
        updateable true;
        description "Is user restricted";
    }
    attribute "source_id" {
        jsonType "integer";
        format "int64";
        creatable true;
        updateable true;
        description "The ID of the user's Authentication Source";
    }
    attribute "starred_repos_count" {
        jsonType "integer";
        format "int64";
        description "Number of repositories starred by this user";
    }

    attribute "visibility" {
        jsonType "string";
        updateable true;
        description "UserSchema visibility level option: public, limited, private";
        enumeration ["public", "limited", "private"];
    }
    attribute "website" {
        jsonType "string";
        description "the user's website";
    }

    connIdAttribute "UID" "id";
    connIdAttribute "NAME" "login";
    connIdAttribute "ENABLE" "active";
    connIdAttribute "LAST_LOGIN_DATE" "last_login_date";
    connIdAttribute "LOCK_OUT" "prohibit_login";
    connIdAttribute "SHORT_NAME" "full_name";
    connIdAttribute "DESCRIPTION" "description";

}
