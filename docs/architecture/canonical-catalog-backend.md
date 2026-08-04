# Canonical Catalog and Backend Query API

**Status:** SATISFIED
**Last updated:** 2026-08-04
**Depends on:** Picnic catalog object model and Picnic live smoke client

## 1. Overview

Introduce the first provider-neutral grocery catalog model and expose it through a small JVM Ktor
backend. Picnic remains an integration detail: backend and future frontend consumers receive
canonical products, compositions, offers, quantities, money, and provenance only.

## 2. Scope

### In scope

- A Kotlin Multiplatform `core/catalog` module.
- Separate canonical `Product`, `ProductComposition`, and `ProductOffer` concepts.
- Safe integer-minor-unit money and structured package quantities.
- Explicit unknown allergen, nutrition, availability, and verification states.
- Picnic search and detail mapping behind a core catalog port.
- `GET /api/v1/products?query=...&limit=...`.
- `GET /api/v1/products/{id}`.
- Local JVM backend composition using an ignored Picnic environment file.
- Deterministic core, adapter, route, error, and serialization tests.

### Out of scope

- Catalog persistence, ingestion events, projections, sync, frontend UI, caching, and pagination.
- Product deduplication across providers, barcode identity, and category taxonomy.
- Write endpoints, cart operations, checkout, accounts, recommendations, and comparisons.
- Public internet deployment, backend user authentication, and provider credential distribution.
- Provider-specific bundles, similar-product carousels, and dynamic page layout structures.

## 3. Current State

- `integration/picnic-client` exposes typed provider search and product-detail models.
- Provider models preserve current/legacy route provenance, integer-cent prices, ingredient and
  allergen evidence, and structured nutrition decimals.
- `core/catalog` and `apps/backend` did not exist before this loop.
- The live smoke client proves current Picnic reads with an ignored captured session.

## 4. Target Architecture

```text
HTTP client
  -> apps/backend Ktor route
  -> core/catalog ProductCatalogPort
  -> integration/picnic-client PicnicCanonicalCatalogAdapter
  -> PicnicCatalogPort
  -> live or fake Picnic transport
  -> canonical query result
  -> kotlinx.serialization JSON response
```

This slice is query-only. It does not change domain state, so commands, durable domain events,
event storage, sync envelopes, projections, and frontend event flows do not apply. A later catalog
ingestion loop must use `ProductImported` and `OfferObserved` events rather than persisting these
query responses directly.

## 5. Canonical Models and IDs

| Value | Representation | Invariant |
|---|---|---|
| `ProductId` | provider-qualified stable string | non-blank |
| `Product` | identity, name, brand, description, image, highlights | no retailer price fields |
| `ProductComposition` | ingredients, allergens, nutrition, origin, supplier, storage | missing safety data is unknown |
| `ProductOffer` | retailer, region, price, package, tiers, promotion, availability, evidence | price is integer minor units |
| `Money` | minor units plus ISO currency | no floating point |
| `DecimalAmount` | unscaled integer plus scale | non-negative scale |
| `PackageQuantity` | optional decimal, dimension-safe unit, original label | unknown units remain explicit |
| `ProviderEvidence` | provider, external ID, endpoint, region, observation time, API and route | travels with external facts |

Search may return `composition = null` because Picnic search tiles do not contain trustworthy
ingredient or allergen evidence. Product detail returns a composition whose allergen status is
still `UNKNOWN` when the provider omitted the section.

## 6. Query Port

| Query | Inputs | Result |
|---|---|---|
| Search products | non-blank query, limit 1..100 | canonical products and total provider match count |
| Get product | non-blank canonical/provider ID | canonical product or null when all routes are unavailable |

Provider authentication, route fallback, dynamic JSON, and provider exceptions remain behind the
integration. A malformed or unavailable provider response is not reported as product-not-found.

## 7. Backend API

### `GET /api/v1/products`

