package openProject

objectClass("Group") {
    attribute("id") {
        jsonType "integer";
        openApiFormat "int64";
        creatable false;
        updateable false;
        description "Group’s id";
    }
    attribute("name") {
        jsonType "string";
        description "Group’s full name, formatting depends on instance settings";
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
//  "name": "Emperor's guard",
//  "_links": {
//    "members": [
//      {
//        "href": "/api/v3/users/42"
//      },
//      {
//        "href": "/api/v3/users/43"
//      },
//      {
//        "href": "/api/v3/users/44"
//      }
//    ]
//  }
//}