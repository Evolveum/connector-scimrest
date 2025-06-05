
# SCIM & REST Combined implementation

## Initialization

1. Authorize client
2. Discover features
  - Get `/ServiceProviderConfig` - to discover features support using
    - authentication support
    - paging
    - filter
  - Get `/ResourceTypes` - to discover API endpoints & root types
  - Get `/Schemas` - to discover schema for root resources
3. Construct Base **SCIM Context** (not yet mapped to ConnID)
4. Load mappings / Groovy scripts
5. Build schema & operation handlers based on `SCIM Context` and *Groovy Scripts*

## Detecting object references from schema

The one side of object reference can be detected from schema if the following contidtions are true for attribute:

 - Attribute type is `reference`
 - Attribute has `referenceTypes` specified and values are resource types (eg. `User`, `Group`), not `uri` or `external`

**IMPORTANT:** Some servers (eg. `schema.dev`) may sent even core schemas missing `referenceTypes` property.


The problematic part is that sometimes the actual reference (from ConnID perspective) is the whole parent structured container.

```json5
{
  "name": "members",
  "type": "complex",
  "multiValued": true,
  "description": "A list of members of the Group.",
  "required": false,
  "subAttributes": [
    {
      "name": "value",
      "type": "string",
      "multiValued": false,
      "description": "Identifier of the member of this Group.",
      "...": "..." // Shortened for clarity
    },
    {
      "name": "$ref",
      "type": "reference",
      "referenceTypes": [
        "User",
        "Group"
      ],
      "multiValued": false,
      "description": "The URI corresponding to a SCIM resource that is a member of this Group.",
      "...": "...",
    },
    {
      "name": "type",
      "type": "string",
      "multiValued": false,
      "description": "A label indicating the type of resource, e.g., 'User' or 'Group'.",
      "canonicalValues": [
        "User",
        "Group"
      ],
      "...": "...",
    }
  ],
  "mutability": "readWrite",
  "returned": "default"
}
```

This example is more of the convention and it is not required by the specification for custom resource types or extensions.