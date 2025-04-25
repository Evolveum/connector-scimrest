objectClass "User" {
    attribute "active" {
        jsonType "boolean";
    }
    attribute "avatar_url" {
        jsonType "string";
    }
    attribute "created" {
        jsonType "string";
        openApiFormat "$date-time";
    }
    attribute "description" {
        jsonType "string";
    }
    attribute "email" {
        jsonType "string";
        format "email";
    }
    attribute "followers_count" {
        jsonType "integer";
        format "int64";
    }
    attribute "following_count" {
        jsonType "integer";
        format "int64";
    }
    attribute "full_name" {
        jsonType "string";
    }
    attribute "html_url" {
        jsonType "string";
    }
    attribute "id" {
        jsonType "integer";
        format "int64";
    }
    attribute "is_admin" {
        jsonType "boolean";
    }
    attribute "language" {
        jsonType "string";
    }
    attribute "last_login" {
        jsonType "string";
        openApiFormat "$date-time";
    }
    attribute "location" {
        jsonType "string";
    }
    attribute "login" {
        jsonType "string";
    }
    attribute "login_name" {
        jsonType "string";
        default "empty";
    }
    attribute "prohibit_login" {
        jsonType "boolean";
    }
    attribute "pronouns" {
        jsonType "string";
    }
    attribute "restricted" {
        jsonType "boolean";
    }
    attribute "source_id" {
        jsonType "integer";
        format "int64";
    }
    attribute "starred_repos_count" {
        jsonType "integer";
        format "int64";
    }
    attribute "visibility" {
        jsonType "string";
    }
    attribute "website" {
        jsonType "string";
    }
}