- Requires a non-blank `query` parameter.
- Optional `limit` defaults to 20 and accepts 1 through 100.
- Returns a canonical `ProductSearchResult`.

### `GET /api/v1/products/{id}`

- Returns a canonical `CatalogProduct`.
- Returns `404 PRODUCT_NOT_FOUND` only when the provider reports route-level absence.

Validation uses `400 INVALID_REQUEST`. Unexpected provider failures use a redacted
`502 PROVIDER_UNAVAILABLE`. Errors never contain tokens, headers, raw response bodies, or provider
payloads.

## 8. Provider Mapping Rules

- Picnic product IDs become `picnic:<country>:<external-id>` canonical IDs.
- Picnic is the retailer and provider; country code is the initial offer region.
- Prices remain integer euro cents with currency `EUR`.
- Quantity labels are parsed without floating point; unsupported labels preserve their original
  text with unit `UNKNOWN`.
- A missing price creates no offer rather than a zero-price offer.
- Promotions preserve label, promotion ID, strike-through price, and tier conditions.
- Search never invents composition data.
- Detail mapping preserves ingredients, contains/may-contain separation, nutrition basis and
  decimal values, storage, origin, supplier, description, highlights, and source evidence.
- Availability remains `UNKNOWN` because current catalog payloads do not prove stock state.

## 9. Backend Configuration and Safety

- The backend binds to `127.0.0.1` by default.
- `PICNIC_ENV_FILE` defaults to `.env.picnic.local` and may point directly at the discovery
  project's `.secrets/auth.env`.
- The file is parsed as assignments and never sourced as shell code.
- Picnic auth remains in memory and is sent only to Picnic hosts selected by request policy.
- The backend performs reads only and is not production-ready for public exposure.

## 10. Dependencies

- Reuse Kotlin `2.4.10`, Ktor `3.5.1`, kotlinx.serialization `1.11.0`, coroutines `1.11.0`, Gradle
  `9.6.1`, and JDK 17 already selected and verified for this repository.
- Add Ktor server core, Netty, content negotiation, status pages, serialization, and test-host
  artifacts at the existing Ktor version; no dependency version changes are part of this loop.

## 11. Testing and Verification

- Core model invariant, quantity parsing, and JSON round-trip tests.
- Picnic search mapping including price, package quantity, promotion, route and freshness.
- Picnic detail mapping including allergens, nutrition, missing safety data, and not-found rules.
- Backend success, validation, not-found, limit, and redacted provider-failure route tests.
- Existing Picnic transport and compatibility tests.
- Full JVM, iOS Simulator, Wasm, coverage, and line-count quality gate.

Exact commands:

```shell
./gradlew :core:catalog:allTests \
  :integration:picnic-client:jvmTest \
  :apps:backend:test \
  lineCountCheck
./gradlew check
```

An optional manual backend verification may use the existing ignored discovery auth file. It must
not be added to CI.

## 12. Completion Criteria

- [x] Canonical models compile on every configured core target.
- [x] Product, composition, and offer remain separate.
- [x] Money, quantity, provenance, freshness, and unknown safety states are explicit.
- [x] Picnic provider types do not escape the integration adapter.
- [x] Search and detail routes return canonical JSON.
- [x] Invalid, missing, and provider-failure responses use stable safe errors.
- [x] Backend credentials remain local, ignored, and absent from responses and logs.
- [x] Deterministic and repository quality gates pass.
- [x] README, module layout, and architecture documentation are current.

The optional live verification used the ignored discovery auth file and exercised the production
backend on loopback. Search returned current Picnic results as canonical products and offers;
detail returned structured composition, allergen evidence, nutrition, a 500 gram package, and
current-route provenance. The run also exposed and fixed a quantity extraction defect where a
brand label could be mistaken for package quantity.

## 13. Next Loop

The durable ingestion portion is complete in
[Native event-sourced catalog backend](native-event-sourced-backend.md). Frontend event consumption
and observable client projections remain the next bounded loop.
