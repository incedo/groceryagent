# [Feature or Bounded Context Name]

**Status:** DRAFT
**Last updated:** YYYY-MM-DD
**Depends on:** None

## 1. Overview

Describe the grocery, recipe, recommendation, comparison, or shopping-list problem and whether the feature is local-only, local-first with optional sync, or backend-first.

## 2. Scope

### In scope

-

### Out of scope

-

## 3. Current State

- Current modules and files:
- Current data and provider shape:
- Current events and projections:
- Known limitations:
- Existing fixtures:

## 4. Target Architecture

```text
feature action
  -> command
core decision model
  -> domain event
event store
  -> local/backend sync envelope
projection
  -> Flow/observable frontend state
adaptive feature UI
```

Identify the source of truth and explain how events reach frontend projections. For local-first behavior, the local event store is the canonical client-side source of truth.

## 5. Canonical Models and IDs

| Value | Type | Validation | Notes |
|---|---|---|---|
| `{Context}Id` | value class | Stable and non-blank | Survives sync and import |
| `DeviceId` | value class | Stable per installation | Event producer identity |
| `ClientEventId` | value class | Globally unique | Idempotent sync |

Document product, offer, recipe, ingredient, quantity, money, source, region, freshness, and confidence fields used by the feature.

## 6. Commands

| Command | Fields | Decision inputs | Emits |
|---|---|---|---|
| `Create...` | | | |
| `Update...` | | | |
| `Remove...` | | | |

## 7. Events

| Event | Tags or stream | Payload | Trigger |
|---|---|---|---|
| `{Thing}Created` | `thing:{id}` | | Create command |
| `{Thing}Updated` | `thing:{id}` | | Update command |
| `{Thing}Removed` | `thing:{id}` | tombstone | Remove command |

Event metadata covers event ID, schema version, local sequence, producer/device ID, occurred time, optional user ID, correlation and causation IDs, and sync status.

## 8. Decision Models and Invariants

| Decision model | Reads | Enforces |
|---|---|---|
| `{Thing}Decision` | relevant events | existence and valid transition |

Include dietary safety, provider freshness, quantity compatibility, and concurrency rules where relevant.

## 9. Projections and Frontend State

| Projection/read model | Source events | Frontend consumer | Rebuild behavior |
|---|---|---|---|
| `{Thing}ListItem` | | list screen | |
| `{Thing}Detail` | | detail screen | |
| `{Thing}Summary` | | dashboard | |

Define how the frontend observes projections, handles loading and sync lifecycle events, resumes from a cursor, and avoids a parallel mutable source of truth.

## 10. Sync

- Local and backend sources of truth:
- Push and pull protocol:
- Event ordering and cursor:
- Idempotency and duplicate handling:
- Offline writes and reconnect:
- Conflict resolution:
- Tombstones:
- Schema evolution:
- Retry and failure behavior:
- Frontend update path:

## 11. Provider Data

- Provider and license:
- Attribution:
- Region coverage:
- Fetch and cache policy:
- Freshness window:
- Rate limits:
- Mapping to canonical models:
- Unknown and conflicting-data behavior:
- Redacted, license-compatible fixtures:

## 12. Recommendation, Comparison, or Recipe Rules

- Hard constraints:
- Scoring formula and version:
- Explanation and evidence:
- Quantity and currency normalization:
- Promotion handling:
- Recipe scaling and rounding:
- Substitutions:
- Saved snapshot behavior:

Remove irrelevant fields only after confirming they do not apply.

## 13. Dependency Decisions

- New or upgraded dependencies:
- Official latest stable version checked on:
- Compatibility constraints:
- Beta or release-candidate exception and stable-exit trigger:

## 14. UI and Testing

- Feature screens:
- Compact, medium, and expanded coverage:
- Touch, pointer, and keyboard coverage:
- Command and decision tests:
- Event serialization tests:
- Store and projection rebuild tests:
- Sync duplicate, ordering, offline, cursor, and reconnect tests:
- Provider mapping fixture tests:
- Visual or browser coverage:
- Exact verification commands:

## 15. Completion Criteria

- [ ] Scope and out-of-scope behavior are explicit.
- [ ] Product-critical questions are resolved.
- [ ] Contracts live in the correct core module.
- [ ] Domain changes emit versioned events.
- [ ] Events persist and sync idempotently where required.
- [ ] Projections rebuild deterministically.
- [ ] Frontend state updates from projections or event streams.
- [ ] Raw provider types do not escape integrations.
- [ ] Dietary safety and unknown-data behavior are tested.
- [ ] Adaptive and multimodal UI behavior is verified.
- [ ] Documentation and exact verification commands are current.
- [ ] The next loop is defined or the bounded slice is explicitly complete.

## 16. Open Questions

- **Q-1:** [Question] — **Decision:** Pending

## 17. Next Loop

- Next spec:
- Reason:
