# Historical Picnic Order Prices

**Status:** SATISFIED
**Last updated:** 2026-08-09
**Depends on:** `picnic-order-capture-import.md`, `batch-product-importer.md`

## 1. Overview

Preserve prices embedded in completed Picnic orders as immutable historical observations so price
changes can be queried without treating an old paid price as a live offer. Raw orders remain local;
only canonical, non-personal price facts enter the backend event store.

## 2. Scope

### In scope

- Extract single-product `ORDER_LINE` observations from completed-order JSON.
- Preserve paid line total, original line total, item count, currency, purchase time, retailer,
  region, provider product ID, package text, promotion label, and an opaque source hash.
- Emit a versioned `HistoricalPriceObserved` event through an idempotent command.
- Project observations into PostgreSQL and expose product price history through the backend API.
- Keep integer minor units; never round a non-divisible line total into a unit price.

### Out of scope

- Importing raw order, account, address, delivery, or payment data.
- Assigning a combined line price to one product when the line contains multiple product IDs.
- Currency conversion, inflation adjustment, charts, recommendations, and automatic scheduled runs.
- Starting the live product importer or changing its retailer request rate.

## 3. Current State

- `PicnicOrderReferenceExtractor` extracts product IDs but not price observations.
- `OfferObserved` represents current retailer offers and replaces the current offer projection.
- PostgreSQL stores catalog events and a current `catalog_products` projection only.
- Captured `ORDER_LINE.price` is a line total. A `PRICE` decorator can contain the actual
  promotional line total. `QUANTITY` supplies the ordered item count.

## 4. Target Architecture

```text
private completed-order JSON
  -> deterministic historical-price extraction
  -> import manifest with canonical observations
  -> RecordHistoricalPrice command
  -> HistoricalPriceObserved event
  -> append-only PostgreSQL event store
  -> product_price_history projection
  -> GET /api/v1/catalog/products/{id}/price-history
```

The event store is the source of truth. The read model is rebuildable from event envelopes.

## 5. Canonical Models and IDs

| Value | Validation | Notes |
|---|---|---|
| `HistoricalPriceObservationId` | non-blank SHA-256 hex | Derived from provider/order/line identity; raw IDs are not stored |
| `ProductId` | canonical `picnic:nl:{providerId}` | Joins history to products when product details exist |
| `paidLineTotal` | non-negative integer minor units | Actual paid product-line total |
| `originalLineTotal` | optional non-negative integer minor units | Pre-promotion total when different |
| `quantity` | positive integer | Ordered count; line total remains authoritative |
| `purchasedAt` | provider timestamp | Historical observation time, not live freshness |

## 6. Commands

| Command | Fields | Emits |
|---|---|---|
| `RecordHistoricalPrice` | observation, stable command ID, producer | `HistoricalPriceObserved` |

## 7. Events

| Event | Stream | Payload | Trigger |
|---|---|---|---|
| `HistoricalPriceObserved` v1 | `price-history:{observationId}` | canonical observation | accepted extracted order line |

Stable command and observation IDs make repeats idempotent. Event timestamps use `purchasedAt`.

## 8. Decision Models and Invariants

- Accept only one distinct `s[0-9]+` product ID per order line.
- Require a positive integral quantity and non-negative integer line prices.
- Prefer the `PRICE` decorator's `display_price` as paid total; retain the row price as original
  total only when it differs.
- Do not calculate or persist a rounded unit price. Queries return line totals and quantities.
- Missing or ambiguous product, timestamp, price, or quantity data fails closed for that line.

## 9. Projections and API

| Projection | Source | Query | Rebuild |
|---|---|---|---|
| `product_price_history` | `HistoricalPriceObserved` | product, retailer, chronological observations | delete and replay |

The API returns observations ordered by purchase time and opaque observation ID. A later UI/chart
loop can consume this endpoint without a second mutable source of truth.

## 10. Sync

- Existing event envelopes provide stable IDs, versions, cursors, producer, timestamps, and order.
- Duplicate commands and observation IDs are idempotent.
- Retry reuses the stable command ID; conflicting payload reuse fails.
- Tombstones and offline writes do not apply to immutable provider history in this loop.

## 11. Provider Data

- Provider: Picnic completed-order payloads captured for an authorized account.
- Raw responses and account data stay ignored locally and are never committed.
- Stored provenance identifies Picnic, NL region, purchase timestamp, and opaque source hash.
- Old order prices are historical paid observations and must never be presented as live availability.

## 12. Price Rules

- Currency is `EUR`; all money uses integer cents.
- Comparisons use `paidLineTotal / quantity` as an exact ratio at query/consumer time.
- Promotion handling preserves paid and original line totals plus an optional provider label.
- Package text is retained as provenance; normalized EUR/kg or EUR/l is a later loop.

## 13. Dependency Decisions

- No new dependencies. JVM SHA-256 is confined to the importer app.

## 14. Testing

- Deterministic extraction fixtures cover normal, promotional, multi-quantity, ambiguous, and
  malformed lines.
- Event codec covers the new schema-v1 event.
- PostgreSQL tests cover idempotent storage, chronological queries, and deterministic rebuild.
- Backend route tests cover product filtering and validation.
- Exact verification:

```text
./gradlew :integration:picnic-client:allTests :core:events:allTests \
  :integration:postgres:test :apps:importer:test :apps:backend:test lineCountCheck
./gradlew check
```

## 15. Completion Criteria

- [x] Extraction maps supported order rows without storing personal data.
- [x] Historical prices emit versioned, idempotent events.
- [x] Events persist and rebuild a deterministic PostgreSQL projection.
- [x] Product history is queryable through the backend API.
- [x] Manifest generation contains products plus historical observations.
- [x] Tests cover ambiguity, promotion, quantity, duplicates, ordering, and rebuild.
- [x] Documentation and verification commands are current.

## 16. Open Questions

- None for this bounded slice.

## 17. Next Loop

- Add normalized package-unit price trends and adaptive charts after sufficient historical data is
  imported and provider field stability is measured.
