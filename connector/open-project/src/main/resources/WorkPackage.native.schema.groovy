objectClass("WorkPackage") {

    attribute("displayId") {
        description """The user-facing identifier for the work package.
Its format depends on the `work_packages_identifier` setting.
When set to `semantic`: the project-based identifier (e.g. "PROJ-42").
When set to `classic`: the numeric ID as a string (e.g. "123")."""
        readable true
        json {
            type "string"
        }
    }

    attribute("id") {
        description "Work package id"
        readable true
        updateable false
        creatable false
        json {
            type "integer"
        }
    }

    attribute("subject") {
        description "The subject of the work package"
        required true
        creatable true
        readable true
        updatable true
        json {
            type "string"
        }
    }

    attribute("type") {
        description "The type of the work package"
        required true
        creatable true
        readable true
        updatable true
        json {
            type "string"
            path attribute("_links").child("type").child("href")
        }
    }

    attribute("project") {
        description "The workspace to which the work package belongs"
        required true
        creatable true
        readable true
        updatable true
        json {
            type ("string")
            openApiFormat ("uri-reference")
            path attribute("_links").child("project").child("href")
        }
    }

    attribute("status") {
        description "The current status of the work package"
        required true
        creatable true
        updatable true
        json {
            type("string")
            path attribute("_links").child("status").child("href")
        }
    }

    attribute("priority") {
        description "The priority of the work package"
        required true
        creatable true
        readable true
        updatable true
        json {
            type("string")
            path attribute("_links").child("priority").child("href")
        }
    }

    attribute("description.value") {
        json{
            path attribute("description").child("raw")
            type("string")
        }
        creatable true
        readable true
        updatable true
        returnedByDefault false
    }
    attribute("description.format") {
        json{
            path attribute("description").child("format")
            type("string")
        }
        creatable true
        readable true
        updatable true
        returnedByDefault false
    }

    attribute("description.html") {
        json{
            path attribute("description").child("format")
            type("string")
        }
        creatable true
        readable true
        updatable true
        returnedByDefault false
    }
}