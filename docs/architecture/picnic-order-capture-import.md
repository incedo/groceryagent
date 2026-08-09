# Picnic Order Capture to Product Import

**Status:** SATISFIED
**Last updated:** 2026-08-09
**Depends on:** Picnic client; Batch product importer

## 1. Overview

Allow an operator to use a separate Picnic account to fetch completed-order data read-only, retain
the raw responses temporarily as local text, extract Picnic product identifiers, and generate a
product-import manifest for the existing event-driven importer.

```text
second-account auth.env
  -> POST /deliveries/summary ["COMPLETED"]
  -> GET /deliveries/{id} compatibility candidate for each returned delivery
  -> ignored local JSON capture
  -> provider-specific product-id extraction
  -> reviewed import manifest
  -> existing ProductImported + OfferObserved pipeline
```

## 2. Scope

### In scope

- A separate `PICNIC_ENV_FILE` for the other account; no login or credential copying.
- Read-only delivery summary and detail requests.
- A new, non-overwriting local capture directory containing pretty-printed raw JSON text.
- Provider-specific extraction of direct product-ID fields matching Picnic's `s` + digits shape.
- Deduplication preserving first-seen order and generation of schema-version 1 import manifests.
- Deterministic synthetic structural tests without real order or account data.

### Out of scope

- Persisting orders, account identity, address, slot, payment, invoice, or order totals in the
  Grocery Automate database.
- Committing raw captures, provider tokens, device IDs, delivery IDs, or shopping history.
- Placing, confirming, changing, rating, emailing, or cancelling an order.
- Treating the current raw delivery JSON as an agreed canonical order model.
- Automatic product import in the same capture command. The operator reviews the generated list
  and invokes the existing importer explicitly.
- Recipe import or inference from order contents.

## 3. Privacy and Local Storage

Order history is private data. Repository-local captures must live below `.local/`, which is
ignored. The tool refuses an existing capture directory rather than overwriting it and attempts to
apply owner-only directory/file permissions on POSIX systems. Console output contains counts and
local paths, never tokens, response bodies, delivery identifiers, address data, or product names.
Only a fully completed summary/detail capture receives `capture-complete.json`; manifest generation
fails closed when that marker is absent.

Raw files are temporary operator-owned snapshots. Deleting them after the import manifest has been
reviewed is the retention policy. They are not event sources and are never uploaded by CI.

## 4. Provider Contract and Fail-Closed Extraction

The established current route is `POST /deliveries/summary`. The existing read-only
`GET /deliveries/{id}` client operation remains an unverified compatibility candidate until this
other account supplies a successful live response; an unsupported response leaves only a partial
local capture and produces no manifest. Each top-level summary object may expose its delivery
identifier as `id`, `delivery_id`, or `deliveryId`; only these direct fields are used to request
details.

Detail JSON is traversed recursively. A value is a candidate only when:

- its direct field name is `id`, `product_id`, `productId`, `article_id`, `articleId`,
  `selling_unit_id`, or `sellingUnitId`; and
- its complete string value matches `^s[0-9]+$`.

No product is inferred from names, prices, images, arbitrary text, or embedded expressions. Zero
delivery IDs or zero product candidates fails visibly and produces no import manifest. This keeps
unknown provider shapes from silently creating incorrect catalog facts.

## 5. Commands

```shell
PICNIC_ENV_FILE=/absolute/path/second-account.env \
./gradlew :apps:importer:run --args='--capture-orders .local/orders/account-2-YYYYMMDD'

./gradlew :apps:importer:run \
  --args='--orders-to-manifest .local/orders/account-2-YYYYMMDD .local/import-account-2-YYYYMMDD.json account-2-orders-YYYYMMDD'
```

The resulting manifest is passed unchanged to the batch product importer. A new batch ID requests
fresh product and offer observations; reusing it resumes idempotently.

## 6. Verification

- Summary extraction covers supported delivery-ID field variants and rejects nested ambiguity.
- Detail extraction covers nested articles, duplicates, unrelated IDs, and malformed candidates.
- Capture storage refuses overwrite and writes parseable JSON.
- Manifest generation fails on empty captures and writes only deduplicated product IDs.
- Existing importer tests prove event append and PostgreSQL projection behavior.

Exact commands:

```shell
./gradlew :integration:picnic-client:allTests :apps:importer:test lineCountCheck
./gradlew check
git diff --check
```

## 7. Completion Criteria

- [x] The other account is selected only through an explicit local auth-file path.
- [x] Capture performs read-only summary/detail requests and never calls a mutation.
- [x] Raw private responses remain in ignored local text files.
- [x] Extraction is field- and shape-constrained, deterministic, and deduplicated.
- [x] Empty, partial, or unknown provider shapes fail closed before manifest creation.
- [x] The generated manifest works with the existing product event pipeline.
- [x] Documentation and tests cover safe operation and deletion/retention expectations.

The complete repository gate passed locally. A native, non-root importer image converted a
synthetic completed capture into a two-product manifest. No live account or order response was
used; the first second-account run remains an explicit operator action because the repository has
no auth file for that account.

## 8. Next Loop

Capture a deliberately sanitized, minimal structural derivative from an account with past orders,
then define typed provider delivery/order models only after confirming the live current response
shape. Canonical order-history storage remains a separate privacy-reviewed decision.
