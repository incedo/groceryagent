# Missing Functional Test Coverage

**Status:** BACKLOG
**Last reviewed:** 2026-08-02
**Current scope:** `integration/picnic-client`

## Purpose

This file records behavior that is not yet proven by automated tests. It is a pickup backlog, not a claim that every item is already implemented or ready to test.

Kover currently reports 95.32% JVM line coverage (`1141/1197`) and 65.46% branch coverage (`580/886`). These percentages measure executed JVM bytecode. They do not mean that the same percentage of the Picnic integration or grocery application works end to end.

The endpoint-parity test invokes 65 client routes with an in-memory fake transport. That proves basic request construction, but it does not prove live endpoint validity, response semantics, real Ktor behavior or successful provider mutations.

The evidence behind those gaps and the ordered fix loops are defined in [Picnic Contract Reconciliation](docs/architecture/picnic-contract-reconciliation.md). Use that document to distinguish a contradicted current request contract from an unobserved legacy route and from a route whose response is merely untyped.

## Current Proven Coverage

- Authentication login hashing and successful token capture.
- 2FA generation, verification and token rotation.
- Redaction of one structured provider authentication error.
- Method and route construction for the legacy 65-route client surface.
- Search and product-detail mapping through synthetic fixtures.
- Search and product-detail mapping through sanitized Picnic Android 1.239.3 captures.
- Seven distinct captured product-detail layouts.
- Integer-cent prices, one promotion shape, allergens, nutrition, preparation and similar products.
- Unknown allergens when the allergen block is absent.
- Missing nutrition remaining unknown rather than being invented.
- Recursive sensitive-data checks for committed capture fixtures.
- Common synthetic tests on JVM, iOS Simulator and Wasm.

## P0: Provider Boundary and Authentication

### Real Ktor transport contract — completed 2026-08-02

`KtorPicnicHttpTransportContractTest` now exercises the production adapter with Ktor `MockEngine` on JVM, iOS Simulator and Wasm.

It proves:

- HTTP method and URL forwarding;
- ordinary request headers and content-header normalization;
- absent and present request bodies;
- response status, headers and body bytes;
- JSON and binary image responses;
- cancellation and transport exception propagation.

Repeated request headers remain outside the current port because `PicnicHttpRequest` represents headers as `Map<String, String>`. Repeated response headers are preserved and tested.

### HTTP and provider error matrix

**Missing:** only one structured `401` response is tested.

Cover:

- empty and non-JSON error bodies;
- JSON errors without `error.message`;
- `304 Not Modified`;
- representative `400`, `401`, `403`, `404`, `409`, `429` and `5xx` responses;
- provider messages that must be exposed safely;
- bodies containing secrets that must never enter exception messages;
- retry or backoff classification once retry behavior is designed.

**Complete when:** every status class has an explicit, user-safe outcome and tests do not leak raw provider bodies.

### Authentication failure and lifecycle behavior

Cover:

- blank username, password, 2FA channel, OTP and phone values;
- successful login without the required auth response header;
- successful 2FA verification without a replacement auth header;
- malformed or incomplete login JSON;
- failed logout and the decision whether the local token remains available;
- expired or rejected tokens;
- secure persistent auth-store adapters when introduced;
- concurrent reads and token replacement.

**Complete when:** token state after every success and failure is deterministic and documented.

### Production routing and country configuration

This work is the first executable reconciliation loop, PCR-001.

Cover:

- the default Netherlands API and storefront URLs;
- Germany and France host and `Accept-Language` selection;
- trailing-slash normalization for overrides;
- unsupported country codes;
- invalid API version, device ID, agent and client ID;
- endpoint families that require a different host from the storefront host.

**Complete when:** route construction is proven for every supported country and provider host family.

### Current-first route fallback — completed for typed catalog and login 2026-08-02

Proven:

- current-route success without invoking the legacy route;
- definitive unsupported-route responses selecting a legacy read route;
- current and legacy response shapes mapping to the same typed Picnic model;
- both routes failing with one sanitized combined outcome;
- authentication rejection, rate limiting, cancellation, timeout and `5xx` never triggering fallback;
- mapping incompatibility fallback for read-only operations without logging raw data;
- cart, checkout, order, consent, delivery, payment and account mutations never being sent twice;
- explicit pre-request legacy selection for a mutation when a capability decision requires it.

