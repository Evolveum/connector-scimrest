If implementation of `search` for specific `object class` and  empty filter is not viable by using any available endpoints, you can implement it manually.
You should analyze already supported endpoints and other endpoints:

  - if it is possible to obtain list of related objects
  - using that list of related objects to perform multiple searches for required object.

If it is possible to indirectly (via multiple searches) get list of all objects you can implement it as
`YourObjectClass.custom.search.groovy`.


## Example

The REST interface does not support listing all organization units, which we represented by
`OrganizationUnit` class.

The available endpoints are:
* `organization/` - Returns a list of all organizations
* `organization/{id}` - Get organization by ID
    * parameter `id`
* `organization/{org}/unit` - List all units inside an concrete organization
* `unit/{id]` - Gets Organization Unit by ID

Given that we already implemented:
* support for organization as object class `Organization`
    * searching all using `organization/` endpoint, this endpoint does not return list of units
* support for organization unit as object class `OrganizationUnit`
    * support for searching units by parent organization as attribute`organizationId` - by using endpoint `organization/{id}/unit`

We can implement manual search as following:

```groovy
objectClass("OrganizationUnit") {
    search {
        custom {
            // This search supports empty filter
            emptyFilterSupported true

            implementation {
                // Step 1: Fetch all organizations
                def orgs = objectClass("Organization").searchAll()

                // Step 2: Iterate over each organization to find units
                for (def org : orgs) {
                    def id = org.get("id")

                    // Create a filter to search for units with the same organization ID

                    def filter = filter("organizationId").eq(id)
                    
                    // Performs search using units for specific organization
                    // and pass results to resultHandler
                    objectClass("OrganizationUnit").search(filter, resultHandler)
                }
            }
        }
    }
}
```

## Detailed API Documentation

### Search `custom` handler

Custom handler is block inside of `objectClass` `search` handler, which allows us to implement custom search script for the cases, when search requires coordination between multiple HTTP requests or object searches.
Note that `custom` handler block should be avoided if required data could be obtained by single simple HTTP call.

`custom` block has following properties:

* `emptyFilterSupported` - `boolean`
   * `true` - custom search implementation handles empty filters, practically meaning it returns all objects of the object class.
   * `false` - custom search implementation handles specific filters and contains filtering logic for these filters.
* `implementation` - code block containing implementation logic in Groovy, which is responsible for implementing the functionality.
  * Available properties & methods:
    * `requestFilter` - a filter, which should be interpreted by this custom
    * `resultHandler` - result handler which should be used to report final found objects of the object class this search handler is implemented for.
    * `objectClass(className)` - returns an `ObjectClassHandler` for specified `className`, which allows us to perform searches for objects of that particular object class.
    * `filter(attributeName)` - returns a fluent builder for filters for current object class this implementation is tied to.

### `ObjectClassHandler`

Provides an access to existing implementation of the Object Class support.

Available methods are:

* `searchAll()` - Searches for all objects of this class without any specific filter.
   * returns a collection of objects. 

* `search(filter)` - Searches for objects of this class which matches specified filter
   * returns a collection of objects which matches the filter.

* `search(filter, resultHandler)` - Searches for objects of this class and reports the results to the specified result handler.



