objectClass("Project") {
    attribute("id") {
        jsonType "integer";
        openApiFormat "int64";
        description "Projects’ id";
    }
    attribute("identifier") {
        jsonType "string";
    }
    attribute("name") {
        jsonType "string";
    }
    attribute("active") {
        jsonType "boolean";
        description "Indicates whether the project is currently active or already archived\t";
    }
    attribute("statusExplanation") {
        jsonType "formattable";
        description "A text detailing and explaining why the project has the reported status";
    }
    attribute("public") {
        jsonType "boolean";
        description "Indicates whether the project is accessible for everybody";
    }
    attribute("description") {
        jsonType "formattable";
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
        description "Time of the most recent change to the project";
    }
}