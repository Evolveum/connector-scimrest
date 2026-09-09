# Development Mode Logging Implementation

## Overview
This implementation adds comprehensive development mode logging to the SCIM REST connector, providing structured, YAML-formatted logs of HTTP requests, responses, errors, and SCIM operations. The system only activates when `developmentMode = true` in the connector configuration.

## Features

### 1. HTTP Request/Response Logging
- Full request details: method, URI, headers (with masking support), and body
- Response details: status code, status message, headers, and body
- Automatic masking of Authorization headers
- Body truncation at 10KB limit
- Development mode gated - only logs when enabled

### 2. SCIM Operation Logging
- Schema discovery logging with schema ID and name
- Resource type discovery logging with endpoint information
- Error logging with full stack traces

### 3. Structured YAML Output
- All logs are output in YAML format for easy parsing
- Includes `operation` field in fully qualified format
- Timestamps in ISO 8601 format
- `[DEV]` prefix on all development mode logs

### 4. Type-Specific Builders
- `RequestLogEntry` for HTTP requests
- `ResponseLogEntry` for HTTP responses
- `ErrorLogEntry` for error tracking
- `ScimLogEntry` for SCIM operations

### 5. Operation Context
- Operation name in fully qualified format (e.g., `com.evolveum.polygon.scimrest.http.request`)
- `OperationScope` for temporarily changing operation name within code blocks
- Thread-local operation stack for nested scopes

## Usage Examples

### Basic HTTP Request Logging
```java
DevLogger logger = new DevLogger(RestContext.class, developmentMode, 
    "com.evolveum.polygon.scimrest.http.request");

// Log HTTP request
logger.logHttpRequest(requestSpecification);

// Log HTTP response
logger.logHttpResponse(response);
```

### SCIM Discovery Logging
```java
DevLogger logger = new DevLogger(ScimContext.class, developmentMode,
    "com.evolveum.polygon.scimrest.scim");

// Log schema discovery
logger.logSchemaDiscovery("urn:ietf:params:scim:schemas:core:2.0:User", "User");

// Log resource discovery
logger.logResourceDiscovery("User", "User", "/Users");
```

### Error Logging
```java
try {
    // Some operation
} catch (Exception e) {
    logger.logError("Operation Failed", "Error occurred during processing", e);
}
```

### Operation Scope
```java
DeLogger logger = new DevLogger(..., "com.example.operation");

try (OperationScope scope = logger.createScope("com.example.suboperation")) {
    // Logs in this block use "com.example.suboperation" as operation name
    logger.logHttpRequest(request, maskedHeaders);
}
// Operation name restored to "com.example.operation"
```

### Masked Headers
```java
// Add masked headers to RequestSpecification
requestBuilder.maskedHeader("X-API-Key", "secret-token");

// Headers will be logged as [masked] in development mode
```

## Sample Log Output

### HTTP Request
```yaml
---
operation: com.evolveum.polygon.scimrest.http.request
timestamp: '2025-01-15T10:30:15.123Z'
type: request
method: POST
uri: 'https://forgejo.example.com/api/v1/repos/search'
headers:
  Content-Type: application/json
  Authorization: '[masked]'
  User-Agent: Polygon-SCIM-REST-Connector/1.0
body: '{"q": "test-repo", "limit": 10}'
```

### HTTP Response
```yaml
---
operation: com.evolveum.polygon.scimrest.http.response
timestamp: '2025-01-15T10:30:15.245Z'
type: response
statusCode: 200
statusMessage: OK
headers:
  Content-Type: application/json; charset=utf-8
  X-Total-Count: '5'
body: |
  {
    "ok": true,
    "data": [{"id": "123", "name": "test-repo"}]
  }
```

### Error Log
```yaml
---
operation: com.evolveum.polygon.scimrest.http.request
timestamp: '2025-01-15T10:30:16.789Z'
type: error
category: HTTP Request
exceptionType: java.net.http.HttpConnectTimeoutException
message: Connection timeout after 30000 ms
stackTrace: |
  java.net.http.HttpConnectTimeoutException: Connection timeout
      at java.net.http.HttpClient.send(HttpClient.java:1234)
      at com.evolveum.polygon.scimrest.impl.rest.RestContext.executeRequest
      ...
```

### SCIM Schema Discovery
```yaml
---
operation: com.evolveum.polygon.scimrest.scim
timestamp: '2025-01-15T10:30:20.001Z'
type: scim_schema_discovery
schemaId: 'urn:ietf:params:scim:schemas:core:2.0:User'
schemaName: User
```

## Files Modified/Created

### New Files
- `DevLogger.java` - Master logging utility
- `AbstractDevLogEntry.java` - Base class for log entries
- `RequestLogEntry.java` - HTTP request log entry
- `ResponseLogEntry.java` - HTTP response log entry
- `ErrorLogEntry.java` - Error log entry
- `ScimLogEntry.java` - SCIM discovery log entry
- `DevHeaderMaskInfo.java` - Header masking marker
- `OperationScope.java` - Operation name scope management
- `DevLoggerTest.java` - Unit tests for DevLogger

### Modified Files
- `connector-scimrest/common/pom.xml` - Added Jackson YAML dependency
- `RestContext.java` - Integrated DevLogger for HTTP request/response logging
- `ConnectorContext.java` - Pass developmentMode to RestContext
- `ScimContext.java` - Integrated DevLogger for SCIM discovery logging

## Dependencies
- Jackson YAML (`jackson-dataformat-yaml`) 2.18.3
- Existing Jackson dependencies already present in project

## Configuration

Enable development mode in connector configuration:
```java
configuration.setDevelopmentMode(true);
```

## Design Decisions

1. **Jackson YAML Used**: Chosen over manual YAML building for clean, maintainable code consistent with existing project patterns.

2. **Operation Names**: Fully qualified format for easy identification and mapping to OperationResult.

3. **Automatic Authorization Masking**: All Authorization headers automatically masked for security.

4. **10KB Body Limit**: Hardcoded limit to prevent log flooding with large responses.

5. **ThreadLocal Operation Stack**: Supports nested operation scopes with automatic restoration.

6. **[DEV] Prefix**: Clear visual indicator in logs that entry is from development mode.

7. **[masked] Placeholder**: Standard format for sensitive data suppression.

## Future Enhancements (Not Implemented)

- Duration/performance tracking for operations
- Automatic detection of sensitive headers (beyond Authorization)
- Configurable masking rules
- Operation-to-OperationResult mapping utilities
- Async operation tracking
- Log correlation IDs

## Testing

All unit tests pass successfully:
```bash
cd connector-scimrest
mvn test -Dtest=DevLoggerTest
```

Tests cover:
- Request log entry construction
- Response log entry construction
- Error log entry with stack trace
- SCIM schema discovery logging
- SCIM resource discovery logging
- Operation scope management
- YAML serialization
- Body truncation

## Integration

The logging integrates seamlessly with the existing connector architecture:

- **HTTP Layer**: Logs requests/responses in `RestContext.executeRequest()`
- **SCIM Layer**: Logs schema and resource discovery in `ScimContext.initialize()`
- **Configuration**: Controlled by `developmentMode` flag in `BaseGroovyConnectorConfiguration`

The structured YAML logs can be parsed by upper layers (GUI, monitoring tools) and potentially converted to `OperationResult` format for rich display and error reporting.
