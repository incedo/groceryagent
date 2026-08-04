# Picnic Kotlin Client

Status: `SATISFIED`

## Goal and Compatibility Baseline

Provide a Kotlin Multiplatform client that can replace the public service and request surface of [`MRVDH/picnic-api`](https://github.com/MRVDH/picnic-api) as the first Picnic provider adapter for Grocery Automate.

The audited community baseline is upstream version `4.6.0`, commit `fe89231b35a4fb13fd63ba6d3fb2b424d036bc87` from 2026-07-02. The client retains all 12 upstream service groups and can issue every request represented there. Operations with newer app evidence may deliberately use a discovery-backed route instead of the legacy community route. The current discovery baseline is Picnic Android 1.239.3 at `incedo/api-discovery-picnic` commit `aacd26b4d30f6004445fb058541b76648aa7d9ca`.

“Complete” in the original loop means public operation coverage, not exhaustive layout-tree modeling. Catalog search and product details now use typed provider objects backed by discovery-shaped mappers. Other dynamic Picnic Fusion/PML responses remain `JsonElement` until their own bounded object-model loop. Explicit page and raw methods preserve access to unmodeled provider data.

Current-provider completeness is tracked separately in [Picnic Contract Reconciliation](picnic-contract-reconciliation.md). That comparison distinguishes captured request validity, wire-response behavior, typed provider objects, and legacy route-surface compatibility. In particular, the 65-route endpoint-parity test must not be interpreted as proof that all legacy routes remain accepted by the current provider.

Where both current and legacy wire contracts exist, the input port exposes one capability and one typed Picnic provider model. The current captured route is preferred. A legacy adapter may map its distinct response into that same model under the fallback rules in the reconciliation spec. State-changing requests are never automatically sent twice after an ambiguous or failed first attempt.

This policy is implemented for login destination compatibility and for typed catalog search and product details. Catalog provenance records `CURRENT` or `LEGACY`; callers receive the same `PicnicSearchResult` or `PicnicProductDetails` either way. The Ktor production adapter, common fake transport, JVM, iOS Simulator, and Wasm tests cover the behavior. Other endpoint families adopt fallback only after they expose an agreed typed provider model.

## Architecture

The module follows hexagonal architecture:

```text
application / feature code
          |
          v
Picnic service input ports
          |
          v
application services + provider rules
          |
          v
HTTP | auth-store | clock | password-hasher output ports
          |
          v
Ktor | secure storage | system/fake adapters
```

- `application/port/in` defines the capabilities callers use.
- `application/service` implements Picnic operations and response extraction.
- `application/port/out` defines dependencies controlled by the composition root.
- `adapter/out` contains Ktor, memory, clock, and hashing adapters.
- `domain` contains provider-specific configuration and typed convenience values.
- `PicnicClient` is a façade that wires the hexagon; it is not a domain model.

Features must not import the concrete Ktor adapter or Picnic payload models. A future `integration/picnic-catalog-adapter` maps Picnic data into core `Product`, `ProductOffer`, freshness, and provenance models.

## Supported Targets and Initial Versions

The initial stack selected the newest stable compatible releases available when this module was established:

| Component | Initial version | Purpose |
| --- | ---: | --- |
| Kotlin | 2.4.10 | Multiplatform language and build plugin |
| Gradle | 9.6.1 | Reproducible wrapper build |
| Ktor client core | 3.5.1 | Replaceable multiplatform HTTP adapter |
| kotlinx.coroutines | 1.11.0 | Suspending APIs and deterministic tests |
| kotlinx.serialization | 1.11.0 | Dynamic JSON and typed request data |
| JDK toolchain | 17 | JVM compilation baseline |

These are initialization choices, not a mandate for continuous incidental upgrades. After establishment, upgrades are separate scoped work unless a feature, compatibility need, or security fix requires one.

Compilation targets are JVM, iOS ARM64, iOS Simulator ARM64, and Wasm JS. The consuming application chooses the matching Ktor engine. Direct browser calls may be restricted by Picnic CORS policy, so production Wasm integration should normally use a Grocery Automate backend adapter rather than expose provider authentication to the browser.

## Compatibility Matrix

| Upstream service | Kotlin input port | Covered operations |
| --- | --- | --- |
| app | `PicnicAppPort` | bootstrap, page, deeplink resolution |
| auth | `PicnicAuthPort` | login, second factor, phone verification, logout, auth state |
| cart | `PicnicCartPort` | read/add/remove/clear, slots, order status/confirm, minimum value |
| catalog | `PicnicCatalogPort` | typed current search and product details; raw suggestions/pages; images/data URI |
| consent | `PicnicConsentPort` | settings, topic consent, general consent |
| content | `PicnicContentPort` | FAQ and empty-search content |
| customer service | `PicnicCustomerServicePort` | contacts, messages, reminders, parcels |
| delivery | `PicnicDeliveryPort` | deliveries, position, scenario, cancel, rating, invoice email |
| payment | `PicnicPaymentPort` | profile and wallet transactions/details |
| recipe | `PicnicRecipePort` | discovery, cookbook, details, save, basket and portions |
| user | `PicnicUserPort` | details, info, profile, suggestions, captured push contract, current-first update check |
| user onboarding | `PicnicUserOnboardingPort` | household, business, push topics |
| custom requests | `PicnicRawPort` | arbitrary method/path/data with optional Picnic headers |

The contract tests verify destination selection, automatic Picnic headers, complete current login metadata, current-first and legacy fallback paths, mapping equivalence, methods, paths, current search-session parameters, request bodies, MD5 password compatibility, auth-token rotation, image encoding, typed catalog mapping, dietary unknown handling, provider-error redaction, and zero automatic mutation replay. They use fake or Ktor `MockEngine` transports and never consume provider quotas.

## Security and Operational Rules

- Login credentials, auth keys, raw private profiles, and precise delivery locations must never be logged or committed.
- The included in-memory auth store is the safe default for tests and short-lived processes. Apps that persist sessions supply a platform secure-storage adapter.
- Provider error messages may be surfaced, but full response bodies are not placed in exception messages.
- Live tests require explicit opt-in and credentials outside the repository.
- Mutating live operations, including cart, order, delivery, consent, and account changes, require explicit user authorization and must not run in ordinary CI.
- Picnic is an unofficial external API and can change without notice. Preserve response provenance and fail visibly when required data is missing.

## Verification and Completion Criteria

This loop is satisfied when:

- every upstream service group and request call site at the pinned revision has a Kotlin operation;
- the service layer depends only on output ports;
- deterministic request-contract tests pass on JVM;
- shared production code compiles for JVM, iOS Simulator ARM64, and Wasm JS;
- all touched Kotlin and Gradle source remains at or below 300 lines;
- licensing and known compatibility limits are documented.

Required commands:

```shell
./gradlew :integration:picnic-client:jvmTest lineCountCheck
./gradlew :integration:picnic-client:iosSimulatorArm64Test :integration:picnic-client:wasmJsBrowserTest
```

## Out of Scope and Next Loops

- Live account verification is excluded because it needs private credentials and may mutate a real Picnic account.
- Canonical Grocery Automate mapping is a follow-up adapter, not part of the provider client.
- Exhaustive typed models for dynamic Fusion/PML components are intentionally avoided. Add stable provider objects incrementally with synthetic or sanitized, license-compatible fixtures.
- Backend proxying, event ingestion, source snapshots, projections, and frontend synchronization form the next end-to-end loop.
- Request-host/header drift, current payload alignment, HTTP outcome modeling, typed clean responses, and legacy-route disposition are bounded as PCR-001 through PCR-006 in [Picnic Contract Reconciliation](picnic-contract-reconciliation.md).