The reusable policy is complete for capabilities with an agreed typed model. Each future route pair still needs its own sanitized mapper-equivalence cases before enabling fallback.

## P1: Captured Response Object Models

The local discovery repository contains ignored captures for cart, delivery slots, deliveries, meals, messages, user, wallet and related page endpoints. Do not commit these raw files. Introduce each capture only after its typed object model and privacy scope are agreed.

### Cart and delivery slots

Current cart methods prove route invocation only. Add typed models and sanitized capture tests for:

- empty and populated carts;
- add, multi-add, decrement, remove and clear outcomes;
- selling-unit contexts;
- price, deposit, discount and promotion totals;
- minimum-order value;
- available, unavailable and expired delivery slots;
- setting and replacing a delivery slot;
- invalid product, group and slot IDs;
- zero, negative and excessive quantities;
- duplicate product mutations and idempotency expectations.

### Checkout and order lifecycle

Add current API contracts for:

- pre-checkout validation and upsell tasks;
- checkout start;
- payment-required, rejected and successful checkout states;
- confirmation and order-status transitions;
- unavailable or expired slots during checkout;
- duplicate submission protection;
- provider errors that leave cart state uncertain.

The captured `checkout/start` failure is evidence of a route, not a successful checkout contract. Never run order-placement tests against the live provider in ordinary CI.

### Deliveries and wallet

Add typed, privacy-reviewed models and capture tests for:

- current and historical deliveries;
- delivery summary, position and scenario;
- cancellation and rating outcomes;
- invoice-email requests;
- payment profile;
- wallet pagination and transaction details;
- missing, pending, failed and refunded payment states;
- currency and integer-minor-unit preservation.

### Recipes and meals

Routes currently return raw `JsonElement`. Add typed models and capture tests for:

- recipe identity, title, source and serving count;
- structured ingredient quantities and units;
- preparation steps and multiple methods;
- saving and unsaving;
- assigning, resizing and removing selling groups;
- substitutions and unavailable ingredients;
- price and nutrition snapshots;
- shopping-list consolidation with recipe provenance.

### User, consent and customer-service data

Add typed models only after a privacy review. Cover:

- incomplete user/profile responses;
- consent read and update behavior;
- messages, reminders and parcels;
- onboarding input validation;
- push-token registration failures;
- update-check responses;
- explicit redaction and logging rules for private account data.

## P1: Catalog Edge Cases

### Product identity and fallback behavior

Cover:

- no matching `sellingUnit` for the requested product;
- missing product ID or name;
- duplicate product IDs;
- missing main container;
- header-only fallback name, brand, quantity, price and image;
- empty or malformed accordion items;
- pages that contain no recognizable product.

Define whether an unusable page returns a partial object or a typed mapping failure before adding assertions.

### Product bundles

No current capture contains a `product-page-bundles-*` container. The bundle extraction path remains untested.

Cover:

- one and multiple bundle choices;
- missing nested selling units;
- missing or zero prices;
- missing image and maximum-count values;
- ordering and derived quantity behavior.

Prefer a new sanitized provider capture. Use a synthetic fixture only when the expected provider shape is documented.

### Search, prices and promotions

Cover independently:

- empty search and no-results pages;
- duplicate and malformed selling units;
- decorator-only prices;
- integer-compatible decimal prices such as `189.0`;
- zero, negative, missing and malformed prices;
- `image_ids` fallback;
- price-range rows missing price or quantity;
- top-level and nested promotion fields;
- promotion badge without price details;
- loyalty and multi-buy conditions when observed;
- stale or unavailable offers once freshness mapping exists.

### Dietary and allergen safety

Cover:

- every supported contains/may-contain heading;
- duplicate allergen labels with different casing;
- comma-separated and individually listed allergens;
- malformed and unexpectedly long values;
- conflicting sections;
- observed allergen section with no usable values;
- incomplete cross-contamination information;
- German and French labels before enabling those storefronts.

Missing or ambiguous data must continue to fail closed.

### Nutrition and preparation

