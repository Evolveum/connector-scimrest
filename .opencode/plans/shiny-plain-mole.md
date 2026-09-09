# Plan: ConnId → SCIM filter translation for SCIM searches

## Context

`ScimSearchHandler.translate(Filter)` (`common/.../impl/scim/ScimSearchHandler.java:84`) is a stub returning `null`, so any non-UID ConnId filter on a SCIM search is **silently dropped** — the remote gets an unfiltered query and the connector returns results that don't match the filter. This change implements real ConnId → RFC 7644 filter translation (per RFC 7643/7644), using the unboundid SCIM2 SDK already on the classpath (`scim2-sdk-common:6.0.0`, `com.unboundid.scim2.common.filters.Filter` with `eq/ne/co/sw/ew/pr/gt/ge/lt/le/and/or/not` factories, `toString()` serialization with proper escaping, `fromString()` parser).

Scope decisions (confirmed with user):
- **Our own code, NOT the ConnId `FilterTranslator` interface** — no `createFilterTranslator` override, no conndev-base changes.
- **Unsupported filters throw** `IllegalArgumentException` (consistent with `FilterBasedSearchDispatcher`'s "Unsupported filter"), fixing the silent-drop bug.
- **Implement the `scim { limitations { supportedFilter(spec) { } } }` DSL** — declarative only (no override/mapping semantics; the closure body is evaluated against the empty `FilterSupportImplementation` marker delegate), used by the dispatcher via `supports()` to check applicability.
- **Nested SCIM attribute paths supported** via the existing path mechanics in ../conndev (`AttributePath`, `ScimPath.parse`/`ScimPath.serialize`), incl. implementing the `scim { path("...") }` string DSL (current FIXME at `RestAttributeBuilderImpl.java:109-112`).

## Mapping (ConnId filter → RFC 7644 §3.4.2.2)

| ConnId | SCIM | Notes |
|---|---|---|
| `EqualsFilter` | `eq` | value: String/GuardedString/Boolean/Integer/Long/Double/Float/Date |
| `ContainsFilter` | `co` | string value only |
| `StartsWithFilter` | `sw` | string value only |
| `EndsWithFilter` | `ew` | string value only |
| `GreaterThanFilter` / `GreaterThanOrEqualFilter` / `LessThanFilter` / `LessThanOrEqualFilter` | `gt` / `ge` / `lt` / `le` | SDK typed overloads (incl. `Date`) |
| `AndFilter` / `OrFilter` (n-ary via `getFilters()`) | `and(...)` / `or(...)` (`Filter.and(List)`) | empty/1-subfilter = malformed → throw |
| `NotFilter` | `not(...)` | |
| `EqualsIgnoreCaseFilter`, `ContainsAllValuesFilter`, `ExternallyChainedFilter`, unknown filters | — | throw `IllegalArgumentException` (visitor fallback `visitExtendedFilter` + explicit cases) |
| top-level `Uid` equals | — | unchanged: still short-circuits to SCIM retrieve (`performGet`) |
| attribute names | attribute's SCIM path | `uid`→`id`, `name`→`userName`/`displayName` (per detection rules), nested `name.givenName`, extension `urn:...:employeeNumber` |

Attribute path resolution: per attribute, `RestAttributeDefinition` → `attr.connId().getName()` (key) → `attr.scim().path()` (`AttributePath`). Filterable path = no `FilterComponent` (index/value filters). Path → SCIM string via `ScimPath.serialize(path)` (conndev; correct `:` for extension URIs, dotted nesting, escaping — `AttributePath.toString()` is NOT used, it mangles extension URIs).

## Changes

### 1. NEW `common/src/main/java/com/evolveum/polygon/scimrest/impl/scim/ScimFilterTranslator.java`
- `static ScimFilterTranslator fromObjectClass(RestObjectClassDefinition oc)` — builds `Map<connIdName, AttributePath>` from `oc.attributes()` (skip attrs without `scim()` mapping; first definition wins). Test-friendly ctor taking that map directly.
- `boolean isTranslatable(Filter)` — recursive check (null → true; composites → children; attribute filter → supported type + known attribute + filterable path).
- `String translate(Filter)` — null → null; walks via ConnId `FilterVisitor<com.unboundid...Filter, Void>` (`filter.accept(visitor, null)`); builds SDK filter tree; returns `sdkFilter.toString()`.
  - Attribute not in map, non-filterable path, unsupported filter type, unsupported value type, wrong value count → `IllegalArgumentException("Filter cannot be translated to SCIM: ...")` (message includes the offending attribute/filter).
- No comments; EUPL header (rewrite plugin enforces).

### 2. MODIFY `ScimSearchHandler.java`
- Drop stub `translate(Filter)`; add `private final ScimFilterTranslator filterTranslator` (ctor: `ScimFilterTranslator.fromObjectClass(objectClass)`).
- `performSearch`: `var scimFilter = query == null ? null : filterTranslator.translate(query);` computed **before** the try block (translation errors surface as `IllegalArgumentException`, not wrapped "SCIM search failed"; both end up `ConnectorException` at the `ClassHandlerConnectorBase` boundary, same as dispatcher errors).
- `supports(Filter)`:
  ```java
  if (filter == null) return supportsEmptyFilter;
  if (!filterTranslator.isTranslatable(filter)) return false;
  if (supportsAnyFilter) return true;
  return supportedFilters.stream().anyMatch(a -> a.matches(filter));
  ```
  (dispatcher routing now accurate; non-translatable filters can fall through to other handlers of the same object class, else "Unsupported filter")
- `Builder.LimitationsImpl.supportedFilter(spec, closure)`: replace `UnsupportedOperationException` with declarative impl — `closure.setDelegate(new FilterSupportImplementation() {})`, `DELEGATE_ONLY`, `closure.call()`, then `Builder.this.supportedFilters(spec)`. Javadoc: SCIM uses automatic translation; the closure form only declares supported-filter limitations for dispatcher applicability checks (no override).
- `executeQuery` / `performGet` unchanged.

### 3. MODIFY `RestAttributeBuilderImpl.java` (`ScimBuilder.path(String)`, line ~109)
- `this.path = ScimPath.parse(path); return this;` (conndev `com.evolveum.polygon.conndev.api.ScimPath`) — enables `scim { path("name.givenName") }`, `path("emails[type eq \"work\"].value")`, `path("urn:...:User:employeeNumber")`; resolves the existing FIXME.

### 4. Tests (TestNG, existing patterns)

**Unit** — `common/src/test/java/.../unit/scim/ScimFilterTranslatorTest.java` (no WireMock; explicit name→path map):
- exact SCIM expressions per operator: `userName eq "jdoe"`, `userName co "do"`, `sw`/`ew`, `age gt 30`, `age ge 30`, `age lt 30`, `age le 30`, `active eq true`
- values: escaping (`Jo"hn` → `Jo\"hn`), Long/Double, `GuardedString`, `java.util.Date`
- name mapping: `uid`→`id`, `name`→`userName` (explicit map), nested `name.givenName` (via `ScimPath.parse`), extension URI paths
- composites: and (2- and 3-operand), or, not, nested `and(not(...), ...)`
- round-trip: `com.unboundid...Filter.fromString(result)` re-parses; `toString()` idempotent
- `assertThrows(IllegalArgumentException)`: `EqualsIgnoreCaseFilter`, `ContainsAllValuesFilter`, `ExternallyChainedFilter`, unknown attribute, value-filter path (`emails[type eq "work"].value`), empty/multi-value single-value filter, non-string value on `co`, unsupported value type (e.g. `EmbeddedObject`)
- `isTranslatable` true/false mirroring the above

**Integration (WireMock)** — pattern of `ScimLimitationsEmptyFilterSupportedSearchTest` (stub `/Schemas` with richer User attrs: `userName`, `active` (boolean), `age` (number), `id`; `/ResourceTypes`; `/Users`):
- `crud/ScimSearchFilterTranslationTest.java`:
  - eq / contains / gt / boolean-eq / compound and/or/not → assert GET `/Users` request's `filter` query param (WireMock auto-decodes)
  - `and(uid eq "1", active eq true)` → `id eq "1" and active eq true` on the wire
  - top-level uid-equals still uses retrieve (`GET /Users/1`, no `filter` param) — regression guard
  - `EqualsIgnoreCaseFilter` → exception; assert NO `/Users` search request recorded
- `crud/ScimSearchFilterLimitationsTest.java`:
  - `scim { limitations { supportedFilter(attribute("userName").eq().anySingleValue()) { } } }` (empty closure body) accepted — regression vs old `UnsupportedOperationException`
  - declared spec matches (eq query) → filter sent; non-matching query (contains) → `IllegalArgumentException` "Unsupported filter" from dispatcher
  - spec-only form `supportedFilter(spec)` (no closure) unchanged behavior
- `crud/ScimSearchNestedAttributeFilterTest.java` (Groovy schema script):
  - schema: `objectClass("User") { attribute("givenName") { scim { path("name.givenName"); type "string" } } }` (new `path(String)`); filter on it → `name.givenName eq "Jane"`
  - plus unit-style assertion that `path("emails[type eq \"work\"].value")` builds a path whose filter is rejected by translation (unit test covers the rejection)

### 5. Verification
- `mvn -pl common test -Dtest=ScimFilterTranslatorTest` then new integration tests individually, then full `mvn -pl common test`
- Full build at the end: `mvn clean install` (all modules; connector bundles unaffected but ensure nothing else breaks)
- `mvn rewrite:run` if the project's style enforcement demands (EUPL headers/unused imports) — check existing files' headers first

## Out of scope / notes
- No ConnId `FilterTranslator`/`createFilterTranslator` involvement (per user) — no conndev-base changes.
- SCIM `pr` and SDK complex-value filters are never *produced* (no ConnId source filter for "attribute present"); value-filter **paths** on attributes cannot be comparison-filter targets in v1 (RFC 7644 filter `attributePath` ends at the complex value filter) → rejected with clear error.
- REST endpoint search path untouched (has its own Groovy closure mapping). YAML model untouched (SCIM search config is Groovy-only; handler change is shared).
- Paging (hardcoded page size 25) and retrieve-by-uid behavior unchanged.
