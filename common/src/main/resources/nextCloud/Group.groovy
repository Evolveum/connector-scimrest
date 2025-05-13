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