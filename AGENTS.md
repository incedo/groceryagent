# AGENTS.md

Project rules for contributors and coding agents working in this repository.

## Scope

- These rules apply to the whole repository.
- If a subdirectory contains its own `AGENTS.md`, the closest file to the changed code takes precedence.

## Kotlin MPP + Compose Rules

1. Architecture layering is mandatory.
   - `apps/*` are platform entry points only.
   - `shared/app` is the composition root.
   - `feature/*` contains feature UI and feature state.
   - `core/*` contains reusable primitives, domain models, rules, and contracts.
   - `integration/*` contains provider, persistence, and platform adapters.
   - `apps/*` must not be imported by `shared/*`, `feature/*`, `core/*`, or `integration/*`.

2. Shared-first code placement.
   - Put domain logic, state models, recommendation rules, comparison rules, recipe calculations, and use cases in `commonMain` by default.
   - Use platform source sets (`androidMain`, `iosMain`, and others) only for platform APIs.

3. Platform and provider APIs behind abstractions.
   - Platform integrations such as barcode scanning, location, notifications, filesystem, and secure storage must be wrapped behind interfaces.
   - Retailer, product-catalog, pricing, nutrition, and recipe-provider APIs must be adapted in `integration/*` behind ports declared by `core/*`.
   - Use dependency injection or factory wiring in platform, app, or composition-root modules.

4. UI state pattern.
   - Features expose explicit immutable UI state and event handlers.
   - Avoid mutable global state and platform-specific UI logic in shared features.

5. Adaptive UI requirement.
   - Mobile UIs support phone and tablet through shared adaptive layouts and size breakpoints.
   - Desktop and web use the same feature state and navigation semantics.

6. Dependency management.
   - Versions come from `gradle/libs.versions.toml`.
   - Do not hardcode dependency versions inside module `build.gradle.kts` files.

7. Module dependency direction.
   - Allowed functional flow: `apps -> shared -> feature -> core`.
   - Feature modules may depend on core modules only.
   - Core modules must not depend on feature, shared, app, or concrete integration modules.
   - Integration modules implement core ports and are wired by `apps/*` or `shared/app`; features never import concrete integrations.

8. Testing minimum.
   - Every new feature or module includes at least one deterministic automated test.
   - Shared ranking, filtering, normalization, comparison, and recipe logic uses deterministic cross-platform tests from dedicated test modules.

9. Feature test code location.
   - Keep feature production code and test code in separate sibling folders or modules.
   - Use `feature/<name>` for production and `feature/<name>-test` for tests.
   - Prefer test modules over placing tests directly inside production feature-module source folders.

10. CI gate.
    - Pull requests must pass GitHub Actions checks before merge.
    - Do not merge with failing checks.

11. Keep platform bridges thin.
    - Android `Activity`, iOS `UIViewController`, desktop, and web `main` files only bootstrap shared UI and platform adapters.

12. Canonical grocery model is required.
    - Retailer, Open Food Facts, barcode, nutrition, and recipe-provider records map to canonical shared models in `core/*`.
    - Feature and UI layers consume canonical models, never raw provider payload types.
    - Keep `Product`, `ProductComposition`, `ProductOffer`, `Store`, `Recipe`, `Ingredient`, `DietaryProfile`, `Recommendation`, and `Comparison` as separate concepts.
    - A product describes what an item is; an offer describes a retailer-, region-, quantity-, time-, and promotion-specific sales opportunity.

