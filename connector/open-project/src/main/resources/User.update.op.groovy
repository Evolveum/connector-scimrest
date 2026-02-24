objectClass("User") {
    update {
        endpoint(PATCH, "/users/{id}") {
            request {
                contentType APPLICATION_JSON
            }
            supportedAttributes "admin", "email","login", "language","lastName", "firstName", "status"
        }

        endpoint(POST, "/users/{id}/lock") {
            request {
                body EMPTY
            }
            supportedAttribute("status") {
                value "locked"
            }
        }

        endpoint(DELETE, "/users/{id}/lock") {
            request {
                body EMPTY
            }
            supportedAttribute("status") {
                value "active"
            }
        }
    }
}