Cover:

- nutrition with an unknown basis;
- grams and millilitres independently;
- dot and comma decimals;
- missing, malformed and unexpectedly formatted nutrient values;
- kilojoules containing separators or combined values;
- fibre and saturated-fat variations;
- preparation with no explicit step markers;
- multiple named preparation methods;
- malformed or blank steps;
- every documented preparation stop heading.

Assertions should verify exact typed values, not merely non-null results.

### Images

Cover:

- every configured image size;
- empty and non-image response bodies;
- provider image failures;
- actual response content type;
- PNG and WebP behavior before claiming both formats;
- correct data-URI media type instead of assuming PNG.

## P2: Resilience and Platform Confidence

### Network resilience

No retry, timeout, rate-limit or caching policy is currently proven. Once behavior is agreed, cover:

- connection failure and timeout;
- coroutine cancellation;
- retryable versus non-retryable errors;
- `Retry-After` handling;
- bounded retries and backoff with a fake clock;
- offline behavior;
- cached responses and `304` handling;
- duplicate mutation protection.

### Random IDs and clocks

Tests currently inject fixed IDs and clocks. Cover production adapters for:

- UUID shape, version and variant bits;
- deterministic seeded generation;
- practical uniqueness across a bounded sample;
- timestamp formatting and timezone behavior;
- monotonic assumptions, if any are introduced.

### Cross-platform capture execution

Sanitized resource tests are JVM-only because they use JVM classpath resources. The shared mapper is compiled for all targets, but the complete captured documents are not executed on iOS or Wasm.

Add a multiplatform-safe fixture-loading approach if cross-target execution becomes worth the build and artifact cost. Do not count JVM Kover results as native or Wasm coverage.

## Not Yet Testable: Grocery Application Flow

The current repository implements a Picnic integration client, not the complete grocery product. The following end-to-end behavior does not exist yet and therefore cannot be considered tested:

- mapping provider products into canonical `Product` and `ProductComposition`;
- mapping retailer prices into region-, quantity- and time-specific `ProductOffer` snapshots;
- command to domain event to event-store flow;
- incremental sync envelopes, cursors and conflict handling;
- projections and frontend `Flow` updates;
- dietary hard filtering before recommendation ranking;
- explainable recommendation scoring and evidence;
- normalized product and offer comparisons;
- recipe costing, nutrition estimates and shopping-list consolidation;
- compact and expanded adaptive UI behavior;
- keyboard, pointer and touch interaction.

Each future feature loop must test the complete event-driven path required by `AGENTS.md`, not only its provider adapter.

## Fixture Safety Requirements

For every new capture-derived fixture:

1. Keep the raw capture ignored and outside the committed fixture tree.
2. Select response bodies only; never include request headers or flow envelopes.
3. Extend the deterministic sanitizer before generating the fixture.
4. Remove authentication, device, session, account, address and contact data.
5. Pseudonymize provider IDs and opaque identifiers.
6. Run the recursive sensitive-key and sensitive-value scanner.
7. Review the generated diff for product-data licensing and unnecessary payload volume.
8. Use fixed clocks, fake transports and no live provider quota in ordinary CI.

Never weaken a scanner or expected result merely to make a new fixture pass.

## Recommended Pickup Order

1. Add the HTTP/provider error matrix and authentication lifecycle failures.
2. Validate production country and host routing.
3. Define typed cart and offer models, then sanitize the existing cart and slot captures.
4. Add checkout and delivery state models without performing live order placement.
5. Define typed recipe models, then sanitize existing meal captures.
6. Close catalog bundle, localization, malformed-data and image-format gaps.
7. Connect provider models to canonical grocery events, projections and frontend state.

Each item should use its own `AGREED` loop spec, `codex/*` branch and pull request.

## Verification Commands

Use the smallest relevant set first, then run the full gate when shared contracts or build wiring change:

```shell
./gradlew :integration:picnic-client:jvmTest \
  :integration:picnic-client:koverLog \
  :integration:picnic-client:koverVerify \
  lineCountCheck
./gradlew check
git diff --check
```

Update this file whenever a gap is closed, split into an agreed loop, newly discovered, or deliberately accepted.
