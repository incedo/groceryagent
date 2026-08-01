# Picnic Contract Reconciliation

**Status:** AGREED
**Last updated:** 2026-08-02
**Depends on:** Picnic Kotlin client, sanitized Picnic capture fixtures, Ktor transport contract

## 1. Purpose

Reconcile three distinct descriptions of the Picnic integration:

1. the request and response contracts described in the local `picnic-api-discovery/docs` Markdown files;
2. the traffic observed from Picnic Android 1.239.3 in the local, ignored capture archive;
3. the Kotlin client currently implemented in `integration/picnic-client` from the legacy `MRVDH/picnic-api` surface.

The current Kotlin client can construct the 65 legacy operations represented by its parity test. That is useful compatibility coverage, but it is not proof that every operation is accepted by the current provider or that every response has a usable object model.

This document is the agreed reconciliation backlog. It separates request drift from response-model drift and defines bounded loops to close both without replaying private or mutating traffic against a live account.

## 2. Terms and Evidence Rules

| Term | Meaning |
|---|---|
| Request contract | Destination host, method, path, query, headers, and body shape |
| Wire response | HTTP status, response headers, and provider JSON or binary shape |
| Provider object model | Stable typed Picnic model returned by this integration |
| Canonical grocery model | Provider-independent model in `core/*`; it is not part of this reconciliation |

Evidence is applied in this order:

1. Captured Picnic Android 1.239.3 traffic is authoritative for what that app version actually sent or received.
2. Discovery Markdown explains and indexes the captures, but does not override contradictory traffic.
3. Committed sanitized fixtures prove selected response shapes without retaining private data.
4. `MRVDH/picnic-api` 4.6.0 is a compatibility baseline, not evidence that a route remains current.
5. The Kotlin endpoint-parity test proves request construction only; it does not prove provider acceptance.

Each finding has one classification:

- **Confirmed:** capture evidence and the current implementation agree.
- **Contradicted:** capture evidence shows that the current request or interpretation differs.
- **Unobserved:** present in code or Markdown, but not demonstrated by the available captures.
- **Partially modeled:** the route is usable, but response semantics are lost behind `JsonElement` or a lossy type.

Raw captures remain ignored in `/Users/kees/data/projects/picnic-api-discovery/captures`. Tests may use only minimal sanitized derivatives that pass the sensitive-data checks. Request values, credentials, auth headers, device identifiers, account data, addresses, and order data must not be copied into this repository.

## 3. Request Contract Reconciliation

