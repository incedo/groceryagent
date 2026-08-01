# Picnic Catalog Object Model

**Status:** SATISFIED
**Last updated:** 2026-08-01
**Depends on:** `docs/architecture/picnic-client.md`

## 1. Overview

Replace raw Picnic catalog JSON as the normal caller contract with typed Kotlin Multiplatform objects. Picnic's changing server-driven UI remains isolated inside the provider adapter.

## 2. Scope

### In scope

- current app `search-page-root-content` requests;
- typed product summaries and details;
- integer-cent prices, quantity labels and volume-price ranges;
- promotions, ingredients, contains/may-contain allergens, nutrition, preparation and product information;
- provider source metadata and an explicit raw-page escape hatch;
- synthetic discovery-shaped fixtures and cross-platform tests.

### Out of scope

- canonical `core/catalog` and `core/offers` models;
- typed account, cart, delivery, checkout and wallet responses;
- exhaustive modeling of Picnic page layout components;
- live provider calls or committed captures.

## 3. Current State

The client mirrors `MRVDH/picnic-api` 4.6.0. Search calls a superseded page route and returns `List<JsonObject>`. Product details have a partial object model derived from older page assumptions. Validation against app 1.239.3 showed that the old extractor loses the primary name and price and flattens allergen safety states.

## 4. Target Architecture

```text
Picnic HTTP response
  -> raw JsonElement inside integration adapter
  -> discovery-backed catalog mapper
  -> typed PicnicSearchResult / PicnicProductDetails
  -> future canonical grocery adapter
```

The typed provider model is not imported by features. A later adapter maps it into canonical `Product`, `ProductComposition`, `ProductOffer`, money, quantity, freshness and provenance models.

## 5. Model Invariants

- Product IDs are non-blank provider identifiers.
- Prices are nullable integer cents; zero never substitutes for missing data.
- Nutrition decimals use an unscaled integer plus scale, never floating point.
- Allergen `contains` and `mayContain` remain separate.
- Missing allergen sections produce `UNKNOWN`, never an empty-safe assertion.
- Unknown product sections remain available through structured extra information.
- Typed results carry endpoint, country, API version and observation time.
- Raw JSON is returned only by explicitly named raw/page methods.

## 6. Provider Data

- Provider: Picnic private app API, API level 15.
- Discovery baseline: Android app 1.239.3, discovery commit `aacd26b4d30f6004445fb058541b76648aa7d9ca`.
- Community baseline: `MRVDH/picnic-api` 4.6.0, commit `fe89231b35a4fb13fd63ba6d3fb2b424d036bc87`.
- Cross-platform fixtures contain synthetic product data matching observed structure. JVM contract tests additionally use deterministically sanitized response bodies derived from the current local discovery captures; they contain no flow envelopes, headers, tokens, PII or raw account traffic.
- The mapper degrades missing optional fields to null and records unknown safety data explicitly.

## 7. Testing

- current search route, session parameters and header contract;
- production mapping against sanitized current-app response fixtures;
- recursive fixture scanning for sensitive keys and credential/contact patterns;
- typed product-summary extraction;
- contains versus may-contain allergens and unknown handling;
- decimal-comma nutrition and integer-cent pricing;
- promotion price and strikethrough preservation;
- preparation and extra-information extraction;
- JVM, iOS Simulator and Wasm execution;
- repository line-count and diff hygiene gates.

## 8. Completion Criteria

- [x] Search and product-detail callers receive typed objects.
- [x] Dynamic layout types do not escape the mapper.
- [x] Dietary safety fails closed.
- [x] Current discovery-shaped fixtures pass on configured targets.
- [x] Raw provider access remains explicitly available.
- [x] Remaining raw-response ports and the next conversion loop are documented.

## 9. Next Loop

Add typed clean-data objects for cart, user, delivery, wallet and checkout preflight, then map the provider objects into canonical core models and event-backed projections.
