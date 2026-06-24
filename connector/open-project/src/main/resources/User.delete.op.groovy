objectClass("User") {
    delete {
        endpoint(DELETE, "users/{id}")
    }
}