| Capability | Markdown and capture evidence | Kotlin client now | Finding | Required reconciliation |
|---|---|---|---|---|
| Host families | Discovery separates gateway, storefront API, and public gateway traffic. Captures place most authenticated calls on the country storefront. | One `apiBaseUrl` plus a derived storefront root; one override controls both. | **Contradicted** for login/public routing; update/push host evidence is mixed. | Model destinations explicitly and test each endpoint family against its selected host. |
| Login destination | Discovery documents `POST /user/login` on the global gateway. | Relative login resolves against the storefront API base URL. | **Contradicted** | Route login through the gateway destination. |
| Login body | Documented keys are `client_id`, `client_version`, `device_id`, `device_name`, `key`, and hashed `secret`. | Sends only `client_id`, `key`, and hashed `secret`. | **Contradicted** | Add the three missing device/version fields as explicit configuration values. |
| Authenticated headers | Captured authenticated requests consistently include `x-picnic-auth`, `x-picnic-agent`, `x-picnic-did`, language, and user agent. | Auth is added when available; agent/device headers depend on a per-call `picnicHeaders` Boolean. Several authenticated operations omit that flag. | **Contradicted** | Replace call-site opt-in with an explicit request policy that derives required headers. |
| Repeated request headers | No available capture requires multiple values for one request-header name. | `PicnicHttpRequest.headers` is `Map<String, String>`: many names, one value per name. | **Confirmed limitation** | Keep the current port until evidence requires repeated values. Responses continue to preserve repeated headers. |
| Update check | Captured keys include `build_number`, `client_id`, `device_id`, `device_name`, `device_os`, `first_time`, `tracking`, and `version`. | Sends the captured typed shape to storefront first and can fall back to the documented gateway after a definitive unsupported-route response. | **Reconciled** | Preserve request-shape and host-fallback tests without exposing device/tracking values. |
| Push registration | Captured body uses `push_destination` and numeric `push_version` on storefront. | Sends the captured shape to storefront and never automatically retries this state change on another host. | **Reconciled** | Preserve the single-transmission mutation contract. |
| Cart mutations | Add/remove use `count` and `product_id`; remove-group uses `group_id`; set-slot uses `slot_id`. | Current request shapes match those observed calls. | **Confirmed** | Preserve with sanitized request-shape tests. |
| Search | `search_term` is required; captures also show optional session/tampering keys in some flows. | Sends the required term and its existing session parameters. | **Confirmed with variants** | Treat extra captured keys as optional until their semantics are agreed. |
| Product details | Current page route is confirmed. A Boolean action flag varies in documentation/examples. | Sends the current page route and one fixed action-flag value. | **Unsettled value** | Assert the key and validate its value from redacted capture metadata before changing it. |
| Current checkout | Captures show pre-checkout and `POST /cart/checkout/start`; the captured checkout start returned `400`. | Neither current operation is exposed. Legacy order status/confirm routes are exposed. | **Missing current surface** | Add contracts and mock fixtures only. Never use ordinary CI to place a live order. |
| Address specifications | Current captures/docs show specification, metadata, and enabled-field endpoints. | No ports exist. | **Missing current surface** | Add read-only request contracts after privacy review. |
| Wallet debts | Current capture/docs show `/wallet/debts`. | No operation exists. | **Missing current surface** | Add a read-only request contract and sanitized response fixture. |
| Current server-driven pages | Slot selector, basket footer, categories, cooking/planner, profile, and wallet pages are captured/documented. | Generic `getPage` can fetch page references, with only limited typed conveniences. | **Confirmed generic access** | Do not add duplicate routes merely for parity; add named operations only when they carry stable semantics. |
| Legacy cart/delivery/payment routes | Legacy bulk add, clear, minimum-order, order confirm/status, per-delivery operations, and transaction detail are in the Kotlin surface. | Endpoint parity constructs them successfully against a fake transport. | **Unobserved** | Quarantine as compatibility operations until captured, documented as current, or deliberately deprecated. |
| Removed legacy endpoints | Discovery reports old search, store, product/article, list, old deliveries, recipe, and wallet roots as removed/404. | Current catalog/delivery paths already use newer alternatives; other compatibility operations still need classification. | **Partly reconciled** | Add an explicit disposition test/table; do not claim live parity from route construction. |

The configured default agent currently identifies an older app build than the capture baseline. Versions and device metadata must become explicit typed configuration, not fragile substrings parsed from one header. This loop does not require routine upgrades after initialization; it requires the configuration used by a request to be internally consistent and testable.

## 4. Response and Object-Model Reconciliation

