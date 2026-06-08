# Authentication Groovy Script Guide

This document explains when a Groovy script is needed to configure authentication, describes
the scripting interface in full, and provides examples for common scenarios.

---

## 1. When is a Groovy script needed?

The connector ships with built-in implementations for six authentication methods. These
implementations follow the relevant RFC standards precisely. Configuring them requires only
setting the appropriate properties — no script is needed.

| Method | Config interface | Key config properties |
|---|---|---|
| HTTP Basic (RFC 7617) | `BasicAuthorization` | `restUsername`, `restPassword` |
| Bearer Token (RFC 6750) | `BearerTokenAuthorization` | `restTokenValue` |
| JWT Bearer (RFC 7519/7515) | `JwtBearerAuthorization` | `restJwtAlgorithm`, `restJwtSecret`, `restJwtTokenName`, `restJwtPayload`, `restJwtLocalization` |
| API Key | `ApiKeyAuthorization` | `restApiKey`, `restApiKeyName`, `restApiKeyLocation` |
| OAuth2 Client Credentials (RFC 6749 §4.4) | `OAuth2ClientCredentialsAuthorization` | `restOAuth2TokenUrl`, `restOAuth2ClientId`, `restOAuth2ClientSecret` |
| OAuth2 JWT Bearer (RFC 7523) | `OAuth2JwtBearerAuthorization` | `restOAuth2TokenUrl`, `restOAuth2ClientId`, `restOAuth2PrivateKey` |

The same six methods are built-in for SCIM as well (`ScimClientConfiguration` variants with
`scim`-prefixed property names).

### When the API deviates from the standard

Real-world APIs often have specifics that do not match what the RFC mandates. A Groovy script
lets you override the built-in behavior for that particular API. Common reasons to write a
script include:

- **Non-standard header prefix** — Forgejo and Gitea accept `token <value>` instead of the
  RFC 6750 `Bearer <value>` prefix.
- **Non-standard token field** — the token endpoint returns `token` or a nested `data.token`
  instead of the standard `access_token` field.
- **Custom token application** — the API expects the token in a proprietary header such as
  `X-Auth-Token` or `X-Api-Key` rather than `Authorization`.
- **Refresh token flow** — the built-in OAuth2 handler does not use refresh tokens; scripts
  can add this by overriding the token request and validation logic.
- **Extra parameters or headers** — the token endpoint requires additional form fields
  (e.g. `tenant`, `audience`, `custom_param`) beyond the standard OAuth2 parameters.
- **Context-dependent headers** — the API requires a tenant ID or other value extracted from
  the token response to be sent on every subsequent API request.
- **Multi-method selection** — the target system accepts more than one auth scheme and the
  connector should probe and pick the active one automatically.

---

## 2. Script basics

### 2.1 File placement

Place the script in the connector's resource directory:

```
src/main/resources/authorization.op.groovy
```

The connector infrastructure loads it automatically via `builder.loadFromResource()`.
For testing, a script string can be passed directly to `builder.loadFromString(script)`.

### 2.2 Top-level structure

```groovy
authentication {
    rest {
        // REST authentication configuration
    }
    scim {
        // SCIM authentication configuration (if the connector also uses SCIM)
    }
}
```

Both `rest { }` and `scim { }` blocks are optional. The `authentication { }` closure is the
script entry point and is resolved against `AuthenticationCustomizationBuilder`.

---

## 3. DSL reference

### 3.1 Method shortcut blocks

Inside `rest { }` and `scim { }`, use shortcut methods corresponding to the auth interface
implemented by your connector configuration:

| Shortcut | Applies to config interface |
|---|---|
| `basic { }` | `BasicAuthorization` |
| `bearer { }` | `BearerTokenAuthorization` |
| `jwtBearer { }` | `JwtBearerAuthorization` |
| `apiKey { }` | `ApiKeyAuthorization` |
| `oauth2ClientCredentials { }` | `OAuth2ClientCredentialsAuthorization` |
| `oauth2JwtBearer { }` | `OAuth2JwtBearerAuthorization` |

### 3.2 The `implementation { }` block

