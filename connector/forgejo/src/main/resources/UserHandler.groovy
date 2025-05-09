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

            supportedFilter(attribute("id").eq().anySingleValue()) {
                // API weirdness of Forgejo - attribute is called id, but query parameter is called uid
                request.queryParameter("uid",value)
            }

            supportedFilter(attribute("login").contains().anySingleValue()) {
                request.queryParameter("q", value)
            }
        }

        // This is actually get user by username
        endpoint("/users/") {
            supportedFilter(attribute("login").eq().anySingleValue()) {
                request.subpath(value)
            }
        }
    }
}