| Capability | Markdown and captured response | Kotlin client now | Finding | Required reconciliation |
|---|---|---|---|---|
| Login | Stable account/2FA flags plus rotated auth response header. | Typed `PicnicLoginResult`; auth header is required. | **Confirmed, request still drifts** | Retain the typed response while fixing destination/body. |
| Catalog search | Server-driven UI response mapped into product summaries. | Typed `PicnicSearchResult` backed by sanitized captures. | **Partially modeled** | Add localization and malformed/empty-layout cases; map to canonical products later. |
| Product details | Server-driven layout/script with product, pricing, dietary, nutrition, preparation, and related content. | Typed details model with fail-closed allergens and integer-cent prices. | **Partially modeled** | Reconcile image list/format, localization, preparation-null semantics, and unobserved bundles. |
| Images | Captures may return PNG or WebP and variants are provider-controlled. | Builds a fixed `{size}.png` URL and PNG data URI. | **Contradicted/too narrow** | Preserve actual content type and support only observed, tested variants. |
| Cart and slots | Clean, structured cart responses include lines/articles, totals, slot state, minimums, state token, and mutation timestamps. Mutations return the updated cart. | Returns raw `JsonElement`. | **Partially modeled** | Create typed provider models from sanitized empty/populated/mutation fixtures. |
| Deliveries | Summary is an array; other lifecycle routes need current evidence. | Returns raw `JsonElement` for summary and compatibility operations. | **Partially modeled/unobserved** | Type summary first; do not infer per-delivery contracts from legacy code. |
| User/account/address | Discovery proposes clean user, address, and household models; captures contain private fields. | Returns raw `JsonElement`. | **Partially modeled, privacy-sensitive** | Agree minimization/redaction before fixtures or typed models. |
| Payment/wallet | Profile, transaction list, debts, and minor-unit amounts have structured responses. | Returns raw `JsonElement`; debts is absent. | **Partially modeled** | Add typed money-safe models and pagination/debt outcomes. |
| Meals/recipes | Responses are page/layout based; recipe semantics need a mapper. | Returns raw `JsonElement`. | **Partially modeled** | Keep raw PML internal and expose stable typed recipe/provider values incrementally. |
| Messages | Captures show `200` with messages/query interval and `304` with an empty body, using ETag semantics. | Every non-2xx status throws `PicnicApiException`. | **Contradicted** | Represent not-modified/cache outcomes and preserve ETag headers. |
| Reminders/no content | Documentation records `204` when no reminders exist. | Successful empty bodies become an empty JSON object. | **Lossy** | Preserve no-content as an explicit outcome instead of inventing `{}`. |
| Provider errors | Checkout and other errors may carry an error code, message, and structured details. | Keeps status and one nested message only. | **Lossy** | Define safe error code/details and retry classification without exposing raw bodies. |

Typed Picnic models are still integration models. They must not escape directly into features. A later provider adapter maps them to canonical `Product`, `ProductOffer`, `Recipe`, `Money`, provenance, freshness, and confidence types.

## 5. Current Test Meaning

- `EndpointParityTest` constructs the 65 legacy operations with an in-memory transport. It proves methods, paths, selected headers, and bodies—not current provider validity.
- `KtorPicnicHttpTransportContractTest` proves the real Ktor adapter forwards URLs, methods, bodies, and request headers and preserves response bytes/status/repeated headers.
- Sanitized capture tests currently prove search and product-detail mapping only.
- JVM coverage of 95.32% lines and 65.46% branches measures executed bytecode, not current API completeness.
- No ordinary test may contact or mutate a live Picnic account.

## 6. Current-First Compatibility Policy

The client exposes one capability and one typed Picnic provider model even when Picnic has more than one wire contract for that capability:

```text
Picnic input port
  -> current captured route and current-response mapper
  -> eligible fallback only when the current route is unavailable
  -> legacy route and legacy-response mapper
  -> one typed Picnic provider model plus route provenance
```

The current captured route is always the primary implementation. The legacy route is a compatibility adapter, not a second public API. Both response shapes have separate wire DTOs or parsers and converge on the same typed provider model. Features therefore do not branch on Picnic route generations.

Automatic fallback follows these rules:

- A read-only, idempotent operation may fall back after a definitive `404`, `405`, `410`, an explicitly modeled unsupported-operation error, or a current-response mapping incompatibility.
- Authentication may fall back between documented login destinations only after a definitive unsupported-route response. A rejected credential (`401` or `403`) is final and must not be retried elsewhere.
- A cart, consent, checkout, order, delivery, payment, account, or other state-changing request must not be sent automatically to a second route after the first request was transmitted. Timeouts and connection loss leave the mutation outcome ambiguous and must surface as such.
- A mutating legacy route may be selected only before transmission through an explicit, tested capability/configuration decision. It is never a recovery retry.
- `400`, `401`, `403`, `409`, `429`, cancellation, timeout, network failure, and `5xx` do not mean “route missing” and do not trigger automatic fallback.
- If the primary response cannot be mapped, a read fallback records the mapping failure for safe diagnostics; it never logs the raw response or sensitive values.
- If both candidates fail, the client returns one safe failure containing attempted route generations and sanitized status/reason metadata, never raw bodies.

Successful results carry integration-level provenance identifying current or legacy route generation and the observation time. This provenance is mapped into canonical source/freshness metadata later; it is not UI branching state.

Contract tests must prove primary success, eligible read fallback, both-candidates failure, mapping equivalence, authentication rejection without fallback, and zero double-send behavior for mutations. Live fallback tests remain opt-in and may not mutate a real account in ordinary CI.

### Implemented slice — SATISFIED 2026-08-02

- Login uses the current gateway request first and uses the legacy storefront destination only after a definitive unsupported-route response.
- Login rejection, rate limiting, transport failure, cancellation, and server failure cannot trigger the legacy destination.
- Catalog search prefers `/pages/search-page-root-content` and can fall back to legacy `/search`.
- Product details prefer `/pages/product-details-page-root` and can fall back to legacy `/product/{id}` after route unavailability or a typed mapping incompatibility.
- Update check uses the captured storefront request first and can fall back to the documented gateway with an identical typed request body.
- Push registration uses the captured storefront request and is never automatically retried because it changes provider state.
- Current page responses and legacy clean responses converge on `PicnicSearchResult` and `PicnicProductDetails` with `CURRENT` or `LEGACY` provenance.
- Legacy dietary data fails closed: absent explicit allergen data remains `UNKNOWN`.
- Mutating service operations do not use the read-fallback executor and a contract test proves a failed cart mutation is transmitted exactly once.
- The production Ktor `MockEngine` contract proves the two-request read fallback, not just an in-memory service fake.

This satisfies the current-first policy for the typed capabilities that presently have both route generations. Raw `JsonElement` endpoint families do not gain fallback until their stable provider object model and privacy requirements are agreed.

## 7. Reconciliation Roadmap

Each row is a separate future `codex/*` branch and pull request.

| Loop | Status | Bounded result | Depends on |
|---|---|---|---|
| **PCR-001 Provider routing and header policy** | **SATISFIED** | Correct destination model, automatic header policy, internally consistent client metadata, and current-first login routing | Current transport contract |
| **PCR-002 HTTP outcomes and safe errors** | AGREED | Explicit `204`, `304`/ETag, safe structured errors, fallback eligibility, and ambiguous-mutation outcomes | PCR-001 |
| **PCR-003 Current-first request surface** | PARTIAL | Typed catalog selection plus update/push payload and host behavior are implemented; mock-only pre-checkout, checkout-start, address-specification, and wallet-debt contracts remain | PCR-001, PCR-002 |
| **PCR-004 Clean response objects** | PARTIAL | Current and legacy catalog mappers converge; typed cart/slots and privacy-reviewed delivery, wallet, and user models remain | PCR-002, PCR-003 |
| **PCR-005 Pages, recipes, and images** | AGREED | Internal raw-PML boundary, typed recipe mapping, localization, and content-type-correct images | PCR-003 |
| **PCR-006 Legacy disposition** | AGREED | Every compatibility operation labeled current, deprecated compatibility-only, removed, or still unobserved | PCR-002 through PCR-005 |

## 8. Satisfied Loop: PCR-001

### Goal

Make every request select its provider destination and header requirements from one explicit policy, then align login with the documented current request contract while retaining a safe legacy destination candidate.

### In scope

