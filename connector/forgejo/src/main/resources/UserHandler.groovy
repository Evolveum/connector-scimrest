objectClass("User") {
    search {
        endpoint("/users/search") {
            objectExtractor {
                return response.body().get("data")
            }
            pagingSupport {
                request.queryParameter("limit", paging.pageSize)
                       .queryParameter("page", paging.pageOffset)
            }
            emptyFilterSupported true
            /*
            supportedFilter("id", EQUAL) {
                request {
                    request.queryParameter("id",value)
                }
            }
            supportedFilter("login", Contains) {
                request {
                    request.queryParameter("q", value)
                }
            }*/
        }
        /*
        endpoint("/users/") {
            supportedFilter("login", EQUAL) {
                request.subpath(value)
            }
        }*/
    }
}