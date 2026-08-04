# Grocery Automate

Kotlin Multiplatform project for explainable grocery recommendations, structured recipes, shopping lists, and evidence-based product and price comparisons.

The project is being established with the same shared-first architecture used by the local `personal-health` project:

- Compose Multiplatform for Android, iOS, desktop, and web;
- shared Kotlin domain models and deterministic recommendation rules;
- Ktor for versioned backend APIs;
- PostgreSQL for durable events, source snapshots, and read models;
- provider-specific adapters for retailers, product catalogs, and recipe sources;
- adaptive layouts with touch, pointer, and keyboard support.

## Core Principles

- Separate canonical products from store-, region-, and time-specific offers.
- Apply allergen, dietary, availability, and budget constraints before ranking.
- Normalize prices and quantities before comparison.
- Attach source, freshness, region, and confidence metadata to external facts.
- Make recommendation scores and trade-offs reproducible and explainable.
- Preserve recipe and comparison snapshots so later source changes do not rewrite history.

## Documentation

- [Project rules](AGENTS.md)
- [Kotlin Multiplatform and Compose best practices](docs/kmp-compose-best-practices.md)
- [Tech stack and architecture direction](docs/architecture/tech-stack.md)
- [Implementation-loop configuration](docs/architecture/LOOP-CONFIG.md)
- [Feature and bounded-context spec template](docs/architecture/TEMPLATE.md)
- [Picnic Kotlin client compatibility and architecture](docs/architecture/picnic-client.md)
- [Picnic captured-contract reconciliation and implementation loops](docs/architecture/picnic-contract-reconciliation.md)
- [Opt-in Picnic live smoke client](docs/architecture/picnic-live-smoke-client.md)
- [Picnic catalog object model](docs/architecture/picnic-catalog-object-model.md)
- [Sanitized Picnic capture fixtures](docs/architecture/picnic-sanitized-fixtures.md)
- [Picnic capture coverage expansion](docs/architecture/picnic-capture-coverage.md)
- [Ktor transport contract](docs/architecture/ktor-transport-contract.md)
- [JVM code coverage](docs/architecture/code-coverage.md)
- [Required project skills and capabilities](docs/project-skills.md)
- [Missing functional test coverage](missingtest.md)

## Module Layout

- `apps/*` platform and backend entry points
- `shared/app` shared app shell and composition root
- `feature/*` grocery discovery, comparisons, recipes, lists, and preferences
- `core/*` canonical models, rules, events, and design primitives
- `integration/*` retailer, catalog, recipe, persistence, and platform adapters

The first implemented module is `integration/picnic-client`, a Kotlin Multiplatform ports-and-adapters client matching the public route surface of `MRVDH/picnic-api` 4.6.0. It exposes Picnic service ports while keeping HTTP, authentication storage, time, and password hashing replaceable at the composition root.

Changing Fusion/PML documents are isolated inside the integration. Catalog search and product details return typed Picnic objects for products, integer-cent prices, volume ranges, promotions, ingredients, contains/may-contain allergens, nutrition, preparation and source metadata. Explicit page and raw methods remain available for discovery and forward compatibility. Application features must still map provider objects into canonical grocery models before use.

Typed catalog reads use a current-first compatibility policy. The captured page routes are attempted first; a definitively unavailable or incompatible read can use the legacy clean route and map its different response into the same Picnic object model. Route generation is retained as provenance. State-changing operations are never automatically sent twice.

Run its deterministic contract checks with:

```shell
./gradlew :integration:picnic-client:jvmTest lineCountCheck
```

Pull requests run the complete JVM, iOS Simulator, Wasm, line-count, and coverage quality gate through GitHub Actions:

```shell
./gradlew check
```

Generate JVM coverage reports with:

```shell
./gradlew :integration:picnic-client:koverHtmlReport \
  :integration:picnic-client:koverXmlReport \
  :integration:picnic-client:koverVerify
```

The HTML report is written to `integration/picnic-client/build/reports/kover/html`. Kover measures `commonMain` and JVM production code through JVM tests; iOS and Wasm behavior remains covered by their separate test tasks. The current measured coverage is 95.32% lines and 65.46% branches, with non-regression gates at 95% and 64% respectively.

The application composition root supplies the platform Ktor engine and chooses a persistent secure auth-store adapter when session persistence is needed:

```kotlin
val picnic = PicnicClient(
    transport = KtorPicnicHttpTransport(platformHttpClient),
    authStore = platformSecureAuthStore,
)

val search = picnic.catalog.search("wholegrain pasta")
search.products.forEach { product ->
    println("${product.name}: ${product.priceCents} cents")
}
```

For a manual read-only check against Picnic, the JVM smoke client can consume the auth file
created by the local API-discovery project. It never runs in ordinary tests or CI:

```shell
./gradlew :integration:picnic-client:picnicLiveSmoke \
  -PpicnicEnvFile=/Users/kees/data/projects/picnic-api-discovery/.secrets/auth.env \
  -PpicnicQuery=pasta
```

See [the live smoke-client contract](docs/architecture/picnic-live-smoke-client.md) for its
environment shape, safety boundaries, and optional product-id selection.
