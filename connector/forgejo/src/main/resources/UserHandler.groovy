objectClass("User") {
    search {
        operation "GET"
        endpoint("/users/search") {
            objectExtractor {
                return response.body().get("data")
            }
            supportedFilter("id", EQUAL) {
                request {
                    uriBuilder.addParameter("id",value)
                }
            }
            supportedFilter("login", Contains) {
                request {
                    uriBuilder.addParameter("q", value)
                }
            }
        }
        endpoint("/users/") {
            supportedFilter("login", EQUAL) {

            }
        }
    }
}