13. Event-based module communication is required.
    - Cross-module communication is event-driven through shared event contracts in core modules.
    - Producers publish events and consumers subscribe; avoid direct feature-to-feature calls.
    - Realtime in-app synchronization uses events first, not database polling.
    - Use explicit event categories:
      - UI feedback events;
      - navigation events;
      - domain events such as preference, offer, recommendation, recipe, comparison, and shopping-list changes;
      - sync lifecycle events: idle, syncing, up-to-date, and error.
    - Domain state changes follow the full path: command -> decision -> domain event -> local or backend event store -> projection -> frontend state.
    - Backend-to-client synchronization transfers event envelopes with stable IDs, schema versions, device or producer IDs, ordering/cursor metadata, and timestamps.
    - Frontends update from projected event streams exposed as `Flow` or an equivalent observable contract; do not maintain a second independently mutated UI source of truth.
    - Use incremental event sync or pushed invalidation before polling. Polling is only a documented fallback for providers or transports that cannot stream changes.
    - Ephemeral pointer, hover, focus, and draft-input changes do not need durable domain events unless they alter domain state.

14. `commonMain` must stay platform-neutral.
    - `commonMain` code must not import platform-specific APIs such as `java.time`, `java.io`, `java.nio`, Apple Foundation types, or Android framework classes.
    - Put platform-specific date/time, filesystem, locale, location, scanner, and OS integrations behind `expect`/`actual` abstractions or multiplatform-safe libraries.
    - Shared logic uses only APIs that compile on every configured target.

15. Recommendations must be deterministic and explainable first.
    - Apply hard constraints before ranking: allergies, dietary rules, excluded ingredients, budget ceilings, and availability.
    - Results include scoring version, factors, trade-offs, exclusions, evidence sources, confidence, and generation time.
    - AI may explain or parse information but must not invent products, prices, availability, ingredients, nutrition, or ranking evidence.
    - Sponsored placement, if introduced, must be labeled and cannot bypass hard constraints.

16. Comparisons must use a normalized basis.
    - Compare prices using declared compatible units such as `EUR/kg`, `EUR/l`, or `EUR/item`.
    - Preserve original pack price, pack quantity, unit, currency, retailer, region, and observation timestamp.
    - Promotions retain conditions such as loyalty membership, minimum quantity, and validity period.
    - Incompatible products or quantity dimensions must be marked explicitly and must not produce a misleading winner.

17. Food safety and dietary constraints fail closed.
    - Missing allergen, ingredient, or cross-contamination data is `unknown`, never assumed safe.
    - Allergy conflicts cannot be overridden by a soft score or preference.
    - Show a warning when source data is incomplete, uncertain, or unverified.
    - Product and recipe recommendations are general grocery guidance, not medical diagnosis or treatment.

18. Recipe calculations must be reproducible.
    - Store structured ingredient amounts, units, serving count, substitutions, nutritional estimates, price inputs, and source snapshots.
    - Scaling preserves units and documented rounding rules.
    - Cost and nutrition outputs are labeled as estimates and recalculated when an input changes.
    - Shopping-list consolidation preserves the originating recipe and manual user edits.

19. Freshness and provenance travel with external data.
    - Externally sourced facts record provider, source identifier, fetched or observed time, region when relevant, and verification or confidence state.
    - Never present stale price or availability as live.
    - Saved recipes, recommendations, and comparisons preserve accepted snapshots so later provider corrections do not silently rewrite history.

20. Money and quantities use safe representations.
    - Store money as integer minor units plus ISO currency; do not use floating-point values for prices.
    - Store quantities as structured amount and unit values.
    - Conversion rules must be explicit, tested, and dimension-safe.

## App Generation Rules (Required)

1. Generate one adaptive UI code path, not separate per-device screens.
   - Build layouts from window size classes and constraints, never from hardcoded device names.

2. Support multimodal input by default.
   - Every primary user action works with touch, mouse or pointer, and keyboard.
   - Include focus, hover, and accessible tap or click targets in shared UI components.

3. Use pane-based responsive layouts.
   - Compact: single-pane layouts.
   - Medium: two-pane list-detail layouts where relevant.
   - Expanded: two or three panes with persistent navigation, rail, or sidebar.

4. Include foldable-aware behavior.
   - Handle window-size and posture changes without losing UI state.
   - Do not place critical controls across hinge or seam regions.

