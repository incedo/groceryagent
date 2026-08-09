# Native Event-Sourced Catalog Backend

**Status:** SATISFIED
**Last updated:** 2026-08-09
**Depends on:** Canonical catalog and backend query API

## 1. Overview

Turn the first catalog backend into a deployable catalog microservice whose durable source of truth
is an append-only PostgreSQL event store. The service runs as a GraalVM native executable, imports
canonical products from the Picnic adapter through commands, updates rebuildable projections, and
serves catalog queries from PostgreSQL rather than treating live provider responses as durable
state.

## 2. Scope

### In scope

- Multiplatform event contracts and catalog import events in `core/events`.
- A PostgreSQL adapter in `integration/postgres` with ordered migrations.
- Immutable event envelopes with stable IDs, schema versions, stream versions, producer IDs,
  correlation/causation IDs, timestamps, and global cursor positions.
- Optimistic stream concurrency and command-level idempotency.
- Atomic event append and catalog projection updates.
- Deterministic projection rebuild from the event log.
- A provider product-import command and persisted product search/detail queries.
- A cursor-based event feed for future frontend synchronization.
- Ktor CIO and GraalVM Native Image packaging.
- Docker Compose for the native service and PostgreSQL 18.4.
- Testcontainers component and integration tests using PostgreSQL 18.4.

### Out of scope

- User accounts, authorization, public internet exposure, TLS termination, and multi-tenant data.
- Frontend event consumption, push transport, offline client writes, conflict UI, and tombstones.
- Catalog-wide provider crawling, scheduled refresh, cross-provider deduplication, and caching.
- Recommendation, comparison, recipe, and shopping-list events.
- Production orchestration, backups, replicas, telemetry aggregation, and zero-downtime migrations.

## 3. Current State

- `apps/backend` uses Ktor Netty and calls `ProductCatalogPort` directly for every search/detail read.
- `core/catalog` owns canonical catalog objects and the provider-neutral query port.
- `integration/picnic-client` maps live Picnic objects into the canonical catalog model.
- There is no event contract, durable store, migration, projection, or database module.
- The backend is verified on the JVM but has no native executable or container runtime.

## 4. Target Architecture

```text
POST product import command
  -> catalog import service
  -> Picnic ProductCatalogPort
  -> canonical CatalogProduct
  -> ProductImported + OfferObserved decisions
  -> PostgreSQL append transaction
       event_streams + event_commands + domain_events
       catalog_products projection
  -> command result with stream/global positions

GET product/search
  -> PostgreSQL catalog projection
  -> canonical JSON

GET event feed after cursor
  -> immutable event envelopes
  -> future frontend sync consumer
```

Provider responses are transient inputs. `domain_events` is the durable source of truth and
`catalog_products` is replaceable query state. Event append, idempotency record, and projection
changes commit in one PostgreSQL transaction.

## 5. IDs and Event Metadata

| Value | Representation | Rule |
|---|---|---|
| Event ID | UUID string | globally unique and immutable |
| Command ID | UUID string | required idempotency key |
| Stream ID | `product:{canonicalProductId}` | stable aggregate boundary |
| Stream version | positive `BIGINT` | contiguous within one stream |
| Global position | database-generated `BIGINT` | cursor ordering only |
| Schema version | positive integer | starts at 1 |
| Producer ID | non-blank string | identifies backend/device/importer |
| Occurred time | UTC ISO-8601 string | supplied by a clock at decision time |
| Correlation ID | UUID string | equals command ID for this loop |
| Causation ID | optional UUID string | absent for external import commands |

## 6. Command

| Command | Fields | Decision | Emits |
|---|---|---|---|
| `ImportProviderProduct` | provider product ID, command ID, producer ID | require provider detail; snapshot canonical facts | one `ProductImported`, then zero or more `OfferObserved` |

`POST /api/v1/retailers/picnic/products/{providerProductId}/imports` requires an `Idempotency-Key`
UUID. A retry
with the same key returns the original append result and cannot append or project twice. A command
ID reused for another stream is rejected.

## 7. Events

| Event | Stream | Payload | Schema |
|---|---|---|---|
| `ProductImported` | product stream | product, optional composition, evidence | 1 |
| `OfferObserved` | product stream | complete offer with price, quantity, availability, and evidence | 1 |

Events use explicit type names and serializers. Unknown type/schema combinations fail projection
rebuild visibly; they are never silently discarded.

## 8. Event Store and Concurrency

- `event_streams` owns the current stream version and is locked during append.
- `event_commands` provides command idempotency and records the affected stream and positions.
- `domain_events` is append-only and enforces unique event IDs and stream versions.
- Append compares the caller's expected version with the locked current version.
- A mismatch returns an optimistic-concurrency error; the service may reload and retry a bounded
  number of times.
- Database roles used by the application receive no event `UPDATE` or `DELETE` workflow.
- Event reads use global position ascending and bounded limits.

## 9. Projection

`catalog_products` stores canonical JSON plus indexed product name, brand, and last global
position. `ProductImported` replaces product/composition/evidence while retaining later offer
observations. `OfferObserved` replaces the matching offer ID. Reapplying the same or older global
position is a no-op.

This loop does not infer that a previously observed offer disappeared merely because a later
provider snapshot omitted it. Such offers retain their original observation time and must not be
presented as live after the freshness policy expires. Explicit offer-withdrawal/tombstone events
belong to the synchronization follow-up loop.

Rebuild truncates only projection tables, replays all events in global-position order, and produces
the same canonical JSON. It never edits the event log.

## 10. API

