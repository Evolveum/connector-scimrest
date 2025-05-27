objectClass("Team") {
    search {
        endpoint("orgs/{org}/teams") {
            responseFormat JSON_ARRAY
            pagingSupport { // IDEA: lambda may delegate also to RequestBuilder
                request.queryParameter("limit", paging.pageSize)
                       .queryParameter("page", paging.pageOffset)
            }
            supportedFilter(attribute("organization.name").eq().anySingleValue()) {
                request.pathParameter("org", value);
            }
        }

        endpoint("teams/{id}") {
            singleResult()
            supportedFilter(attribute("id").eq().anySingleValue()) {
                request.pathParameter("id", value);
            }
        }

        /**
        attributeResolver {
            attribute "members"
            type PER_OBJECT

            implementation {
                // Implemented via search for all team members
                return objectClass("User").search(attributeFilter("teams").eq(object));
            }
        }*/
    }
}