# Picnic Import Failure Diagnostics

**Status:** SATISFIED
**Last updated:** 2026-08-09
**Depends on:** Batch product importer, Picnic import pacing

## 1. Overview

Historical Picnic product IDs can fail while a manifest continues successfully for other products.
The importer currently reduces every exception to `FAILED`, which prevents distinguishing unavailable
legacy products from authentication, rate-limit, mapping, provider, or persistence failures.

## 2. Scope

### In scope

- Attach a structured, safe diagnostic to every failed product result.
- Classify Picnic HTTP status codes, exhausted current/legacy routes, mapping failures, and unexpected
  exception types.
- Log route generation, status code, and typed reason for each failed route attempt.
- Give provider `null` results an explicit not-found classification.
- Keep processing later products in the same manifest and preserve the existing non-zero Job result.
- Cover classification and redaction behavior with deterministic tests.

### Out of scope

- Logging exception messages, response bodies, request headers, tokens, auth-file values, or personal
  order data.
- Persisting operational failures as catalog domain events.
- Retrying, searching for replacement products, or changing provider request pacing.
- Reprocessing shards that already completed.

## 3. Target Flow

```text
product import attempt
  -> success, duplicate, or not found
  -> typed exception classification on failure
  -> safe single-line product result
  -> continue after configured pacing delay
```

## 4. Decisions and Invariants

- Diagnostics are operational metadata, not grocery-domain state, and therefore do not enter the
  catalog event store or projections.
- Known Picnic failures expose only enum-like categories, numeric HTTP status, and current/legacy
  route generation.
- Unknown failures expose only their exception type, never `message`, cause text, or stack trace in
  the per-product result.
- A compatibility failure retains both route attempts so discontinued products can be separated
  from rate limiting or authentication failures.
- Existing `IMPORTED`, `ALREADY_IMPORTED`, `NOT_FOUND`, and `FAILED` statuses and exit behavior remain
  compatible.

No new dependencies, events, migrations, projections, sync contracts, or provider calls are added.

## 5. Verification

```shell
./gradlew :apps:importer:test lineCountCheck --no-daemon
git diff --check
```

Tests cover a missing provider result, current/legacy route exhaustion, direct rate limiting,
mapping incompatibility, unexpected exceptions, and exclusion of sensitive exception text.

## 6. Completion Criteria

- [x] Every unsuccessful product line contains a stable failure category.
- [x] Compatibility failures contain current and legacy route attempts.
- [x] Direct API status codes distinguish auth, rate limiting, validation, conflicts, and servers.
- [x] Unknown exceptions expose their type but no untrusted message.
- [x] Existing batch continuation, pacing, and exit semantics remain unchanged.
- [x] Targeted tests and line-count checks pass.

## 7. Next Loop

Use collected categories to decide whether bounded retries, replacement lookup, or a durable import
operations projection is justified. Do not add those behaviors without a separate agreed loop.
