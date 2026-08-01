# Ktor Picnic Transport Contract

**Status:** SATISFIED
**Last updated:** 2026-08-02
**Depends on:** Picnic client, JVM code coverage

## 1. Overview

Prove that the concrete Ktor outbound adapter faithfully translates the provider-neutral Picnic HTTP port without accessing the live Picnic API.

## 2. Scope

### In scope

- Use Ktor `MockEngine` from the same stable Ktor 3.5.1 dependency line as the client.
- Exercise the production `KtorPicnicHttpTransport` class.
- Verify request method, URL, headers and absent/present body forwarding.
- Verify response status, repeated headers and binary body bytes.
- Verify transport failures propagate to the caller.
- Run the contract from `commonTest` across configured targets.

### Out of scope

- Live provider requests, DNS, TLS or socket behavior.
- Provider retry and timeout policy, which is not yet designed.
- HTTP status interpretation, which belongs to `PicnicRequester` tests.
- Repeated request headers, which the current `Map<String, String>` request port cannot represent.

## 3. Architecture

```text
PicnicHttpRequest
  -> KtorPicnicHttpTransport
  -> Ktor HttpClient(MockEngine)
  -> captured Ktor request + deterministic response
  -> PicnicHttpResponse
```

The mock engine replaces only network I/O. Request and response translation runs through the production adapter.

## 4. Dependency Decision

- Artifact: `io.ktor:ktor-client-mock`.
- Version: `3.5.1`, centrally inherited from the existing stable Ktor version.
- Official API verified: 2026-08-02.
- Non-stable dependency: none.

## 5. Verification

```shell
./gradlew :integration:picnic-client:allTests \
  :integration:picnic-client:koverLog \
  :integration:picnic-client:koverVerify \
  lineCountCheck
./gradlew check
git diff --check
```

## 6. Completion Criteria

- [x] The real Ktor adapter is executed by automated tests.
- [x] Method, URL, headers and present/absent bodies are asserted.
- [x] Status, repeated response headers and binary body bytes are asserted.
- [x] A mock-engine failure reaches the caller.
- [x] JVM, iOS Simulator and Wasm tests pass.
- [x] Coverage and line-count gates pass.

## 7. Next Loop

Add the HTTP/provider error matrix around `PicnicRequester`, including empty, malformed, `304`, `4xx`, `429` and `5xx` responses.
