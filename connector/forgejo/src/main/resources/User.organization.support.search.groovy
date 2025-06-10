import org.identityconnectors.framework.common.objects.ConnectorObjectReference
/*
objectClass("User") {
    search {
        attributeResolver {
            attribute "organization"
            resolutionType PER_OBJECT
            implementation {
                var orgFilter = objectClass("Organization").attributeFilter("member").eq(value)
                var orgs = objectClass("Organization").search(orgFilter)
                value.addAttribute("organization", orgs.collect { i -> new ConnectorObjectReference(i)})
            }
        }
    }
}
*/
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