objectClass("Team") {
    search {
        endpoint("orgs/{org}/teams") {
            pathParameter("org", relation("organization-to-team").attribute("id"))
            objectExtractor {
                return response.body.get("data")
            }
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                       .queryParameter("page", paging.pageOffset)
            }
        }
    }
}