- Add separate gateway, storefront API, storefront asset, and public-gateway destinations to `PicnicClientConfig` or an equivalent immutable configuration model.
- Replace `picnicHeaders: Boolean` with a request policy describing destination, authentication requirement, and Picnic device-header requirement.
- Apply the policy consistently to all existing operations without changing their public response types.
- Represent client version, build number, device name/OS, device ID, client ID, and agent consistently.
- Send the documented login body through the gateway and retain hashed-secret and auth-token behavior.
- Model the legacy login destination as a compatibility candidate, but fall back only after a definitive unsupported-route response; never after rejected credentials, timeout, cancellation, or server failure.
- Keep `PicnicHttpRequest.headers` as one value per header name because current captures do not require a multi-value request header.
- Add deterministic common tests and Ktor `MockEngine` tests for destinations, language, headers, login body, eligible login fallback, and rejection without fallback.

### Out of scope

- Live authentication or any real provider call.
- Push/update payload changes; those belong to PCR-003 once routing and outcome policies exist.
- Adding checkout, cart, delivery, wallet, user, or recipe object models.
- Canonical grocery mapping, events, persistence, synchronization, projections, or frontend state.
- Dependency upgrades.

### Affected modules and direction

- `integration/picnic-client` only.
- Input ports remain independent of Ktor.
- The request policy belongs in the application/domain side of the integration; Ktor remains an output adapter.
- No `core/*`, feature, shared-app, or frontend dependency is introduced.

### Event and frontend impact

PCR-001 changes only the provider transport boundary. It emits no grocery domain event and changes no frontend projection. Provider responses must not become a second frontend source of truth. Event ingestion and canonical projections start only after typed provider values are mapped in a later bounded context.

### Completion criteria

- [x] Tests distinguish gateway, storefront API, asset, and public-gateway destinations.
- [x] Login uses the gateway and sends all six documented fields without exposing their values in snapshots or failures.
- [x] A definitive unsupported current login route selects the legacy destination and maps to the same `PicnicLoginResult`.
- [x] Invalid credentials, rate limits, timeout, cancellation, and `5xx` make exactly one login attempt by construction; rejection and server outcomes have direct contract coverage.
- [x] Authenticated storefront requests automatically receive auth, agent, device, language, and user-agent headers according to policy.
- [x] Unauthenticated requests do not accidentally receive an auth token.
- [x] Germany, France, Netherlands, overrides, and trailing-slash normalization are deterministic.
- [x] Existing endpoint-parity and typed catalog tests still pass.
- [x] The production Ktor adapter contract passes on JVM, iOS Simulator, and Wasm.
- [x] Touched Kotlin/Gradle source stays at or below 300 lines.
- [x] PCR-003 records the remaining mixed-host update/push decision.

### Verification

```shell
./gradlew :integration:picnic-client:allTests \
  :integration:picnic-client:koverLog \
  :integration:picnic-client:koverVerify \
  lineCountCheck
./gradlew check
git diff --check
```

## 9. Open Evidence Questions

- **Q-1:** Are update-check and push-registration gateway operations or storefront operations for all current countries? **Decision:** current captures win: storefront is primary. Read-only update check may fall back to the documented gateway on a definitive unsupported-route response; mutating push registration must not retry automatically.
- **Q-2:** Which product-detail action-flag value is required by the current app? **Decision:** retain current behavior until a redacted value assertion can be derived safely.
- **Q-3:** Which legacy delivery/order/payment routes remain accepted? **Decision:** label them unobserved and do not delete or advertise them as current before PCR-006.
- **Q-4:** Which private user/address fields are actually needed by Grocery Automate? **Decision:** settle data minimization before PCR-004 creates fixtures or models.

## 10. Next Loop

- **Next spec:** PCR-002 HTTP outcomes and safe errors.
- **Reason:** explicit `204`, `304`/ETag, retry classification, and ambiguous mutation outcomes are prerequisites for safely expanding current-first compatibility to more endpoint families.
