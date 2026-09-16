objectClass("workpackage") {
    search {
        endpoint("work_packages") {
            emptyFilterSupported true
            objectExtractor {
                return response.body().get("_embedded").get("elements")
            }
            pagingSupport {
                request.queryParameter("pageSize", paging.pageSize)
                       .queryParameter("offset", paging.pageOffset)
            }
        }
    }
}