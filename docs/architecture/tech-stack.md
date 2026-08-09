# Grocery Recommendations Tech Stack

**Status:** AGREED
**Last updated:** 2026-08-09

## 1. Product Direction

Grocery Automate is a Kotlin Multiplatform application for:

- personalized grocery recommendations;
- recipe discovery and meal-to-shopping-list planning;
- transparent product, nutrition, and price comparisons;
- store-aware offers and availability;
- dietary, allergen, budget, household, and sustainability preferences.

The project follows the same shared-first, modular, adaptive, and event-driven direction as `personal-health`, while replacing health-provider concepts with canonical grocery, offer, recipe, and recommendation models.

The first release should favor deterministic rules, clear evidence, and replaceable data adapters. Machine-learning or generative-AI features may be added later, but may not become an untraceable source of product facts, dietary safety, prices, or availability.

## 2. Architecture

Dependencies flow in one direction:

```text
apps/* -> shared/app -> feature/* -> core/*
          |                         ^
          +------ integration/* ---+
```

Concrete integrations implement ports declared in core modules and are selected in the composition root. Feature modules never call one another or consume raw provider models.

| Layer | Responsibility | Planned examples |
|---|---|---|
| `apps/*` | Thin platform and backend entry points | Android, iOS, desktop, web, Ktor backend |
| `shared/app` | App shell, navigation, dependency wiring, event subscriptions | Shared Compose application |
| `feature/*` | UI state and user flows | Discover, compare, recipes, shopping list, preferences |
| `core/*` | Canonical models and pure rules | Catalog, offers, recipes, recommendations, comparisons, events |
| `integration/*` | External and platform adapters | Picnic client, retailers, Open Food Facts, recipe feeds, barcode, database |

Recommended initial modules:

```text
apps/android
apps/ios
apps/desktop
apps/web
apps/backend
shared/app
feature/discover
feature/compare
feature/recipes
feature/shopping-list
feature/preferences
core/catalog
core/offers
core/recipes
core/recommendations
core/comparisons
core/preferences
core/events
core/designsystem
integration/open-food-facts
integration/picnic-client
integration/retailers
integration/recipe-providers
integration/postgres
```

Add modules only when their boundary is real; the list is a target structure, not permission to create empty modules.

The implemented catalog slice uses `core/catalog` for provider-neutral models, `core/events` for
versioned envelopes and reducers, `integration/picnic-client` for provider mapping,
`integration/postgres` for the event store and projection, and `apps/backend` for the Ktor command,
query, event-feed, and health API.

## 3. Client Stack

| Technology | Purpose |
|---|---|
| Kotlin Multiplatform | Shared domain logic, state, contracts, and platform adapters |
| Compose Multiplatform | One adaptive UI for Android, iOS, desktop, and web |
| Kotlin coroutines and Flow | Async work and event/state streams |
| kotlinx.serialization | Canonical payload and API serialization |
| multiplatform-safe date/time library | Offer timestamps, promotion periods, and saved snapshots |
| Kotlin test | Deterministic common tests |
| JetBrains Kover | JVM line and branch coverage for common and JVM production code |

`commonMain` owns canonical models, ranking and filtering, comparison normalization, recipe scaling, state reducers, and repository ports. Platform code owns barcode scanning, notifications, location permission, secure storage, filesystem access, and platform HTTP/database configuration.

The web target is Compose Multiplatform on `wasmJs`. UI behavior remains semantically consistent across compact, medium, and expanded layouts.

## 4. Backend and Persistence

| Technology | Purpose |
|---|---|
| Ktor CIO on GraalVM Native Image | Versioned JSON API and low-overhead service runtime |
| PostgreSQL 18.4 | Append-only event data and rebuildable read models |
| Dedicated SQL migration runner | Ordered, transactional, checksum-protected migrations |
| JDBC, pgJDBC, and HikariCP | Explicit transactions and bounded database connections |
| OIDC/JWT | Authentication when accounts or cloud sync are introduced |
| Docker | Reproducible local backend and database runtime |

Use Ktor plugins only where needed and keep serialization paths explicit so Native Image does not
depend on runtime reflection. Keep domain decisions in shared pure Kotlin rather than Ktor routes.

The catalog event store atomically records command idempotency, contiguous stream versions,
immutable event envelopes, and projection changes. JDBC keeps ordering and transaction boundaries
visible. Projection rebuild replays the global cursor without changing the event log.

