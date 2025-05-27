objectClass("Organization") {
    search {
        endpoint("orgs") {
            responseFormat JSON_ARRAY
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                       .queryParameter("page", paging.pageOffset)
            }
        }

        endpoint("orgs/{org}") {
            singleResult()
            supportedFilter(attribute("name").eq().anySingleValue()) {
                request.pathParameter("org", value)
            }
        }
    }
}