- `POST /api/v1/retailers/picnic/products/{id}/imports`: Picnic-backed import command.
- `GET /api/v1/catalog/products?query=&limit=`: persisted projection search.
- `GET /api/v1/catalog/products/{id}`: persisted projection detail.
- `GET /api/v1/retailers/picnic/products?query=&limit=`: explicitly transient Picnic discovery.
- `GET /api/v1/events?after=&limit=`: ordered sync envelopes, default cursor zero.
- `GET /health/live`: process liveness.
- `GET /health/ready`: database readiness.

Validation is `400`, missing provider or projected products are `404`, command/stream conflicts are
`409`, unavailable dependencies are redacted `503`, and unexpected failures remain redacted.

## 11. Migrations and PostgreSQL

- SQL migrations are classpath resources applied by a small dedicated migration runner.
- `schema_migrations` records immutable version/name/checksum triples.
- A changed checksum for an applied migration fails startup.
- Each pending migration runs transactionally and is recorded only after success.
- PostgreSQL is pinned to `18.4-alpine`, the current supported minor release on 2026-08-04.
- Local credentials are development-only values in Compose; production secrets come from runtime
  configuration.

## 12. Native Image and Dependencies

Versions verified from official project releases on 2026-08-04:

| Component | Version | Decision |
|---|---:|---|
| [GraalVM Community / Native Image](https://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.2.4) | 25.2.4 / JDK 25.0.4 | newest stable feature release and official multi-arch image |
| [GraalVM Native Build Tools](https://github.com/graalvm/native-build-tools/releases/tag/1.1.6) | 1.1.6 | newest stable plugin |
| [PostgreSQL](https://www.postgresql.org/docs/release/18.4/) | 18.4 | newest current minor release |
| [pgJDBC](https://github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.13) | 42.7.13 | newest stable driver; includes current PostgreSQL 18 coverage |
| [HikariCP](https://github.com/brettwooldridge/HikariCP/releases/tag/HikariCP-7.1.0) | 7.1.0 | newest stable pool |
| [Testcontainers](https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.5) | 2.0.5 | newest stable Java release |
| Kotlin, Ktor, serialization, coroutines | repository versions | already selected two days earlier; no incidental upgrade |

Kotlin/JVM bytecode remains targeted at Java 17 as required by repository policy. Native Image uses
the newer JDK only as its build/runtime toolchain. Ktor switches to CIO because the official Ktor
[native-image documentation](https://ktor.io/docs/graalvm.html) requires CIO. No beta or
release-candidate dependency is used.

Dependency discovery is repeated only for a scoped upgrade, compatibility change, or security fix.
That loop checks official release pages, updates exact pins, runs all event/database tests, builds
the native image, and starts it with Compose. A new release does not interrupt ordinary feature
work. A beta or RC requires a documented stable-version blocker and migration trigger; alpha,
nightly, milestone, and snapshot versions still require explicit approval.

## 13. Configuration and Containers

- Database: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, pool-size/timeouts.
- Provider: optional `PICNIC_ENV_FILE`; provider endpoints report unavailable when omitted.
- HTTP: host/port retain safe loopback defaults outside containers.
- Docker builds the native executable on the target architecture and copies it into a non-root
  runtime image.
- Compose waits for PostgreSQL health before starting the service and exposes only local ports.
- No credentials or provider capture files are copied into an image.

## 14. Testing and Verification

- Event ID/envelope and event serialization tests on configured core targets.
- Pure catalog projection reducer tests.
- Migration first-run, repeat-run, and checksum tests.
- Append ordering, expected-version conflict, duplicate command, duplicate event, and cursor tests.
- Projection atomicity and deterministic rebuild tests.
- Ktor component test: fake provider -> command -> real PostgreSQL event store -> projection -> HTTP
  query/event-feed response.
- Docker Compose PostgreSQL integration test without live provider credentials.
- Native executable build and startup/readiness smoke against Docker PostgreSQL.
- Full repository JVM, iOS Simulator, Wasm, line-count, and coverage gates.

Exact commands:

```shell
./gradlew :core:events:allTests :integration:postgres:test :apps:backend:test lineCountCheck
./gradlew check
docker compose up --build --wait
docker compose down --volumes
```

## 15. Completion Criteria

- [x] Event contracts compile and serialize on every configured core target.
- [x] Import commands emit versioned product and offer events.
- [x] PostgreSQL append is ordered, optimistic, idempotent, and atomic with projection updates.
- [x] Search/detail query PostgreSQL projections rather than live provider state.
- [x] Event cursor reads resume deterministically and ignore no stored events.
- [x] Projections rebuild to identical state.
- [x] Migrations are ordered and checksum protected.
- [x] Component and integration tests use PostgreSQL in Docker.
- [x] A GraalVM native image builds and passes a container readiness smoke test.
- [x] Runtime images contain no provider or database credentials.
- [x] Documentation and version guidance are current.

The completed native image is 45.94 MiB on the local arm64 build, runs as the non-root `grocery`
user, applies migration V1 to a clean PostgreSQL 18.4 volume, and passes liveness, database
readiness, empty persisted-search, empty cursor-feed, validation-error, and unavailable-provider
responses. PostgreSQL exposes two triggers that reject event update/delete and truncate operations.

## 16. Decisions

- Use explicit SQL/JDBC instead of an ORM so event ordering and transaction boundaries stay visible.
- Use HikariCP for bounded production connections; inject a data source in tests.
- Use a dedicated migration runner instead of adding Flyway because the required ordered,
  checksum-protected migration scope is small and this avoids another reflection-heavy native
  dependency.
- Keep one catalog microservice in `apps/backend`; service extraction by bounded context waits until
  a second independently deployable backend context exists.

## 17. Next Loop

Add authenticated frontend event synchronization with per-device cursors, schema negotiation,
offline client writes where appropriate, pushed invalidation, reconnect recovery, tombstones, and
deterministic conflict rules.