Local-first capability is desirable for saved recipes, preferences, comparisons, and shopping lists. Local storage sits behind core repository contracts so SQLite or browser storage can vary per platform without leaking into features. Cloud sync uses stable IDs, idempotent writes, and per-device cursors.

## 5. Canonical Grocery Model

Provider data must map to shared models before it reaches features or ranking logic.

| Model | Key responsibility |
|---|---|
| `Product` | Stable product identity, name, brand, categories, package description |
| `ProductComposition` | Ingredients, allergens, nutrition, labels, and verification state |
| `ProductOffer` | Store, region, price, pack quantity, unit price, promotion, stock, observed time |
| `Store` | Retailer identity, location/region, fulfillment modes |
| `Recipe` | Ingredients, quantities, servings, steps, dietary attributes, source |
| `DietaryProfile` | Allergies, dietary rules, dislikes, goals, household and budget constraints |
| `Recommendation` | Candidate, score, explanations, exclusions, evidence, generated time |
| `Comparison` | Normalized dimensions, winner per dimension, trade-offs, evidence snapshot |

Product identity is not the same as an offer. A single product can have multiple seller-, place-, pack-, time-, and promotion-specific offers.

Quantities use structured amount and unit values. Money uses integer minor units plus ISO currency. Do not use floating-point values for prices. External fields carry provenance, observation time, region, and verification/confidence metadata.

## 6. Catalog and Provider Integrations

Integrations are replaceable adapters:

```text
Retailer APIs / feeds     Open Food Facts     Recipe providers
          \                    |                    /
           integration/* adapters and validation
                           |
                 canonical core models
                           |
          recommendation and comparison engines
```

Initial provider priorities:

1. Curated deterministic fixtures for development and tests.
2. The ports-and-adapters Picnic client for an initial retailer connection, with dynamic provider payloads mapped into canonical models before feature use.
3. Open Food Facts for product identity, ingredients, nutrition, and barcode enrichment where coverage permits.
4. Retailer-specific APIs or permitted feeds for offers, promotions, and availability.
5. Licensed or explicitly permitted recipe sources.

Provider terms, attribution requirements, rate limits, caching rules, and redistribution rights must be reviewed before enabling production ingestion. Scraping is not a default integration strategy.

Ingestion validates payloads, records the raw source identifier and fetch time, maps units and currencies, rejects impossible values, and reports partial or conflicting data instead of silently guessing.

## 7. Recommendation Engine

The initial engine is deterministic and explainable:

```text
canonical candidates
  -> freshness and completeness check
  -> hard dietary/allergen/availability filters
  -> quantity and price normalization
  -> weighted scoring
  -> diversity and substitution rules
  -> explanation with source evidence
```

Hard constraints always run before scoring. A typical score can combine budget fit, nutritional fit, preference match, recipe usefulness, store convenience, sustainability signals, and data confidence. The formula, weights, and version are part of the result so a recommendation can be reproduced.

Scores must not create false precision. Missing evidence reduces confidence and may exclude a candidate from a safety-sensitive result. Sponsored placement, if ever introduced, must be visibly labeled and must not bypass constraints.

## 8. Product Comparisons

Comparisons declare their basis and snapshot time. Price comparisons normalize to `currency/kg`, `currency/l`, or `currency/item` while retaining the original pack price and quantity.

The comparison engine must:

- distinguish factual fields from derived scores;
- show missing or stale data;
- keep loyalty and multi-buy promotion conditions attached;
- prevent invalid comparisons across incompatible quantity dimensions;
- explain trade-offs instead of declaring a universal winner;
- allow user priorities to change dimension weights without changing facts.

Comparison snapshots are immutable once saved. Refreshing creates a new snapshot so historical decisions remain auditable.

## 9. Recipes and Shopping Lists

Recipes store structured ingredients rather than display strings alone. Ingredient matching resolves a recipe requirement to one or more canonical products or substitutions while retaining the user's dietary constraints.

Recipe cost and nutrition are derived snapshots containing:

- servings and scale factor;
- ingredient quantity and selected substitute;
- chosen offer and observation time;
- estimated waste or leftover quantity when known;
- nutrition source and confidence;
- total and per-serving estimates.

Shopping-list consolidation must combine compatible ingredient quantities, preserve recipe traceability, and allow manual items. A user edit takes precedence over later automatic recalculation unless the user explicitly refreshes it.

