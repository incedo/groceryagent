# Catalog and Retailer Route Boundaries

**Status:** SATISFIED
**Last updated:** 2026-08-09
**Depends on:** Canonical catalog and backend query API; Native event-sourced catalog backend

## 1. Overview

Separate durable catalog queries from retailer-specific queries and actions at the HTTP boundary.
The catalog namespace reads Grocery Automate projections. The retailer namespace identifies the
vendor in the URL because retailer capabilities and contracts differ materially.

## 2. Scope

### In scope

- Move persisted product search and detail to `/api/v1/catalog/products`.
- Move live Picnic product discovery to `/api/v1/retailers/picnic/products`.
- Move Picnic-backed product import to `/api/v1/retailers/picnic/products/{id}/imports`.
- Remove the previous routes without redirects or compatibility aliases.
- Update API tests and documentation.

### Out of scope

- Adding retailers, retailer capabilities, or new retailer operations.
- Defining the independent product-knowledge or recipe database.
- Changing canonical models, events, projections, migrations, provider mapping, or sync.
- Public exposure, authentication, authorization, and CORS.

## 3. Current State

- `/api/v1/products` reads the persisted `catalog_products` projection.
- `/api/v1/provider-products` performs transient Picnic discovery.
- `/api/v1/products/{id}/imports` uses Picnic details to append catalog events.
- The generic route names hide the boundary between durable state and retailer interaction.

## 4. Target API

| Responsibility | Method and route | Source or effect |
|---|---|---|
| Catalog search | `GET /api/v1/catalog/products?query=&limit=` | PostgreSQL projection |
| Catalog detail | `GET /api/v1/catalog/products/{id}` | PostgreSQL projection |
| Picnic discovery | `GET /api/v1/retailers/picnic/products?query=&limit=` | Live Picnic adapter |
| Picnic import | `POST /api/v1/retailers/picnic/products/{id}/imports` | Append catalog events |

The retailer name is a path segment, not a query parameter. Future retailers receive their own
namespace and may expose different resources and actions. Stable canonical value types may be
shared, but this loop does not impose false capability parity across vendors.

## 5. Models, Events, Projections, and Sync

This route-only change introduces no values, IDs, commands, events, schema versions, migrations,
projections, or frontend state. `ProductImported` and `OfferObserved` remain the durable import
events. `domain_events` remains the source of truth and `catalog_products` remains its rebuildable
read projection. Existing idempotency, ordering, cursor, retry, and error behavior is unchanged.

Provider provenance, freshness, quantity, money, allergen unknown states, and redaction rules are
unchanged. No provider payload or credential is added to the repository.

## 6. Compatibility

There are no production consumers. The following routes are removed and must return `404`:

- `/api/v1/products`
- `/api/v1/products/{id}`
- `/api/v1/products/{id}/imports`
- `/api/v1/provider-products`

No redirects, aliases, or deprecation window are provided.

## 7. Verification

Deterministic route tests cover catalog search/detail, Picnic discovery/import, validation,
provider error redaction, and absence of the legacy routes. The PostgreSQL component test covers
the renamed import-to-event-to-projection-to-detail chain.

Exact commands:

```shell
./gradlew :core:events:allTests :integration:postgres:test :apps:backend:test lineCountCheck
./gradlew check
docker compose config --quiet
docker compose up --build --wait
docker compose exec -T catalog-service /app/grocery-catalog-service --healthcheck
docker compose down --volumes
git diff --check
```

## 8. Completion Criteria

- [x] Catalog routes use only `/api/v1/catalog/products`.
- [x] Current Picnic routes use only `/api/v1/retailers/picnic`.
- [x] Legacy routes return `404`.
- [x] No event, projection, database, or provider contract changes.
- [x] Tests and API documentation use the new routes.
- [x] Required verification passes.

## 9. Decisions and Next Loop

- **Decision:** Retailer identity stays in the URL because vendor capabilities differ materially.
- **Decision:** Catalog and recipe database design remains separate from retailer integrations.
- **Decision:** Backward compatibility is intentionally omitted before production.
- **Next loop:** Define independent canonical product-knowledge and recipe storage before adding
  their ingestion or search behavior.
