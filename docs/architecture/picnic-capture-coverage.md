# Picnic Capture Coverage Expansion

**Status:** SATISFIED
**Last updated:** 2026-08-02
**Depends on:** Sanitized Picnic capture fixtures, JVM code coverage

## 1. Overview

Exercise the production catalog object mapper against the distinct product-detail layouts already present in the local Picnic Android 1.239.3 capture archive.

## 2. Scope

### In scope

- Deterministically select unique successful product-detail responses from the ignored local flow archive.
- Sanitize each response independently with a pseudonymized primary product ID.
- Commit only sanitized response derivatives that pass the existing sensitive-data scanner.
- Assert typed product, dietary-safety, nutrition, information-section, and related-product outcomes.
- Measure the resulting JVM line and branch coverage and raise gates only to a demonstrated non-regression floor.

### Out of scope

- Committing raw flow data, headers, authentication material, or account payloads.
- Adding cart, account, delivery, wallet, or checkout fixtures before typed models exist.
- Replaying writes against the live Picnic API.
- Creating assertions whose only purpose is executing a line without validating behavior.

## 3. Starting State

- Six successful product-detail responses exist in the ignored local flow archive.
- The responses include a duplicate and several materially different server-driven UI trees.
- One product-detail response and one minimized search response are currently committed as sanitized fixtures.
- The measured JVM baseline is 93.47% line coverage and 62.21% branch coverage.

## 4. Safety and Provenance

- Raw inputs remain under `/Users/kees/data/projects/picnic-api-discovery/captures` and stay ignored.
- Sanitization drops action, analytics, mutation, callback, authentication, session, device, account, address, and contact-data subtrees or keys.
- Product IDs and opaque identifiers are deterministically pseudonymized.
- Every committed fixture is recursively checked for sensitive key patterns and token, contact, postcode, original product-ID, and long-identifier values.

## 5. Verification

```shell
./gradlew :integration:picnic-client:jvmTest \
  :integration:picnic-client:koverLog \
  :integration:picnic-client:koverVerify \
  lineCountCheck
./gradlew check
git diff --check
```

## 6. Completion Criteria

- [x] Only unique product-detail response variants are emitted.
- [x] Every new fixture passes the sensitive-data scanner.
- [x] Tests assert meaningful typed outcomes from every variant.
- [x] JVM coverage increased to 94.20% lines and 64.66% branches.
- [x] Coverage gates now enforce 93% lines and 64% branches.
- [x] JVM, iOS Simulator, and Wasm checks pass.

## 7. Next Loop

Add capture-derived fixtures for other endpoint families only after their canonical typed models and privacy requirements are agreed.
