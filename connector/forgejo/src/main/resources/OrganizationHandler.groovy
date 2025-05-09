objectClass("Organization") {
    search {
        endpoint("/users/search") {
            objectExtractor {
                return response.body.get("data")
            }
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                       .queryParameter("page", paging.pageOffset)
            }

            // IDEA: Filter support could be in separate file
            supportedFilter("id", EQUAL) {
                request { // IDEA: lambda may delegate also to RequestBuilder
                    request.queryParameter("id",value)
                }
            }
            supportedFilter("login", Contains) {
                request { // IDEA: lambda may delegate also to RequestBuilder
                    request.queryParameter("q", value)
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