objectClass("Role") {
    attribute("id") {
        jsonType "integer";
        openApiFormat "int64";
        creatable false;
        updateable false;
        description "Role id";
    }
    attribute("name") {
        jsonType "string";
        creatable false;
        updateable false;
        description "Role name";
    }
}