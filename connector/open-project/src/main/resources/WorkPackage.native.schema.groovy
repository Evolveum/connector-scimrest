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
//        connId{
//            type String.class
//        }
        json {
            type "integer"
//            implementation {
//                deserialize {
//                    return value.toString();
//                }
//            }
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
            connId{
                type String.class
            }
            json {
                type "string"
                path attribute("_links").child("type").child("href")
                implementation {
                    deserialize {
                        return value.asText()
                    }
                    serialize {
                        return value
                    }
                }
            }
    }

        attribute("project") {
        description "The workspace to which the work package belongs"
        required true
        creatable true
        readable true
        updatable true
            connId{
                type String.class
            }
            json {
                type ("string")
                openApiFormat ("uri-reference")
                path attribute("_links").child("project").child("href")
                implementation {
                    deserialize {
                        return value.asText()
                    }
                    serialize {
                        return value
                    }
                }
            }
    }

        attribute("status") {
        description "The current status of the work package"
        required true
        creatable true
        updatable true
            connId{
                type String.class
            }
            json {
                type("string")
                path attribute("_links").child("status").child("href")
                implementation {
                    deserialize {
                        return value.asText()
                    }
                    serialize {
                        return value
                    }
                }
            }
    }

        attribute("priority") {
        description "The priority of the work package"
        required true
        creatable true
        readable true
        updatable true
            connId{
                type String.class
            }
            json {
                type("string")
                path attribute("_links").child("priority").child("href")
                implementation {
                    deserialize {
                        return value.asText()
                    }
                    serialize {
                        return value
                    }
                }
            }
    }
}