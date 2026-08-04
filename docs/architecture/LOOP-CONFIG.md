# Grocery Automate Spec Loop Configuration

**Last updated:** 2026-08-01

This document defines how scoped specifications move into code. It is a guardrail for contributors and coding agents, not permission to implement every draft automatically.

## 1. Execution Model

```text
Pick scoped request
  -> create codex/* branch
  -> read AGENTS.md and relevant specs
  -> inspect current code, events, projections, and data
  -> settle or document open behavior
  -> implement the smallest end-to-end event slice
  -> run targeted checks
  -> update docs and define the next loop if needed
  -> commit one coherent slice
  -> open one PR
  -> wait for required CI before merge
```

Specification status controls implementation:

| Status | Meaning |
|---|---|
| `DRAFT` | Exploration only; do not treat behavior as settled |
| `PROPOSED` | Plausible direction; needs approval or a deliberately scoped spike |
| `AGREED` | Approved direction that may drive implementation |
| `SATISFIED` | Completion criteria are implemented, documented, and verified |

## 2. Required Loop Shape

Each implementation loop defines:

- user value and bounded scope;
- explicit out-of-scope items;
- affected modules and dependency direction;
- canonical values, IDs, commands, and domain events;
- event metadata and schema-version impact;
- source of truth, projections, and frontend state flow;
- local/backend sync, idempotency, cursor, offline, conflict, and tombstone behavior;
- provider freshness, provenance, and licensing implications;
- deterministic tests and exact verification commands;
- migration or compatibility impact;
- completion criteria and the next loop, if any.

Use `docs/architecture/TEMPLATE.md` for new bounded-context or feature-loop specifications.

## 3. Event-First Slice

The smallest useful feature slice crosses all required layers:

```text
feature action
  -> command
  -> decision model
  -> domain event
  -> local or backend event store
  -> sync envelope and cursor where applicable
  -> projection/read model
  -> Flow/observable frontend state
  -> adaptive UI
```

Do not call a loop complete when it stops at storage or an API response but leaves the frontend on a separately mutated state path. Likewise, do not build UI-only mock state when the agreed loop requires durable domain behavior.

Ephemeral UI behavior such as hover, focus, animation progress, and unsubmitted text does not require durable events.

## 4. Repository Constraints

- Respect `apps -> shared -> feature -> core` and isolate concrete adapters in `integration/*`.
- Keep `commonMain` platform-neutral.
- Use canonical grocery models; raw provider payloads remain inside integrations.
- Apply allergen and dietary hard constraints before recommendation scoring.
- Preserve offer freshness, provenance, promotion conditions, and normalized quantities.
- Use `./gradlew`, not system Gradle.
- Prefer the latest stable compatible dependencies; document justified beta or release-candidate exceptions.

## 5. Commit and Pull Request Scope

Each request gets one `codex/*` branch and one pull request unless the user explicitly requests a different grouping.

Commit subjects are imperative, plain, and scoped:

```text
Define grocery recommendation events
Add offer comparison projection
Sync shopping list events to frontend
```

Before committing:

1. inspect `git status`;
2. inspect the unstaged and staged diffs;
3. stage explicit paths;
4. run required verification;
5. confirm no credentials, personal data, provider secrets, unrelated changes, or accidental generated files are included.

## 6. Verification Matrix

| Change | Minimum checks |
|---|---|
| Documentation only | `git diff --check` |
| Core shared logic | affected module `allTests` plus `lineCountCheck` |
| Event schema | serialization, backward-compatibility, projection rebuild, and sync tests |
| Shared app wiring | core tests, `:shared:app:allTests`, and `lineCountCheck` |
| Frontend event sync | reconnect, duplicate, ordering, cursor resume, and UI-state tests |
| Compose web | relevant tests plus Wasm browser development distribution |
| Backend/API | domain, adapter, migration, and API-contract tests |
| Recommendation rules | hard-filter, scoring-version, explanation, evidence, and confidence tests |
| Comparison rules | quantity, currency, promotion, missing-data, and freshness tests |
| Recipe/list rules | scale, conversion, substitution, estimate, and consolidation tests |
| Broad dependency/build change | full repository quality gate |

Use fixed clocks, seeded fixtures, and fake providers. Live-provider tests are opt-in.

## 7. Completion and Next Loop

A loop becomes `SATISFIED` only when:

- every agreed completion criterion is met;
- required tests and builds pass;
- event and sync behavior reaches the frontend projection when in scope;
- documentation reflects the actual implementation;
- known follow-up work is either defined as the next loop or explicitly closed;
- no unresolved product-critical question is hidden in implementation assumptions.

## 8. Stall and Escalation

Stop and request direction when:

- a product-critical choice remains unresolved;
- a provider license or API restriction blocks the intended data use;
- a dependency requires an unjustified unstable release;
- a storage choice would prevent safe offline or multi-device event sync;
- personal data, precise location, or secrets would need to be committed;
- the requested feature requires violating module or dietary-safety rules;
- the same test failure remains after reasonable repeated diagnosis.

Report the exact blocker, affected files, evidence, smallest needed decision, and recommended options.
