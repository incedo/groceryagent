# Picnic Product Replacement Import

**Status:** SATISFIED
**Last updated:** 2026-08-10
**Depends on:** Batch product importer, Picnic import failure diagnostics, canonical catalog backend

## 1. Overview

Historical Picnic product IDs can disappear while an equivalent current product remains searchable
under a new retailer ID. A separate import mode resolves a failed historical snapshot by name and
package, imports an unambiguous current product when needed, and records the historical ID as a
previous ID through the catalog event stream.

## 2. Scope

### In scope

- Add `search-replacements` as an explicit importer mode.
- Extend private manifests with historical name, package text, and image ID.
- Search Picnic once per historical product and match normalized exact name plus compatible package.
- Reject no-match, same-ID, and ambiguous matches without importing.
- Fetch full details only after a unique replacement is selected.
- Check the PostgreSQL projection before importing the selected current product.
- Emit a durable previous-ID link whether the current product is new or already projected.
- Resolve catalog detail reads by either current or previous product ID.
- Keep provider calls sequential and use the configured inter-product delay.
- Commit only a sanitized retry dataset without order IDs, prices, counts, or purchase timestamps.

### Out of scope

- Fuzzy or AI matching, automatic substitution across different package sizes, or silent merges.
- Rewriting historical price observations from the old ID to the new ID.
- Treating missing ingredients, allergens, or nutrition as safe.
- Committing raw order responses, credentials, account identifiers, or personal purchase history.
- Automatically running the generated retry manifest on K3s.

## 3. Target Flow

```text
historical snapshot (old retailer ID, name, package)
  -> provider search
  -> exact normalized name + compatible package decision
  -> no match / ambiguous / unique replacement
  -> PostgreSQL current-product lookup
  -> existing: PreviousProductIdLinked
  -> missing: ProductImported + OfferObserved + PreviousProductIdLinked
  -> event store
  -> catalog product + previous-ID projection
  -> future old-ID detail lookup resolves current product
```

## 4. Canonical Models and IDs

| Value | Type | Validation | Notes |
|---|---|---|---|
| Current product ID | `ProductId` | stable and non-blank | Canonical stream identity |
| Previous product ID | `ProductId` | stable, non-blank, differs from current | Retailer-specific historical alias |
| Search name | string | non-blank | Historical order snapshot, used only for matching |
| Package | `PackageQuantity` | structured where parseable | Must be compatible with the candidate offer |

`Product.previousIds` is an ordered, duplicate-free alias list. Historical prices keep their
original product ID and provenance.

## 5. Events

| Event | Stream | Payload | Trigger |
|---|---|---|---|
| `PreviousProductIdLinked` v1 | `product:{currentId}` | current ID, previous ID, matched name/package, provider evidence | unique replacement decision |

New products continue to use `ProductImported` and `OfferObserved`. The link event is appended last
in the same command so projections never observe an alias without its current product.

## 6. Decision Models and Invariants

- Normalize names with trim, lowercase, and collapsed whitespace; do not remove meaningful words.
- A candidate matches only when the normalized names are equal and package quantities are
  dimension- and amount-compatible. Exact normalized original package text is the fallback for an
  unknown dimension.
- Zero or multiple candidates fail closed.
- The selected current ID must differ from the previous ID.
- A previous ID can point to only one current product in the projection.
- Repeated commands are idempotent and stream-version conflicts use the existing bounded retry.
- No provider exception messages or raw response bodies enter events or logs.

## 7. Projection and Queries

- `catalog_products.document` contains `Product.previousIds`.
- `product_previous_ids` maps one previous ID to one current product and is rebuilt from events.
- `getProduct(previousId)` returns the current canonical product without changing its canonical ID.
- Price-history queries remain keyed by the historical ID so accepted snapshots are not rewritten.

## 8. Provider and Pacing

- Search and detail reads use the existing Picnic adapter and captured `auth.env` contract.
- Each item performs one search and at most one detail request.
- Items stay sequential and the existing `PICNIC_REQUEST_DELAY_MILLIS` boundary applies once after
  each complete item attempt.
- Ordinary CI uses fake providers only and consumes no Picnic quota.

## 9. Verification

```shell
./gradlew :core:catalog:allTests :core:events:allTests \
  :integration:postgres:test :apps:importer:test lineCountCheck --no-daemon
git diff --check
```

Tests cover exact match, package mismatch, ambiguity, same-ID rejection, existing and new products,
event serialization, duplicate commands, alias projection, old-ID reads, rebuild, and redacted logs.

## 10. Completion Criteria

- [x] Search replacement matching is deterministic and fails closed.
- [x] New and existing current products receive a durable previous-ID event.
- [x] PostgreSQL resolves current products by previous ID after normal projection and rebuild.
- [x] Historical price IDs remain unchanged.
- [x] Existing import modes and manifests remain backward-compatible.
- [x] The sanitized retry dataset contains no order IDs, prices, counts, or timestamps.
- [x] Targeted checks and line-count checks pass.

## 11. Next Loop

Review unresolved no-match and ambiguous products manually before considering bounded fuzzy candidate
ranking. Keep that work separate from this exact-match import loop.