5. Keep behavior consistent across form factors.
   - Preserve navigation and domain state when resizing or changing posture.
   - Change presentation density and layout only, not app semantics.

6. Enforce input and size-class testing.
   - New app features include at least one test or preview for compact and expanded layouts.
   - New interactive components validate touch and pointer behavior.

## Tooling Rules

- Use `./gradlew`, not system Gradle, for project tasks.
- Keep the JDK target at 17 unless explicitly changed across the repository.
- Keep touched Kotlin and Gradle source files at or under 300 lines by splitting per responsibility where needed; documentation, generated handoff artifacts, and design references are not source-code line-count targets.
- Keep only code that belongs together in the same Kotlin or Gradle file; do not group unrelated classes, composables, helpers, or build logic for packaging convenience.
- Existing oversized legacy files may remain temporarily, but they must not grow; enforce this with `./gradlew lineCountCheck` when the task exists.
- Use fixed clocks, seeded fixtures, and fake providers in normal automated tests.
- Live retailer, catalog, pricing, or recipe-provider tests are opt-in and must not consume provider quotas in ordinary CI.
- Do not commit credentials, retailer tokens, precise user locations, or raw provider responses containing personal data.

## Dependency Version Rules

- When establishing the project stack or adding a dependency, select the latest stable compatible version of Kotlin, Compose Multiplatform, Gradle, JDK tooling, libraries, plugins, and test tools.
- Verify the current stable version from the dependency's official release notes or repository before selecting it; do not rely on remembered version numbers.
- A beta or release-candidate version is allowed only when a higher-priority repository rule, required platform target, security fix, or necessary compatibility constraint cannot be met by a stable version.
- Document every non-stable dependency in the version catalog or architecture documentation with the reason, risk, containment, and planned stable migration trigger.
- Alpha, milestone, nightly, and snapshot versions require explicit user approval unless an existing repository decision already mandates them.
- Once selected, a dependency does not need to be upgraded during ordinary feature work merely because a newer version exists. Give upgrades their own scoped request, branch, verification, and pull request unless the dependency change is required for the feature, compatibility, or a security fix.

## Testing and Verification Rules

- Run the smallest check set that proves the touched surface, then broaden checks when shared contracts, event schemas, build configuration, or cross-module wiring change.
- Required minimum checks:
  - documentation only: `git diff --check`;
  - core shared logic: affected `allTests` task plus `lineCountCheck`;
  - shared app or event wiring: affected core tests, `:shared:app:allTests`, and `lineCountCheck`;
  - Compose web: relevant tests plus the Wasm browser development distribution build;
  - backend routes or persistence: domain tests, adapter tests, migration tests, and API contract tests;
  - broad build or version changes: the repository quality gate across configured targets.
- Event-driven features test the entire chain: command decision, emitted event, serialization, idempotent storage, sync envelope, ordering or cursor behavior, projection rebuild, frontend state update, and reconnect recovery.
- Sync tests cover duplicates, out-of-order delivery, retry, offline edits, tombstones, schema-version handling, cursor resume, and deterministic conflict resolution.
- Recommendation tests cover hard filters separately from soft ranking and assert human-readable explanations and evidence.
- Comparison tests cover unit normalization, currency boundaries, promotions, incompatible dimensions, missing values, and stale observations.
- Recipe tests cover scaling, rounding, substitutions, cost snapshots, nutrition estimates, and shopping-list consolidation.
- New UI behavior includes compact and expanded coverage and validates keyboard, pointer, and touch interaction.
- Never update a failing expected snapshot or fixture merely to make a test pass; first verify that the new behavior is intended.

## Git Workflow Rules

- For every new user request, create and use a separate branch with prefix `codex/`.
- Open one pull request per request or branch.
- Keep each pull request scoped to that single request and avoid unrelated changes.
- Ensure required checks pass before merge.

## Commit Rules

