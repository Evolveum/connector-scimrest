objectClass("User") {

    create {
        endpoint(POST, "users") {
            request {
                contentType APPLICATION_JSON
            }
            /*
            response {
                contentType APPLICATION_HAL_JSON
            }*/
        }
    }

    update {
        endpoint(PATCH, "/users/{id}") {
            request {
                contentType APPLICATION_JSON
            }
            // Attributes that are updatable for a User.
            // NOTE: 'password' is only writable on creation (see UserCreateModel),
            //       so you may want to exclude it from updates.
            // NOTE: 'identityUrl' is deprecated and will be removed in the future.
            supportedAttributes "admin", "avatar", "firstName", "language",
                    "lastName", "login", "email", "name"
        }

        // Locking endpoint – no body required.
        endpoint(POST, "/users/{id}/lock") {
            request {
                body EMPTY
            }
            supportedAttribute("status") {
                transition "active", "locked"
            }
        }

        // Unlocking endpoint – no body required.
        endpoint(DELETE, "/users/{id}/lock") {
            supportedAttribute("status") {
                transition "locked", "active"
            }

        }
    }

    delete {
        endpoint(DELETE, "/users/{id}")
    }
}