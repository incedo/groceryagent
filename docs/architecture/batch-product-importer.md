# Batch Product Importer

**Status:** SATISFIED
**Last updated:** 2026-08-09
**Depends on:** Catalog and retailer route boundaries; Native event-sourced catalog backend

## 1. Overview

Add a separately deployable, one-shot importer next to the catalog service. An operator supplies a
versioned JSON manifest containing Picnic product identifiers. The importer fetches canonical
details through the existing provider adapter and appends the same `ProductImported` and
`OfferObserved` events as the HTTP import command. PostgreSQL events remain the source of truth;
the catalog projection is updated in the append transaction.

## 2. Scope

### In scope

- A JVM/native `apps/importer` entry point that runs to completion and exits.
- A validated JSON manifest with a batch ID, producer ID, and product list.
- Stable per-batch command IDs so restarting the same manifest is idempotent.
- Sequential provider reads to avoid uncontrolled retailer load.
- Per-product results and a failing process exit when any item cannot be imported.
- A private importer image and opt-in Compose deployment profile.

### Out of scope

- Scheduling, a web UI, concurrent imports, discovery, and automatic product-list generation.
- Storing raw Picnic page/layout JSON.
- Recipe ingestion. The current Picnic recipe boundary exposes raw layout JSON and has no agreed
  canonical recipe mapper. Product events introduced here are suitable future ingredient/product
  references; recipe models, events, projection, licensed source mapping, and ingestion require a
  separate agreed loop.
- Changing product, offer, event, or projection schemas.

## 3. Architecture and Contracts

```text
operator-owned JSON manifest
  -> importer manifest validation
  -> stable command ID(batch ID + retailer + provider product ID)
  -> shared ProductImportService
  -> Picnic ProductCatalogPort
  -> ProductImported + OfferObserved
  -> PostgreSQL event store + catalog_products projection
```

`apps/importer` and `apps/backend` are composition roots. The reusable product-import use case
lives in `core/events`; it depends only on core catalog and event ports. Provider and PostgreSQL
adapters remain in `integration/*`.

The manifest fields are:

| Field | Rule |
|---|---|
| `schemaVersion` | exactly `1` |
| `batchId` | non-blank operator-selected snapshot/run identity |
| `producerId` | non-blank event producer identity |
| `products[].retailer` | `picnic` in this loop |
| `products[].productId` | non-blank provider product identifier; unique per retailer |

Changing `batchId` deliberately creates new import commands and therefore new observations.
Reusing it safely resumes the same batch. Each item is processed sequentially. A missing product
or provider/storage failure is reported without provider payloads or credentials; remaining items
continue so the final report is complete.

`IMPORT_BATCH_ID` may override the file value at deployment time. The homelab CronJob uses its
stable generated Job name: retries within one Job stay idempotent, while a later scheduled Job
creates fresh observations.

## 4. Events, Storage, and Safety

- Existing event types and schema version 1 remain unchanged.
- The deterministic command ID is used only for idempotency; event IDs remain random UUIDs.
- Existing optimistic stream concurrency retries remain bounded at three attempts.
- `domain_events` remains immutable and `catalog_products` remains rebuildable.
- Composition, allergen unknown state, price, quantity, region, freshness, and provenance come only
  from the canonical Picnic mapper; the importer invents no grocery facts.
- The manifest contains identifiers only and must not contain credentials or raw provider data.
- Ordinary CI validates a fixture manifest and uses fake providers; live imports remain opt-in.

## 5. Deployment

- `Dockerfile.importer` builds one native `grocery-catalog-importer` binary.
- Compose exposes an opt-in `import` profile and mounts the manifest and Picnic environment file
  read-only. It is not started with the long-running catalog service by default.
- CI smoke-validates the exact importer image before publishing it only to
  `registry.home.intelliworks.nl:5000/grocery-automate/catalog-importer` on the homelab runner.
- Operators run a manifest explicitly; there is no automatic public-registry fallback.

## 6. Verification

Deterministic tests cover manifest validation, stable command IDs, duplicate batch resume, missing
products, continued processing after failure, emitted events, and projection/database behavior
through the existing repository tests.

Exact commands:

```shell
./gradlew :core:events:allTests :apps:importer:test :apps:backend:test \
  :integration:postgres:test lineCountCheck
./gradlew check
docker compose config --quiet
docker build -f Dockerfile.importer -t grocery-catalog-importer:test .
docker run --rm grocery-catalog-importer:test \
  --validate-manifest /app/import-products.example.json
git diff --check
```

## 7. Completion Criteria

- [x] A manifest is validated before provider or database work starts.
- [x] The same batch can resume without duplicate events.
- [x] Each successful product emits canonical product/offer events and updates the projection.
- [x] Failures are redacted, isolated per item, and result in a non-zero final exit.
- [x] The importer is opt-in and separately deployable.
- [x] Tests and native container validation pass.
- [x] README, module layout, CI/CD, and deployment documentation are current.

The complete repository gate passed locally. The PostgreSQL component test persisted and rebuilt
two events without skips, and the non-root native image validated the checked-in manifest fixture.
The resulting local arm64 image is 47,218,221 bytes including the Debian runtime layer.

## 8. Next Loop

Define `core/recipes`, typed provider recipe mapping, license/freshness rules, recipe import events,
and a rebuildable recipe projection before extending the manifest with recipe entries.
