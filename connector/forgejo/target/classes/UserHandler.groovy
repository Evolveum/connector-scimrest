objectClass("User") {
    search {
        operation "GET"
        endpoint("/users/search") {
            objectExtractor {
                return response.body().get("data")
            }
            pagingSupport {
                request.query("limit", paging.pageSize)
                       .query("page", paging.pageOffset)
            }
            supportedFilter("id", EQUAL) {
                request {
                    uriBuilder.query("id",value)
                }
            }
            supportedFilter("login", Contains) {
                request {
                    uriBuilder.query("q", value)
                }
            }
        }
        endpoint("/users/") {
            supportedFilter("login", EQUAL) {
                request.subpath(value)
            }
        }
    }
}