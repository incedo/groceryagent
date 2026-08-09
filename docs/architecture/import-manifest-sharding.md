# Import Manifest Sharding

**Status:** SATISFIED
**Last updated:** 2026-08-09
**Depends on:** Batch product importer, Picnic order capture import, historical order prices

## 1. Overview

Large reviewed import manifests must be split into small deterministic manifests so operators can
run bounded, resumable importer Jobs without increasing Picnic request concurrency. Sharding is an
offline operation over private local data and does not start an import.

## 2. Scope

### In scope

- Split one schema-version 1 manifest by positive product-count and byte-size ceilings.
- Preserve product and historical-observation order.
- Assign every historical observation to exactly one included product.
- Write non-overlapping manifests with deterministic batch IDs.
- Write an index containing counts and SHA-256 checksums for the source and every shard.
- Refuse overwrites and retain owner-only permissions where the filesystem supports them.
- Document sequential, explicit Kubernetes Job execution.

### Out of scope

- Starting importer Jobs, calling Picnic, or writing PostgreSQL data.
- Parallel importer execution, pacing inside a product shard, or automatic retries.
- Storing private manifests in Git, ConfigMaps managed by Git, or public object storage.
- Changing catalog events, projections, importer idempotency, or manifest schema version 1.

## 3. Current State

- `apps/importer` accepts one file through `IMPORT_MANIFEST_FILE` per process.
- Product commands are idempotent for an unchanged batch ID and product.
- Historical observations are idempotent by observation ID.
- The importer is sequential but does not delay between Picnic product requests.
- Large order-history manifests exceed the Kubernetes ConfigMap boundary.

## 4. Target Architecture

```text
reviewed private manifest
  -> offline manifest splitter
  -> deterministic shard files plus index
  -> operator verifies checksums and ordering
  -> one explicit suspended-CronJob-derived Job per shard
  -> existing command/event/projection path
```

The source manifest remains the source of truth for splitting. Shards do not change event payloads
or projection behavior.

## 5. Models and IDs

| Value | Rule | Notes |
|---|---|---|
| shard batch ID | `<source-batch>-part-NNN` | Stable for the same ordered split |
| shard filename | `manifest-part-NNN.json` | Lexical order is execution order |
| checksum | lowercase SHA-256 | Calculated over exact file bytes |
| index | `manifest-index.json` | Contains source/shard counts and checksums |
| byte ceiling | 900,000 by default | Leaves room below the ConfigMap limit |

Picnic historical observations must reference the canonical ID formed from their retailer, region,
and the provider product ID listed in the source manifest. Unmatched observations fail closed.

## 6. Events and Projections

No event, schema, projection, API, or sync changes are introduced. The existing importer emits the
same events when an operator later runs a shard.

## 7. Decision Rules

- The maximum product count is positive.
- The maximum byte size is positive and no shard exceeds it.
- A single product with too much history to fit fails before output is created.
- The output directory is new and private/local.
- Products occur in exactly one shard and retain source order.
- Historical observations retain source order within the shard of their product.
- Empty source product lists remain invalid under manifest schema version 1.
- Existing output files or directories are never overwritten.

## 8. Provider and Safety Policy

Splitting performs no provider requests. Product shards must be run sequentially because the
current importer has no inter-request delay. `history-only` may run without Picnic credentials,
but operators still run one named Job at a time to preserve an auditable order.

## 9. Testing and Verification

Deterministic tests cover shard boundaries, order, price assignment, checksums, stable batch IDs,
unmatched observations, invalid sizes, and overwrite refusal.

Exact checks:

```shell
./gradlew :apps:importer:test lineCountCheck
git diff --check
```

## 10. Completion Criteria

- [x] A CLI command creates deterministic shards and an index without provider/database access.
- [x] Products do not overlap and historical prices are assigned without loss or duplication.
- [x] Exact source and shard checksums are recorded and tested.
- [x] Private output is never overwritten.
- [x] The CronJob runbook documents generation, verification, and sequential execution.
- [x] Targeted tests and line-count checks pass.

## 11. Open Questions

None for this bounded loop.

## 12. Next Loop

Add explicit per-request pacing and retry policy before any protected Picnic account performs a
product import.