## 10. Events, CQRS, and Read Models

Core modules define events; `shared/app` and backend handlers coordinate consumers. Event categories are UI feedback, navigation, domain, and sync lifecycle.

Domain changes are event-driven all the way to the frontend:

```text
command
  -> decision model
  -> versioned domain event
  -> local or backend event store
  -> idempotent sync envelope and cursor
  -> rebuildable projection/read model
  -> Flow/observable frontend state
  -> Compose UI
```

The UI does not maintain a second independently mutated domain state. Reconnect resumes from the last acknowledged cursor; duplicate event IDs are ignored safely; projections tolerate retry and deterministic reordering rules. Push or streaming invalidation is preferred, with polling used only as a documented transport fallback.

Representative domain events:

- `DietaryProfileUpdated`
- `ProductImported`
- `OfferObserved`
- `ComparisonCreated`
- `RecommendationGenerated`
- `RecipeSaved`
- `RecipeScaled`
- `ShoppingListItemChanged`

For the durable backend, commands append immutable events and queries read projections. PostgreSQL read models may include `product_search_view`, `current_offer_view`, `recipe_detail_view`, `recommendation_view`, and `shopping_list_view`. Read models can be rebuilt; they are not the source of truth.

Use dynamic consistency boundaries only where concurrent decisions need them, such as deduplicating provider imports, accepting a time-sensitive offer, or updating a shared shopping list. Do not introduce event-sourcing ceremony for simple local UI state.

## 11. API Shape

Version all public routes under `/api/v1`.

| Area | Example routes |
|---|---|
| Catalog | `GET /api/v1/catalog/products`, `GET /api/v1/catalog/products/{id}` |
| Retailers | `GET /api/v1/retailers/{retailer}/products` plus retailer-specific actions |
| Offers | `GET /api/v1/offers?productId=&region=` |
| Recommendations | `POST /api/v1/recommendations/query` |
| Comparisons | `POST /api/v1/comparisons` |
| Recipes | `GET /api/v1/recipes`, `POST /api/v1/recipes/{id}/estimate` |
| Shopping lists | `GET /api/v1/shopping-lists/{id}`, `POST /api/v1/shopping-lists/{id}/items` |
| Preferences | `GET /api/v1/preferences`, `PUT /api/v1/preferences` |

Errors use a stable envelope with a machine-readable code, user-safe message, and optional field details. API responses containing prices or availability include observation time, region, currency, and source status.

## 12. Security and Privacy

- Use Authorization Code Flow with PKCE for interactive clients when authentication is added.
- Validate access tokens server-side and scope all user-owned data by subject ID.
- Store access tokens in platform-secure or in-memory storage, not plain local storage.
- Collect only the location precision needed for store and offer discovery; prefer region or postal-code level over precise coordinates.
- Treat allergies, dietary preferences, household details, and shopping history as private user data.
- Never log credentials, access tokens, exact locations, or complete private profiles.
- Keep provider secrets server-side and outside the repository.

## 13. Testing and Quality Gates

Minimum automated coverage includes:

- pure tests for unit and currency normalization;
- allergen and dietary hard-filter tests, including unknown data;
- deterministic ranking tests with fixed clocks and fixtures;
- recipe scaling, substitution, cost, and nutrition estimate tests;
- promotion-condition and stale-offer comparison tests;
- projection rebuild and API contract tests;
- compact and expanded UI tests for new features;
- keyboard, pointer, and touch behavior for primary actions.

Live provider tests are opt-in. Normal CI uses recorded, license-compatible fixtures and fake adapters so it remains deterministic and does not consume provider quotas.

The backend route suite uses Ktor's test host and fake ports. Testcontainers component tests run
the real PostgreSQL 18.4 schema, event append transaction, idempotency, cursor, rebuild, projection,
and HTTP chain. Docker Compose additionally builds and starts the actual native executable against
PostgreSQL; live Picnic access remains opt-in.

The Picnic integration includes a JVM-only, read-only smoke runner backed by the Ktor Java engine at the repository's existing Ktor version. It reads a captured session from an ignored local env file without executing shell content, performs search and product-detail reads through the production ports and adapters, and never runs as part of `check` or CI.

