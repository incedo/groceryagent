# Sanitized Picnic capture fixtures

These JSON files are deterministic, minimized-risk derivatives of responses observed with Picnic Android 1.239.3 and API 15. They contain one minimized search response and seven unique product-detail response shapes. They contain response bodies only: no request headers, flow envelopes, credentials, account records or raw capture files.

Discovery commit: `aacd26b4d30f6004445fb058541b76648aa7d9ca`.

Source SHA-256 values:

- product detail: `2488f5a4363f0e47d8a6d69dad2e81199ef32eb8ead8ab4646cb4a2d0bfded80`;
- flow collection: `646e2cd81a18eee1784e031175067d8f4a7900d28de7a25ad6775575f1ca6d32`.

The sanitizer removes script/action/analytics subtrees, drops keys associated with authentication, devices, sessions and personal contact/account data, replaces credential-like values, and deterministically pseudonymizes product IDs, UUIDs and long hashes. A JVM test scans every committed fixture again before exercising the production mappers.

Regenerate locally from the ignored discovery captures:

```shell
python3 tools/sanitize_picnic_fixtures.py \
  --product-input /Users/kees/data/projects/picnic-api-discovery/captures/probe/product_details.json \
  --flows-input /Users/kees/data/projects/picnic-api-discovery/captures/flows.json \
  --output-dir integration/picnic-client/src/jvmTest/resources/picnic
```

Do not commit the source captures. Review the generated diff and run `./gradlew :integration:picnic-client:jvmTest` before accepting regenerated fixtures.