- Commit only a coherent, working slice. Do not mix unrelated refactors, dependency upgrades, generated files, personal data, or formatting churn.
- Review `git status`, the staged diff, and the exact files being committed before every commit.
- Stage explicit paths; avoid broad staging when unrelated user changes are present.
- Use imperative, plain, scoped commit subjects such as `Define grocery recommendation events` or `Add normalized offer comparison`.
- Keep the subject concise and add a body when the reason, migration, compatibility trade-off, or verification is not obvious from the diff.
- Do not amend, squash, rebase, force-push, or rewrite commits belonging to the user unless explicitly requested.
- Do not create a commit while relevant required tests fail. If a known unrelated check fails, document the exact failure and evidence instead of hiding it.
- One request uses one `codex/*` branch and one pull request unless the user explicitly asks to group work.

## Private Registry and Delivery Rules

- Project-built container images are private artifacts and must publish only to the LAN-only
  homelab registry at `registry.home.intelliworks.nl:5000`.
- Never publish Grocery Automate images to GHCR, Docker Hub, or another public registry unless the
  user explicitly changes this rule in a separately agreed architecture loop.
- Public registries may be used read-only for pinned and verified upstream base images and build
  dependencies; this does not authorize publishing project artifacts there.
- Homelab image publication runs only on the self-hosted `homelab` GitHub Actions runner after all
  required tests and native container checks pass. GitHub-hosted runners must not publish images.
- Pull requests, especially forks, must not run untrusted code on the self-hosted homelab runner;
  use an isolated GitHub-hosted runner for their native build and smoke tests.
- The homelab registry is intentionally LAN-only plain HTTP. Do not expose it publicly, add public
  ingress, disable TLS verification for other hosts, or configure it as insecure outside managed
  homelab Docker daemons.
- Use `registry.home.intelliworks.nl:5000/grocery-automate/<service>` image names. Always publish an
  immutable `sha-<full-git-sha>` tag; mutable `main` or `latest` tags may move only after the same
  tested image is published successfully.
- Build once and publish the exact image that passed PostgreSQL integration and native readiness
  checks. Do not rebuild a separate release image.
- Do not put registry credentials, provider credentials, database secrets, or private image
  archives in the repository, public package stores, workflow artifacts, logs, or container layers.
- If the homelab runner or registry is unavailable, fail or leave delivery pending; never fall back
  automatically to a public registry.

## Implementation Loop Rules

- Every non-trivial feature starts from an `AGREED` spec using `docs/architecture/TEMPLATE.md` or an equivalent existing spec.
- Work follows this loop:
  - pick one scoped request;
  - create a `codex/*` branch;
  - read `AGENTS.md` and relevant architecture/spec documents;
  - inspect the current code, tests, event schema, projections, and data shape;
  - resolve or record unsettled product decisions;
  - implement the smallest end-to-end event-driven slice;
  - verify domain, sync, projection, and frontend behavior;
  - update documentation and define the next loop when work remains;
  - commit one coherent slice and open one pull request;
  - wait for required CI before merge.
- Loop specs use statuses `DRAFT`, `PROPOSED`, `AGREED`, and `SATISFIED` as defined in `docs/architecture/LOOP-CONFIG.md`.
- A loop must have concrete completion criteria, affected modules, event contracts, frontend projection behavior, verification commands, and explicit out-of-scope items.
- Do not silently expand a loop. New work becomes a follow-up loop unless it is required to satisfy the current loop's agreed criteria.
- A completed loop either names the next loop or records why the bounded slice is complete.

## Documentation Rules

- Update `docs/kmp-compose-best-practices.md` and `README.md` when architecture or module boundaries change.
- Keep `docs/kmp-compose-best-practices.md` aligned with adaptive-layout and multimodal-input generation rules.
- Keep `docs/architecture/tech-stack.md` aligned with stack, provider, persistence, recommendation, and comparison decisions.
- New modules must be documented in the README module layout.
- Document scoring formulas, normalization rules, freshness policies, licensing constraints, and provider-specific limitations when introduced.