GitHub Actions runs `./gradlew check` on `macos-15` for JVM, iOS Simulator, Wasm, coverage, and
line-count gates. After that gate passes, a native job builds the GraalVM container, starts
PostgreSQL, and checks liveness, readiness, and a database-backed catalog query. Pull requests and
manual runs use an isolated GitHub-hosted Ubuntu runner; trusted main and tag pushes use the
self-hosted `homelab` runner. Docker-dependent JUnit classes skip only where Docker is unavailable;
the native job keeps the container contract required. On eligible pushes, that same homelab job
publishes the exact tested image to
`registry.home.intelliworks.nl:5000/grocery-automate/catalog-service`; it does not upload a private
image archive to GitHub or rebuild for release. Pull requests never publish, GitHub-hosted runners
cannot publish, and no provider credentials are available to any job. See the
[native backend CI/CD pipeline](native-backend-ci-cd.md).

Kover `0.9.8`, verified as the latest stable release from the official project on 2026-08-02, generates HTML and XML reports during `integration/picnic-client` checks. Capture-derived layout and Ktor transport tests currently measure 95.32% JVM line coverage and 65.46% branch coverage; non-regression floors are 95% and 64% respectively. Kover does not measure Kotlin/Native or Wasm execution, so those target tests remain independent quality gates. It passes with Gradle 9.6.1 but emits a dependency-notation deprecation that must be revalidated or resolved before a future Gradle 10 upgrade. See [JVM code coverage](code-coverage.md).

Use the Gradle wrapper, centralize dependency versions in `gradle/libs.versions.toml`, target JDK 17, and keep touched Kotlin and Gradle source files at or under 300 lines.

At project initialization and whenever a dependency is first added, select the latest stable compatible release verified through official release information. That policy is a selection rule, not a requirement to interrupt ordinary feature work whenever a newer release appears. Deliberate upgrades are separately scoped unless required for the feature, compatibility, or a security fix. Beta or release-candidate versions are limited to documented cases where platform support, compatibility, security, or another higher-priority architecture rule cannot be satisfied by a stable release. Less stable channels require explicit approval.

### Native backend version baseline

Versions were verified from official releases on 2026-08-04 and are pinned exactly for repeatable
builds:

| Component | Selected stable version |
|---|---:|
| GraalVM Community / Native Image | 25.2.4 / JDK 25.0.4 |
| GraalVM Native Build Tools | 1.1.6 |
| PostgreSQL | 18.4 |
| pgJDBC | 42.7.13 |
| HikariCP | 7.1.0 |
| Testcontainers | 2.0.5 |
| SLF4J | 2.0.18 |

For an upgrade, first check the [GraalVM Community releases](https://github.com/graalvm/graalvm-ce-builds/releases),
[Native Build Tools releases](https://github.com/graalvm/native-build-tools/releases),
[PostgreSQL releases](https://www.postgresql.org/docs/release/), and the libraries' official release
pages. Upgrade in a dedicated loop, retain JDK 17 compilation unless the repository rule changes,
then run event contracts, Testcontainers tests, `./gradlew check`, and the Compose native smoke.
Take a newer security/CPU fix immediately when required. Beta or RC remains an exception that must
record why stable cannot satisfy the architecture and what will trigger migration back to stable.

## 14. Deployment Direction

The default production shape is a static Compose/Wasm frontend plus native Ktor services and PostgreSQL:

```text
CDN/static host -> Compose/Wasm frontend
API ingress      -> GraalVM/Ktor backend -> PostgreSQL
                                -> provider adapters
```

Container images are immutable and configuration comes from environment variables or a secret manager. Production startup fails closed when required auth, database, public URL, or provider configuration is unsafe or missing.

## 15. Delivery Order

1. Establish Gradle/KMP structure, canonical models, fixtures, and tests.
2. Build preferences, catalog search, and evidence-rich product detail.
3. Add normalized comparisons with fixture offers.
4. Add deterministic recommendations and explanations.
5. Add structured recipes, cost estimation, and shopping-list generation.
6. Introduce live providers one at a time behind existing ports.
7. Add accounts, authenticated cursor sync, and optional offline writes when needed.

This order keeps the product useful and testable before live data availability or AI behavior becomes a dependency.

## 16. Decisions Still Needed

- Which countries, currencies, and retailers are in the first supported market?
- Which dietary goals are informational versus safety-critical?
- Which recipe and retailer data licenses permit caching and redistribution?
- What freshness window applies per provider and data type?
- Is anonymous local-only use required before accounts and sync?
- Which recommendation dimensions and default weights define the first product experience?
