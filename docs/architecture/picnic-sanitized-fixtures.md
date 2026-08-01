# Sanitized Picnic Capture Fixtures

**Status:** SATISFIED
**Last updated:** 2026-08-02
**Depends on:** `docs/architecture/picnic-catalog-object-model.md`

## Goal

Exercise the production Picnic catalog mappers against response structures captured from Picnic Android 1.239.3 without committing raw traffic, authentication material or personal account data.

## Inputs and Provenance

- Discovery repository commit: `aacd26b4d30f6004445fb058541b76648aa7d9ca`.
- Product response source SHA-256: `2488f5a4363f0e47d8a6d69dad2e81199ef32eb8ead8ab4646cb4a2d0bfded80`.
- Flow collection source SHA-256: `646e2cd81a18eee1784e031175067d8f4a7900d28de7a25ad6775575f1ca6d32`.
- Source captures remain ignored in `/Users/kees/data/projects/picnic-api-discovery/captures`.

Hashes establish local provenance without copying or exposing source contents.

## Sanitization Rules

The deterministic generator:

- selects only response bodies, never request headers or flow envelopes;
- removes scripts, actions, mutations, callbacks and analytics subtrees;
- removes keys associated with authentication, tokens, sessions, devices, users, customers, addresses and contact details;
- replaces JWT-, email-, phone- and postcode-like values;
- deterministically pseudonymizes product IDs, UUIDs and long hashes;
- replaces search product names with fixture names;
- reduces search data to the captured `sellingUnit` objects needed by the mapper;
- retains product-detail layout nodes required for product, price, allergen, nutrition and information extraction.

The committed outputs contain one minimized search response and seven unique product-detail response shapes, approximately 887 KB combined. They are test derivatives, not reusable provider datasets.

## Automated Validation

`SanitizedCaptureContractTest`:

- passes every fixture through the production `PicnicClient` and catalog mappers;
- checks search product count, IDs, names and integer-cent prices;
- checks product name, price, image, information sections, allergens and nutrition;
- checks every distinct product layout produces a typed product with pseudonymized related-product IDs;
- verifies that the captured layout without nutrition remains explicitly unknown rather than receiving invented values;
- verifies committed product fixtures are unique;
- recursively rejects sensitive key patterns;
- rejects email, JWT, phone, postcode, original product-ID and long-hash patterns.

Cross-platform synthetic contract tests continue to run on JVM, iOS and Wasm. The sanitized resource test is JVM-specific because it loads committed resource files; it exercises the same `commonMain` production mapper.

## Regeneration

```shell
python3 tools/sanitize_picnic_fixtures.py \
  --product-input /Users/kees/data/projects/picnic-api-discovery/captures/probe/product_details.json \
  --flows-input /Users/kees/data/projects/picnic-api-discovery/captures/flows.json \
  --output-dir integration/picnic-client/src/jvmTest/resources/picnic
```

Review regenerated fixture diffs and run:

```shell
./gradlew :integration:picnic-client:jvmTest
./gradlew check
git diff --check
```

Never weaken the scanner merely to accept newly generated data. Investigate and extend sanitization first.

## Next Loop

Add separately minimized and sanitized fixtures when typed cart, account, delivery, wallet and checkout-preflight models are introduced. Do not create a general archive of captured account responses.
