# Product Image Import

**Status:** AGREED
**Last updated:** 2026-08-10
**Depends on:** Batch product importer, native event-sourced backend, Picnic client

## 1. Overview

Import the primary Picnic product image for products already projected in PostgreSQL. The one-shot
importer reads bounded candidates from the catalog projection, downloads one `large` PNG at a time,
stores the bytes in homelab MinIO, and records the durable product-to-object relationship as a
catalog domain event.

## 2. Scope

### In scope

- PostgreSQL-driven candidate selection for products with a non-blank `imageId`.
- Sequential, bounded, resumable imports with the existing provider delay.
- Content validation, SHA-256 deduplication, and storage in `grocery-product-images`.
- `ProductImageStored` v1 events and a rebuildable `product_image_assets` projection.
- An explicit, suspended-CronJob-derived `product-images` Job on homelab.

### Out of scope

- Automatic scheduling, parallel downloads, retries by Kubernetes, or downloading all five sizes.
- Moving historical order payloads into object storage.
- Image transformations, thumbnails, deletion, lifecycle policies, or UI rendering.
- Treating the Picnic image identifier as a canonical or public asset identifier.

## 3. Current State

- `Product.imageId` stores the primary provider image identifier.
- `PicnicCatalogPort.getImage` fetches PNG bytes from
  `/static/images/{imageId}/{size}.png` with the storefront asset request policy.
- PostgreSQL currently projects 1,371 products with 1,370 unique non-null image identifiers.
- No product-image object mapping or S3 credentials exist in the grocery Kubernetes namespace.

## 4. Target Architecture

```text
explicit product-images Job
  -> PostgreSQL candidates missing a current large image asset
  -> Picnic large PNG bytes (sequential and paced)
  -> validate PNG and calculate SHA-256
  -> idempotent MinIO put under images/sha256/{prefix}/{digest}.png
  -> StoreProductImage command decision
  -> ProductImageStored v1 event in product:{productId}
  -> product_image_assets PostgreSQL projection
```

The append-only event store is the source of truth for the product/object relationship. MinIO is
the source of truth for the immutable image bytes. A crash between object upload and event append
is safe: a retry uploads the same content-addressed key and then appends the missing event.

## 5. Canonical Models and IDs

| Value | Type | Validation | Notes |
|---|---|---|---|
| Product ID | `ProductId` | Existing stable ID | Event stream identity |
| Source image ID | string | Non-blank | Picnic provenance only |
| Variant | `ProductImageVariant` | `LARGE` in this loop | Extensible without changing keys |
| SHA-256 | lowercase hex string | Exactly 64 hex characters | Canonical content identity |
| Object key | string | `images/sha256/{2 chars}/{digest}.png` | Immutable and retailer-neutral |
| Public URL | string | HTTPS base plus bucket and object key | Convenience read model value |

`ProductImageAsset` preserves product ID, provider, source image ID, variant, media type, byte
size, digest, bucket, object key, public URL, and observed timestamp.

## 6. Commands

| Command | Fields | Decision inputs | Emits |
|---|---|---|---|
| `StoreProductImage` | product, provider image, immutable object metadata | current asset for source ID and variant | `ProductImageStored` or no-op |

The importer uses a deterministic command ID derived from product ID, source image ID, variant,
and digest. Replaying the same command is idempotent.

## 7. Events

| Event | Stream | Payload | Trigger |
|---|---|---|---|
| `ProductImageStored` v1 | `product:{productId}` | complete `ProductImageAsset` snapshot | validated object is present in MinIO |

Normal envelope metadata supplies event ID, producer ID, correlation/causation, stream version,
global cursor, and occurrence time.

## 8. Decision Models and Invariants

- The product and source image IDs must be non-blank.
- Only non-empty PNG content up to 10 MiB is accepted; the PNG signature is checked.
- The digest is calculated from the downloaded bytes and determines the immutable object key.
- A projected asset for the same product, source image ID, and variant is skipped.
- Different products may reference the same content-addressed object.

