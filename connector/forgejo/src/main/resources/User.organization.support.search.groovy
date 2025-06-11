objectClass("Organization") {
    search {
        endpoint("users/{username}/orgs") {
            responseFormat JSON_ARRAY
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                        .queryParameter("page", paging.pageOffset)
            }
            supportedFilter(attribute("member").eq().anySingleValue()) {
                request.pathParameter("username", value.value.name)
            }
        }
    }
}

objectClass("User") {
    search {
        endpoint("orgs/{org}/members") {
            responseFormat JSON_ARRAY
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                        .queryParameter("page", paging.pageOffset)
            }
            supportedFilter(attribute("organization").eq().anySingleValue()) {
                request.pathParameter("org", value.value.uid)
            }
        }
    }
}