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
        endpoint("/users/{username}") {
            singleResult()
            supportedFilter(attribute("login").eq().anySingleValue()) {
                request.pathParameter("username", value)
            }
        }

        endpoint("teams/{id}/members") {
            responseFormat JSON_ARRAY
            supportedFilter(attribute("teams").eq().anySingleValue()) {
                request.pathParameter("id", value.uid)
            }
            pagingSupport {
                request.queryParameter("limit", paging.pageSize)
                        .queryParameter("page", paging.pageOffset)
            }
        }

        attributeResolver {
            attribute "organization"
            resolutionType PER_OBJECT
            implementation {
                var orgFilter = objectClass("Organization").attributeFilter("member").equals(value)
                var orgs = objectClass("Organization").search(orgFilter)
                value.addAttribute("organization", orgs)
            }
        }
    }
}