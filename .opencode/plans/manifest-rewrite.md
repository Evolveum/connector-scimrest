# Manifest-Based Connector Rewrite Plan

## Overview
Rewrite Java connector classes for `open-project` and `sample-scimdev` to use the manifest-based form (extending `ManifestBasedConnector`).

## Already Manifest-Based (Reference Examples)
- `forgejo` - already extends `ManifestBasedConnector`
- `sample-scimdev-noclass` - manifest-only, no Java class

## 1. open-project

### Files to create/modify:

#### Create: `connector/open-project/src/main/resources/connector.manifest.json`

```json
{
    "application": {
        "name": "OpenProject",
        "description": "OpenProject is a web based project and issue tracking tool."
    },
    "connector": {
        "schema": [
            { "script": "/User.native.schema.groovy" },
            { "script": "/User.connid.schema.groovy" },
            { "script": "/Group.native.schema.groovy" },
            { "script": "/Group.connid.schema.groovy" },
            { "script": "/Project.native.schema.groovy" },
            { "script": "/Project.connid.schema.groovy" },
            { "script": "/Role.native.schema.groovy" },
            { "script": "/Role.connid.schema.groovy" },
            { "script": "/Formattable.native.schema.groovy" },
            { "script": "/Principal.native.schema.groovy" },
            { "script": "/Membership.native.schema.groovy" },
            { "script": "/Membership.connid.schema.groovy" },
            { "script": "/associations.schema.groovy" }
        ],
        "authorization": [],
        "operation": [
            { "script": "/User.search.groovy" },
            { "script": "/Group.search.groovy" },
            { "script": "/Project.search.groovy" },
            { "script": "/Role.search.groovy" },
            { "script": "/Membership.search.groovy" },
            { "script": "/User.op.groovy" },
            { "script": "/User.create.op.groovy" },
            { "script": "/User.update.op.groovy" }
        ]
    }
}
```

#### Modify: `connector/open-project/src/main/java/com/evolveum/polygon/openProject/OpenProjectConnector.java`

**Before (current):**
```java
@ConnectorClass(displayNameKey = "openProject.rest.display", configurationClass = OpenProjectConfiguration.class,  messageCatalogPaths = "Messages")
public class OpenProjectConnector extends AbstractGroovyRestConnector<OpenProjectConfiguration>
        implements PoolableConnector {

    public OpenProjectConnector() {
        super(false);
    }

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) { ... }

    @Override
    protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) { ... }

    @Override
    protected AuthorizationCustomizer<RestClientConfiguration> authorizationCustomizer() { ... }

    @Override
    public void checkAlive() throws ConnectionBrokenException {}
}
```

**After (target):**
```java
@ConnectorClass(displayNameKey = "openProject.rest.display", configurationClass = OpenProjectConfiguration.class, messageCatalogPaths = "Messages")
public class OpenProjectConnector extends ManifestBasedConnector
        implements PoolableConnector {

    @Override
    public void checkAlive() throws ConnectionBrokenException {
    }
}
```

**Changes:**
- Change base class from `AbstractGroovyRestConnector<OpenProjectConfiguration>` to `ManifestBasedConnector`
- Remove `initializeSchema()` method (handled by manifest)
- Remove `initializeAuthorizationHandler()` method (handled by manifest)
- Remove `initializeObjectClassHandler()` method (handled by manifest)
- Remove `authorizationCustomizer()` method (not needed, auth auto-detected from config)
- Keep `checkAlive()` method (required for PoolableConnector)
- Remove constructor (no longer needed)
- Remove imports: `GuardedStringAccessor`, `GroovyRestHandlerBuilder`, `GroovySchemaLoader`, `AuthorizationCustomizer`, `RestClientConfiguration`, `ConnectionBrokenException`, `PoolableConnector` stays

**Note:** `OpenProjectConfiguration.java` remains unchanged - it implements `RestClientConfiguration.BasicAuthorization` which the framework auto-detects.

### 2. sample-scimdev

#### Create: `connector/sample-scimdev/src/main/resources/connector.manifest.json`

```json
{
    "application": {
        "name": "SCIM.dev",
        "description": "SCIM Playground is a SCIM test server and playground environment designed to help you play, learn, and test SCIM with ease."
    },
    "connector": {
        "schema": [
            { "script": "/ScimDev.schema.groovy" }
        ],
        "authorization": [],
        "operation": []
    }
}
```

#### Modify: `connector/sample-scimdev/src/main/java/com/evolveum/polygon/sample/scimdev/ScimDevConnector.java`

**Before (current):**
```java
@ConnectorClass(displayNameKey = "scimdev.rest.display", configurationClass = ScimDevConfiguration.class)
public class ScimDevConnector extends AbstractGroovyRestConnector<ScimDevConfiguration> {

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        loader.loadFromResource("/ScimDev.schema.groovy");
    }

    @Override
    protected void initializeAuthorizationHandler(GroovyRestHandlerBuilder builder) {}

    @Override
    protected void initializeObjectClassHandler(GroovyRestHandlerBuilder builder) {}
}
```

**After (target):**
```java
@ConnectorClass(displayNameKey = "scimdev.rest.display", configurationClass = ScimDevConfiguration.class)
public class ScimDevConnector extends ManifestBasedConnector {
}
```

**Changes:**
- Change base class from `AbstractGroovyRestConnector<ScimDevConfiguration>` to `ManifestBasedConnector`
- Remove all three `initialize*()` methods (handled by manifest)
- Remove imports: `GroovyRestHandlerBuilder`, `GroovySchemaLoader`

### 3. sample-scimdev-noclass (reference - already done)

This connector has no Java class and only `connector.manifest.json`. It serves as the reference for the manifest-only form.

## Summary of Changes

| Connector | Manifest Created | Java Class Modified |
|-----------|-----------------|---------------------|
| open-project | Yes (new) | Yes - extends ManifestBasedConnector |
| sample-scimdev | Yes (new) | Yes - extends ManifestBasedConnector |

## Files Not Changed
- `open-project/OpenProjectConfiguration.java` - stays as-is
- `sample-scimdev/ScimDevConfiguration.java` - stays as-is
- `generic/` - no Java connector class, N/A
- `forgejo/` - already manifest-based