Inside any shortcut block, an `implementation { }` closure provides a complete custom
implementation. The closure delegate is [`AuthImplementationContext`](#4-authimplementationcontext).

```groovy
bearer {
    implementation {
        // 'request', 'configuration', 'ctx', 'decrypt', etc. are available here
        request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
    }
}
```

When `implementation { }` is set, it **fully replaces** the built-in handler. If the connector
configuration implements `BearerTokenAuthorization` and the script provides a `bearer {
implementation { } }` block, the Groovy closure runs instead of the built-in
`Authorization: Bearer <value>` logic.

### 3.3 Multi-method preference selection

When the target API supports more than one auth method, use `preference` to let the connector
automatically probe and select the active method at runtime:

```groovy
rest {
    preference apiKey, bearer
}
```

The connector sends a request to the configured test endpoint with each method in order and
caches the first one that returns a 2xx–3xx response. The probe runs once and the result is
cached for the connector lifetime.

The identifiers (`bearer`, `oauth2ClientCredentials`, etc.) are convenience properties defined
on `RestBuilder` / `ScimBuilder` that return the corresponding configuration interface class.

---

## 4. AuthImplementationContext

Every `implementation { }` closure is called with an `AuthImplementationContext` as its delegate.
All properties and methods are directly available without a prefix:

| Name | Type | Description |
|---|---|---|
| `configuration` | connector config object | Raw connector configuration; access typed fields with Groovy property syntax, e.g. `configuration.restUsername` |
| `request` | `HttpRequestSpecification` | The outgoing request to mutate — add headers, query params, form params |
| `ctx` | `Map<String, Object>` | Persistent state map; survives across calls for the lifetime of the connector instance (shared across all invocations) |
| `decrypt(GuardedString)` | `String` | Decrypts a `GuardedString` configuration field to plain text |
| `newRequest(String url)` | `HttpRequestSpecification` | Creates a new outgoing HTTP request (e.g. to a token endpoint) |
| `newJwt()` | `JwtAssertionBuilder` | Creates a new JWT builder supporting HS\*, RS\*, PS\*, ES\* algorithms |
| `execute(HttpRequestSpecification)` | `HttpResponse<String>` | Sends an HTTP request and returns the raw response |
| `parseJson(String)` | dynamic Groovy object | Parses a JSON string; use Groovy property access on the result |

### HttpRequestSpecification helpers

```groovy
request.header("X-Custom", "value")          // add/replace a request header
request.queryParameter("api_key", "value")   // add a query parameter
request.formParam("grant_type", "password")  // add a form body parameter (POST)
```

---

## 5. OAuth2 hooks

The `oauth2ClientCredentials { }` and `oauth2JwtBearer { }` blocks accept optional hook closures
that selectively override parts of the built-in OAuth2 token lifecycle instead of replacing it
entirely. The closure is called with `oauth2Context` as its argument and also as its delegate.

```groovy
oauth2ClientCredentials { oauth2Context ->
    validateToken       { /* ... */ }
    buildTokenRequest   { request -> /* ... */ }
    parseTokenResponse  { response -> /* ... */ }
    applyToken          { request -> /* ... */ }
}
```

Any hook not provided falls back to the built-in behaviour. All four hooks share the same
`oauth2Context` (`AuthContext`) map:

| Key | Description |
|---|---|
| `access_token` | The current access token string |
| `expires_in` | Token lifetime in seconds (from the token response) |
| `expires_at` | `java.time.Instant` expiry timestamp (set automatically from `expires_in`) |

You can store additional keys freely (e.g. `refresh_token`, `tenant_id`).

### Hook signatures

**`validateToken { -> boolean }`**
Called before each request. Return `true` to reuse the cached token; return `false` to fetch a
new one. Default: returns `false` when `access_token` is absent; returns `true` when
`expires_at` is absent (no expiry) or is still in the future.

**`buildTokenRequest { request -> }`**
Called to assemble the token endpoint request. `request` is an `HttpRequestSpecification`.
Default: sends the grant-type-specific form parameters (`grant_type`, `client_id`, etc.).
When overridden, the hook **replaces** the default form parameters entirely — if you need
client credentials, add them explicitly.

**`parseTokenResponse { response -> }`**
Called with the parsed JSON response from the token endpoint (Groovy dynamic object). **Must**
set `oauth2Context["access_token"]`; the framework throws if this key is missing after the hook
returns. `expires_at` is always derived from `expires_in` in the raw response automatically —
the hook only needs to extract the token value itself.

**`applyToken { request -> }`**
Called to attach the token to every outgoing API request. `request` is the `HttpRequestSpecification`.
Default: adds `Authorization: Bearer <access_token>`.

### Using `implementation { }` inside an OAuth2 block

If `implementation { }` is provided inside `oauth2ClientCredentials { }`, it **bypasses all
OAuth2 token management** entirely and is treated like a plain `implementation` block on
`bearer{}` or `apiKey{}`. Use this only when the entire token acquisition must be custom:

```groovy
oauth2ClientCredentials { oauth2Context ->
    implementation {
        // full override — no built-in OAuth2 logic runs
        request.header("Authorization", "MyCustomScheme " + ctx["precomputed_token"])
    }
}
```

---

## 6. Scenarios

### 6.1 No Groovy — Bearer Token (built-in, standard)

Configure only; no script needed:

```properties
restTokenValue = eyJhbGciOiJSUzI1NiJ9...
```

The connector sends `Authorization: Bearer <value>` on every request per RFC 6750.

---

### 6.2 No Groovy — API Key (built-in, standard)

```properties
restApiKey         = sk-live-abc123
restApiKeyName     = X-API-Key
restApiKeyLocation = header
```

To place the key in a query parameter instead:

```properties
restApiKeyLocation = query
restApiKeyName     = api_key
```

---

### 6.3 No Groovy — OAuth2 Client Credentials (built-in, standard)

```properties
restOAuth2TokenUrl     = https://auth.example.com/oauth/token
restOAuth2ClientId     = my-client
restOAuth2ClientSecret = <secret>
restOAuth2Scope        = read write
```

Tokens are fetched automatically, cached, and refreshed on expiry.

---

### 6.4 Full built-in implementations as Groovy reference

The following script reproduces exactly what each built-in handler does internally. Use it as
a starting point when you need to customize only a small part of the standard behavior.

```groovy
authentication {
    rest {

        // HTTP Basic (RFC 7617): encodes credentials as Base64(username:password)
        basic {
            implementation {
                def credentials = configuration.restUsername + ":" + decrypt(configuration.restPassword)
                request.header("Authorization", "Basic " + credentials.bytes.encodeBase64())
            }
        }

        // Bearer Token (RFC 6750): sends the static token verbatim
        bearer {
            implementation {
                request.header("Authorization", "Bearer " + decrypt(configuration.restTokenValue))
            }
        }

        // JWT Bearer (RFC 7519): builds a signed JWT, places it per restJwtLocalization
        jwtBearer {
            implementation {
                def token = newJwt()
                    .claimsFromJson(configuration.restJwtPayload)
                    .sign(configuration.restJwtAlgorithm,
                          decrypt(configuration.restJwtSecret),
                          configuration.restJwtSecretBase64Encoded)
                if ("query".equalsIgnoreCase(configuration.restJwtLocalization)) {
                    request.queryParameter(configuration.restJwtTokenName, token)
                } else {
                    request.header("Authorization", configuration.restJwtTokenName + " " + token)
                }
            }
        }

        // API Key: sends the key in the configured header or query parameter
        apiKey {
            implementation {
                if ("query".equalsIgnoreCase(configuration.restApiKeyLocation)) {
                    request.queryParameter(configuration.restApiKeyName, decrypt(configuration.restApiKey))
                } else {
                    request.header(configuration.restApiKeyName, decrypt(configuration.restApiKey))
                }
            }
        }

        // OAuth2 Client Credentials (RFC 6749 §4.4):
        // fetches a token with client_credentials grant, caches it until expiry
        oauth2ClientCredentials { oauth2Context ->
            validateToken {
                def accessToken = oauth2Context["access_token"]
                if (!accessToken) return false
                def expiresAt = oauth2Context["expires_at"]
                expiresAt == null || expiresAt.isAfter(java.time.Instant.now())
            }
            buildTokenRequest { request ->
                if ("basic".equalsIgnoreCase(configuration.restOAuth2ClientAuthenticationScheme)) {
                    def creds = configuration.restOAuth2ClientId + ":" +
                                decrypt(configuration.restOAuth2ClientSecret)
                    request.header("Authorization", "Basic " + creds.bytes.encodeBase64())
                } else {
                    request.formParam("client_id",     configuration.restOAuth2ClientId)
                    request.formParam("client_secret", decrypt(configuration.restOAuth2ClientSecret))
                }
                request.formParam("grant_type", "client_credentials")
                if (configuration.restOAuth2Scope) {
                    request.formParam("scope", configuration.restOAuth2Scope)
                }
            }
            parseTokenResponse { response ->
                oauth2Context["access_token"] = response.access_token
                // expires_at is derived from expires_in automatically by the framework
            }
            applyToken { request ->
                request.header("Authorization", "Bearer " + oauth2Context["access_token"])
            }
        }

        // OAuth2 JWT Bearer (RFC 7523):
        // signs a JWT assertion with RS256 and exchanges it for a token
        // Note: the built-in always uses RS256. Use buildTokenRequest to change the algorithm.
        oauth2JwtBearer { oauth2Context ->
            buildTokenRequest { request ->
                def now = java.time.Instant.now().epochSecond
                def assertion = newJwt()
                    .claim("iss", configuration.restOAuth2ClientId)
                    .claim("sub", configuration.restOAuth2ClientId)
                    .claim("aud", configuration.restOAuth2TokenUrl)
                    .claim("iat", now)
                    .claim("exp", now + 300)
                    .claim("jti", java.util.UUID.randomUUID().toString())
                    .sign("RS256", configuration.restOAuth2PrivateKey)
                request.formParam("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                request.formParam("client_id",  configuration.restOAuth2ClientId)
                request.formParam("assertion",  assertion)
            }
            parseTokenResponse { response ->
                oauth2Context["access_token"] = response.access_token
            }
            applyToken { request ->
                request.header("Authorization", "Bearer " + oauth2Context["access_token"])
            }
        }
    }
}
```

---

### 6.5 Non-standard header prefix (Forgejo / Gitea)

Forgejo and Gitea require `token <value>` as the Authorization value instead of the RFC 6750
`Bearer <value>`. Both `bearer` and `apiKey` blocks are overridden because either interface
may be configured:

```groovy
// authorization.op.groovy
authentication {
    rest {
        bearer {
            implementation {
                request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
            }
        }
        apiKey {
            implementation {
                request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
            }
        }
    }
}
```

---

### 6.6 Custom JWT claims

When the built-in JWT Bearer handler does not cover all required claims (e.g. the API mandates
specific `iss`, `sub`, or `aud` values not expressible via `restJwtPayload`):

```groovy
authentication {
    rest {
        jwtBearer {
            implementation {
                def jwt = newJwt()
                    .claim("iss", configuration.restOAuth2ClientId)
                    .claim("sub", configuration.restUsername)
                    .claim("aud", "https://api.example.com")
                    .claim("iat", java.time.Instant.now().epochSecond)
                    .claim("exp", java.time.Instant.now().plusSeconds(300).epochSecond)
                    .sign(configuration.restJwtAlgorithm, configuration.restJwtSecret)
                request.header("Authorization", "Bearer " + jwt)
            }
        }
    }
}
```

---

### 6.7 OAuth2 hooks — refresh token flow

The built-in OAuth2 client credentials handler does not support refresh tokens. Hooks can add
that capability by overriding all four stages:

```groovy
authentication {
    rest {
        oauth2ClientCredentials { oauth2Context ->
            validateToken {
                def expiresAt = oauth2Context["expires_at"]
                expiresAt != null && expiresAt.isAfter(java.time.Instant.now())
            }

            buildTokenRequest { request ->
                if (oauth2Context["refresh_token"]) {
                    request.formParam("grant_type",    "refresh_token")
                    request.formParam("refresh_token", oauth2Context["refresh_token"])
                } else {
                    request.formParam("grant_type",    "client_credentials")
                    request.formParam("client_id",     configuration.restOAuth2ClientId)
                    request.formParam("client_secret", decrypt(configuration.restOAuth2ClientSecret))
                }
            }

            parseTokenResponse { response ->
                oauth2Context["access_token"] = response.access_token
                if (response.refresh_token) {
                    oauth2Context["refresh_token"] = response.refresh_token
                }
            }

            applyToken { request ->
                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
            }
        }
    }
}
```

---

### 6.8 OAuth2 hooks — non-standard token field

When the token endpoint returns a non-standard field name (e.g. `token` instead of
`access_token`), override only `parseTokenResponse` — all other hooks use the built-in:

```groovy
authentication {
    rest {
        oauth2ClientCredentials { oauth2Context ->
            parseTokenResponse { response ->
                oauth2Context["access_token"] = response.token
            }
        }
    }
}
```

---

### 6.9 OAuth2 hooks — nested JSON response

When the token is nested inside the response body (e.g. `data.token`):

```groovy
authentication {
    rest {
        oauth2ClientCredentials { oauth2Context ->
            parseTokenResponse { response ->
                oauth2Context["access_token"] = response.data?.token
            }
            applyToken { request ->
                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
            }
        }
    }
}
```

---

### 6.10 OAuth2 hooks — custom token application header

When the API expects the token in a proprietary header instead of `Authorization`:

```groovy
authentication {
    rest {
        oauth2ClientCredentials { oauth2Context ->
            applyToken { request ->
                request.header("X-Auth-Token", oauth2Context["access_token"])
            }
        }
    }
}
```

---

### 6.11 OAuth2 hooks — context value passed between hooks

A value extracted during `parseTokenResponse` (e.g. a tenant ID returned alongside the token)
can be stored in `oauth2Context` and used in `applyToken`:

```groovy
authentication {
    rest {
        oauth2ClientCredentials { oauth2Context ->
            parseTokenResponse { response ->
                oauth2Context["access_token"] = response.access_token
                oauth2Context["tenant_id"]    = response.tenant_id
            }

            applyToken { request ->
                request.header("Authorization", "Bearer ${oauth2Context['access_token']}")
                request.header("X-Tenant-Id",  oauth2Context["tenant_id"])
            }
        }
    }
}
```

---

### 6.12 Caching state in `ctx`

The `ctx` map persists for the connector instance lifetime. Use it to avoid re-computing or
re-fetching values on every request:

```groovy
authentication {
    rest {
        apiKey {
            implementation {
                if (!ctx["token"]) {
                    ctx["token"] = decrypt(configuration.restApiKey)
                }
                request.header("X-API-Key", ctx["token"])
            }
        }
    }
}
```

---

### 6.13 Customizing REST and SCIM independently

When the connector communicates with both a REST API and a SCIM endpoint using different schemes:

```groovy
authentication {
    rest {
        bearer {
            implementation {
                request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
            }
        }
    }
    scim {
        oauth2ClientCredentials { oauth2Context ->
            applyToken { request ->
                request.header("X-SCIM-Token", oauth2Context["access_token"])
            }
        }
    }
}
```

---

### 6.14 Multiple method blocks in one script

Both `bearer` and `apiKey` blocks can coexist in the same script. Only the block matching the
configuration interface actually implemented by the connector will execute:

```groovy
authentication {
    rest {
        bearer {
            implementation {
                request.header("Authorization", "token " + decrypt(configuration.restTokenValue))
            }
        }
        apiKey {
            implementation {
                request.header("Authorization", "token " + decrypt(configuration.restApiKey))
            }
        }
    }
}
```

---

### 6.15 Automatic method preference

When the target server accepts more than one auth scheme, let the connector probe and pick:

```groovy
authentication {
    rest {
        preference apiKey, bearer
    }
}
```

The connector sends a test request to `configuration.restTestEndpoint` with each method in
the listed order. The first method that gets a 2xx–3xx response becomes the active method for
the lifetime of the connector. SCIM uses `/Schemas` as its probe endpoint.
