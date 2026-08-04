# Picnic Live Smoke Client

**Status:** SATISFIED
**Last updated:** 2026-08-02
**Depends on:** Picnic contract reconciliation PCR-001

## 1. Overview

Provide an opt-in JVM smoke client that proves the real transport, captured session headers,
current-first catalog routes, and typed object mapping work together against Picnic.

## 2. Scope

### In scope

- Read a local env file without executing it.
- Accept the auth shape exported by `picnic-api-discovery/tools/dump_auth.py`.
- Perform one product search and one product-detail read.
- Print only product data and route provenance, never authentication values.
- Keep live access outside ordinary tests and CI.

### Out of scope

- Account login, 2FA automation, token refresh, writes, cart changes, checkout, and user data.
- Committing credentials, capture traffic, or personalized provider responses.
- Treating the private Picnic API as stable or officially supported.

## 3. Architecture and Safety

The JVM smoke entry point is test tooling and composes the existing ports and adapters:

```text
local env file -> PicnicClientConfig + InMemoryPicnicAuthStore
  -> PicnicCatalogPort -> KtorPicnicHttpTransport -> live Picnic read routes
  -> typed PicnicSearchResult + PicnicProductDetails -> redacted console summary
```

The env parser accepts assignments only; it does not source a shell file. The session remains
in memory. `.env.picnic.local` and `.secrets/` are ignored. A `401` produces a renewal hint but
does not print the provider token or response body.

No domain state changes, events, projections, sync envelopes, or frontend updates apply because
this is a read-only developer verification adapter rather than an application feature.

## 4. Environment Contract

Required values:

- `PICNIC_AUTH`
- `PICNIC_DID`, or its local-file alias `PICNIC_DEVICE_ID`

Client identity uses either `PICNIC_AGENT` in captured `<client-id>;<version>-#<build>` form
(the older form without `#` is accepted), or all three explicit client identity values below.

Supported capture values:

- `PICNIC_UA`
- `PICNIC_HOST`, used only to infer a storefront country

Optional explicit overrides:

- `PICNIC_COUNTRY` and `PICNIC_API_VERSION`
- `PICNIC_CLIENT_ID`, `PICNIC_CLIENT_VERSION`, and `PICNIC_BUILD_NUMBER`; together these can
  replace `PICNIC_AGENT`, which the loader then constructs in the captured format

## 5. Verification

Verified on 2026-08-02 against the local discovery auth file. A search for `pasta` returned 150
typed products, after which the first result returned typed current-route product details with
ingredients, observed allergen data, and per-100-gram nutrition. No live response was persisted.

Deterministic checks:

```shell
./gradlew :integration:picnic-client:jvmTest lineCountCheck
```

Explicit live check using the discovery project directly:

```shell
./gradlew :integration:picnic-client:picnicLiveSmoke \
  -PpicnicEnvFile=/Users/kees/data/projects/picnic-api-discovery/.secrets/auth.env \
  -PpicnicQuery=pasta
```

An optional `-PpicnicProductId=s...` selects the detail product; otherwise the first search
result is used. Running the live task is manual and may consume provider capacity.

## 6. Completion Criteria

- [x] Captured auth env shape loads through deterministic tests.
- [x] Missing and malformed configuration fails without revealing values.
- [x] Normal CI never calls Picnic.
- [x] Live search returns typed products.
- [x] Live detail returns a typed product with route provenance.
- [x] Repository checks and secret scanning pass.

## 7. Next Loop

Add an interactive login/2FA utility only if captured sessions prove too short-lived for practical
local development. It must use secure input and must never persist plaintext passwords.
