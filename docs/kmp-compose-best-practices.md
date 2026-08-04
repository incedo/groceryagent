# Kotlin MPP + Compose Best Practices

This document defines project conventions for Android phone and tablet, iOS, desktop, and web.

## 1. Modular Architecture

- Separate by responsibility:
  - `apps/*`: platform launchers and backend entry points;
  - `shared/app`: composition root and app shell;
  - `feature/*`: feature-specific UI, state, and use cases;
  - `core/*`: reusable cross-feature models, rules, events, and primitives;
  - `integration/*`: provider, persistence, and platform adapters.
- Keep the functional dependency direction one-way: `apps -> shared -> feature -> core`.
- Integration modules implement core ports and are wired at the app boundary.
- The implemented catalog slice keeps canonical contracts in `core/catalog`, Picnic mapping in
  `integration/picnic-client`, event contracts in `core/events`, durable JDBC infrastructure in
  `integration/postgres`, and native HTTP composition in `apps/backend`.

## 2. Shared-First Development

- Start domain code in `commonMain`.
- Keep catalog normalization, dietary filtering, offer comparison, ranking, recipe scaling, and shopping-list rules platform-agnostic.
- Move code to platform source sets only when a platform API is required.

## 3. Compose UI and State

- Use unidirectional data flow:
  - immutable `UiState`;
  - explicit user actions;
  - clear reducers or update handlers.
- Keep composables mostly stateless and hoist state to feature boundaries.
- Features render canonical core models, not retailer or recipe-provider payloads.

## 4. Adaptive Design

- Implement responsive layout decisions in shared Compose code.
- Use width breakpoints rather than device names.
- Recommended baseline:
  - compact: width below 600dp;
  - medium: width from 600dp through 839dp;
  - expanded: width of at least 840dp.
- Compact uses one pane, medium uses list-detail where useful, and expanded may use two or three panes with persistent navigation.
- Product discovery, comparison, recipe, and shopping-list state must survive resizing.

## 5. Multimodal Input

- All primary actions work with touch, pointer, and keyboard.
- Interactive elements provide accessible target sizes, focus states, hover behavior, and meaningful semantics.
- Do not create critical touch-only or mouse-only flows.

## 6. Foldables and Posture Changes

- Treat fold, unfold, rotation, and resize as runtime state transitions.
- Preserve navigation, selected products, filters, comparison candidates, recipe scale, and list edits.
- Avoid placing critical controls across hinge or seam regions.

## 7. Platform Boundaries

- Platform entry points only start shared UI and wire platform adapters.
- Wrap barcode scanning, location, notifications, secure storage, filesystem, and database access behind interfaces.
- Keep provider HTTP details and credentials out of feature modules.
- Keep JVM-only backend infrastructure in dedicated integration or app modules.

## 8. Dependency and Build Hygiene

- Centralize versions in `gradle/libs.versions.toml`.
- Add dependencies to the narrowest module that needs them.
- Avoid cyclic dependencies and cross-feature coupling.
- Use the Gradle wrapper and keep the repository on JDK 17 unless all targets are deliberately updated.

## 9. Canonical Grocery Data

- Use shared canonical models for products, composition, offers, stores, recipes, preferences, recommendations, comparisons, and shopping lists.
- Keep product identity separate from price and availability observations.
- Keep raw provider types inside their integration module.
- Represent money with integer minor units and currency.
- Represent quantities with structured amounts, units, and compatible dimensions.
- Attach provider, source identifier, region, observation time, and confidence to external facts.

## 10. Recommendation and Comparison Rules

- Run hard allergen, dietary, excluded-ingredient, availability, and budget constraints before scoring.
- Keep the first ranking engine deterministic, versioned, reproducible, and explainable.
- Normalize price and quantity before comparing offers.
- Retain promotion conditions and stale-data warnings.
- Distinguish source facts from calculated scores and user-weighted preferences.
- Never use AI-generated text as evidence for product facts, safety, price, or availability.

## 11. Recipe and Shopping-List Rules

- Recipes use structured ingredients, amounts, units, servings, and substitutions.
- Calculations retain price and nutrition source snapshots.
- Scaling and unit conversion rules are deterministic and tested.
- Consolidation combines only compatible ingredients and retains recipe traceability.
- Explicit user edits take precedence until the user requests a refresh.

## 12. Event-Based Communication

- Use core event contracts between modules.
- Publish domain events and subscribe at the composition root or backend boundary.
- Prefer event streams over direct feature calls or database polling.
- Standardize UI feedback, navigation, domain, and sync lifecycle event categories.
- Carry domain changes end to end: command -> event -> store -> sync envelope -> projection -> observable frontend state.
- Make frontend screens consume projections backed by event streams rather than mutating a parallel state source.
- Sync incrementally with stable event IDs and cursors, and test reconnect, duplicate, retry, ordering, offline, tombstone, and conflict behavior.
- Keep purely ephemeral UI interaction out of the durable event log.

## 13. Testing Strategy

- Keep feature production and test code in sibling modules:
  - production: `feature/<name>`;
  - tests: `feature/<name>-test`.
- Test hard dietary constraints, unknown allergen data, unit conversions, currency handling, promotions, stale offers, ranking explanations, recipe scaling, and list consolidation.
- Use fixed clocks, seeded fixtures, and fake provider adapters.
- Keep live-provider tests opt-in.
- Validate the UI matrix:
  - compact plus touch;
  - medium plus touch;
  - medium plus touch and mouse;
  - expanded plus mouse and keyboard;
  - fold or unfold with state preserved.

## 14. CI Policy

- Run automated checks on each pull request and push to `main`.
- Compile and test at least one target as the initial baseline.
- Expand platform, API-contract, screenshot, and end-to-end checks as working modules are added.
- Never make ordinary CI depend on external provider availability or quota.

## 15. Dependency Versions

- Select the latest stable compatible version after checking official release information.
- Use beta or release-candidate versions only for a documented higher-priority architecture, platform, compatibility, or security requirement that stable releases cannot satisfy.
- Record the reason and stable-exit condition for every non-stable dependency.
- Require explicit approval for alpha, nightly, milestone, or snapshot versions unless already mandated by an agreed repository decision.
- Keep routine upgrades separate from unrelated feature changes.

## 16. Documentation Maintenance

- Update the README module layout when adding, removing, or renaming modules.
- Keep this guide aligned with the real build configuration.
- Keep `docs/architecture/tech-stack.md` aligned with actual module wiring, provider integrations, scoring, data freshness, and persistence decisions.
- Document each live provider's terms, attribution requirements, cache policy, rate limit, region coverage, and known data limitations.
