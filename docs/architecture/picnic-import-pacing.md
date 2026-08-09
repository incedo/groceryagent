# Picnic Import Pacing

**Status:** SATISFIED
**Last updated:** 2026-08-09
**Depends on:** Batch product importer, import manifest sharding

## 1. Overview

Sequential product import currently starts the next Picnic request immediately. Remaining manifest
shards need an explicit inter-product delay so one-at-a-time Jobs do not become request bursts.

## 2. Scope

### In scope

- Configure an inter-product delay through `PICNIC_REQUEST_DELAY_MILLIS`.
- Default product imports to 3,000 milliseconds between products.
- Apply pacing after successful, duplicate, missing, and failed product attempts.
- Skip the delay after the final product and for `history-only` imports.
- Keep the delay cancellation-aware and deterministically testable.
- Document the operational value used for homelab shard runs.

### Out of scope

- Parallel provider calls, adaptive rate limiting, jitter, provider retries, or automatic Job runs.
- Changing product, offer, price, event, or projection contracts.
- Treating unavailable historical products as successful imports.

## 3. Target Flow

```text
product attempt
  -> result recorded
  -> configured cancellable delay when another product remains
  -> next product attempt
  -> existing event store and projections
```

## 4. Decisions and Invariants

- The configured delay is a non-negative number of milliseconds.
- Product imports default to 3,000 milliseconds.
- A duplicate command is also paced so a resumed shard cannot create a burst.
- Cancellation during the delay terminates the Job normally through coroutine cancellation.
- Historical price ingestion remains local to PostgreSQL and receives no artificial delay.

No new events, IDs, migrations, projections, sync behavior, provider payloads, or dependencies are
introduced.

## 5. Verification

Deterministic tests inject a fake pacing function and assert the exact number of boundaries for
successful and mixed-result imports. Settings tests cover defaults, override, zero, and invalid
values.

```shell
./gradlew :apps:importer:test lineCountCheck --no-daemon
git diff --check
```

## 6. Completion Criteria

- [x] Product attempts are separated by the configured cancellable delay.
- [x] The default and environment override are validated.
- [x] History-only behavior remains unchanged.
- [x] Homelab run instructions state the selected delay.
- [x] Targeted tests and line-count checks pass.

## 7. Next Loop

Evaluate bounded retry and unavailable-product classification from observed shard results before
automating further provider imports.
