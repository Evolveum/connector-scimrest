objectClass("workpackage") {
    create {
        endpoint(POST, "/work_packages") {
            request {
                contentType APPLICATION_JSON
            }
        }
    }
}