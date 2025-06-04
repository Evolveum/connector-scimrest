
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