## 9. Projections and Frontend State

`product_image_assets` is keyed by product and variant. New events replace that row only when their
global position is newer. Projection rebuild clears the table and deterministically replays every
event. UI/API exposure is a later loop; no second mutable catalog source is introduced.

## 10. Sync

The event uses the existing catalog envelope, ordering, cursor, append idempotency, duplicate
command handling, schema checking, and reconnect semantics. Object bytes are not embedded in event
sync. Clients use the projected immutable HTTPS URL. Deletion/tombstones are out of scope.

## 11. Provider Data

- Provider: Picnic Netherlands storefront assets; the existing authenticated environment is used.
- Region and provenance: provider name, source image ID, variant, and observation time are retained.
- Fetch policy: one request at a time with `PICNIC_REQUEST_DELAY_MILLIS` between candidates.
- Failures are logged per product using identifiers and categories, never credentials or bodies.
- Provider licensing and long-term image redistribution must be reviewed before public exposure.

## 12. Storage and Operations

- Endpoint: `https://minio.home.intelliworks.nl`.
- Bucket: `grocery-product-images`; it must already exist.
- Public base: `https://assets.home.intelliworks.nl`.
- Kubernetes secret: `grocery-automate-product-images` with keys `S3_ACCESS_KEY`,
  `S3_SECRET_KEY`, and `S3_REGION`; values are never committed.
- The importer requires explicit `IMPORT_MODE=product-images` and `IMAGE_IMPORT_LIMIT` (maximum
  50). The checked-in CronJob remains suspended and keeps `backoffLimit: 0`.

## 13. S3 Client Decision

- The concrete object-storage integration uses a small AWS Signature Version 4 HTTPS PUT adapter.
- It reuses JDK 17 HTTP and cryptography APIs already supported by the native importer image.
- The MinIO Java SDK was removed after homelab smoke testing showed its PUT path was not compatible
  with this GraalVM native image, while the same credentials, endpoint, and object worked on the JVM.

## 14. Testing

- Core tests: validation, deterministic command/event IDs, duplicate/no-op decisions, codec.
- PostgreSQL tests: candidate selection, projection update, duplicate ordering, rebuild.
- Object-store tests: key/content metadata, deterministic Signature Version 4 headers, HTTP PUT,
  and non-success handling against a fake HTTP boundary.
- Importer tests: settings, bounded sequential behavior, skip, failure continuation, pacing.
- Live tests remain opt-in and never run in CI.

Exact verification:

```text
./gradlew :core:events:allTests :integration:postgres:test \
  :integration:object-storage:test :apps:importer:test lineCountCheck --no-daemon
./gradlew check --no-daemon
docker build -f Dockerfile.importer -t grocery-catalog-importer:product-images .
```

## 15. Completion Criteria

- [ ] Event, command, codec, projection, and rebuild behavior are implemented and tested.
- [ ] Candidate selection is bounded to 50 and excludes the current stored variant.
- [ ] MinIO uploads use immutable content-addressed keys and secret-only credentials.
- [ ] The importer runs sequentially, remains operator-triggered, and logs per-product outcomes.
- [ ] Homelab manifests and the CronJob runbook document the explicit image job.
- [ ] CI passes before merge; a bounded live smoke run succeeds before larger batches.

## 16. Open Questions

- **Q-1:** Which image size? — **Decision:** `large`; thumbnails are a later loop.
- **Q-2:** Is the Picnic ID public identity? — **Decision:** No, it remains provider provenance.
- **Q-3:** Is the assets domain bucket-rooted? — **Decision:** No. It routes directly to the S3 API,
  so public URLs include `/grocery-product-images/{objectKey}`.

## 17. Next Loop

- Next spec: Product image API and adaptive UI rendering.
- Reason: expose projected assets with fallback/loading behavior after storage import is proven.
