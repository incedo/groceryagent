# JVM Code Coverage

**Status:** SATISFIED
**Last updated:** 2026-08-02
**Depends on:** Picnic client contract tests

## 1. Overview

Add reproducible source-code coverage reporting for Kotlin code without weakening the existing cross-platform test gate.

## 2. Scope

### In scope

- Apply Kover to `integration/picnic-client`.
- Generate HTML and XML reports during the module `check` task.
- Record a measured JVM baseline before introducing a non-regression threshold.
- Document exactly what the coverage percentage includes and excludes.

### Out of scope

- Treating JVM coverage as proof that iOS or Wasm behavior works.
- Live Picnic API tests or provider credentials.
- Raising coverage by adding unrelated tests.
- Aggregating modules that do not exist yet.

## 3. Starting State

- The Picnic client has deterministic common tests and JVM-only sanitized-fixture tests.
- Common tests also execute on iOS Simulator and Wasm through the normal quality gate.
- No source coverage instrumentation or numeric baseline existed before this loop.

## 4. Dependency Decision

- Tool: JetBrains kotlinx-kover Gradle plugin.
- Version: `0.9.8`, the latest stable version documented by the official project on 2026-08-02.
- Compatibility: Kover supports Kotlin Multiplatform but collects coverage only from JVM test execution. Version `0.9.8` passes on the repository's Gradle `9.6.1`; revalidate or upgrade Kover before adopting Gradle 10 because the plugin currently emits Gradle's deprecated project-dependency-notation warning.
- Non-stable dependencies: none.

## 5. Reporting and Enforcement

- `:integration:picnic-client:koverHtmlReport` creates a locally browsable report.
- `:integration:picnic-client:koverXmlReport` creates a machine-readable report for CI.
- `:integration:picnic-client:koverLog` prints the current metrics.
- The module `check` task generates HTML and XML reports and verifies the recorded baseline.
- The initial baseline was 93.47% line coverage and 62.21% branch coverage.
- Capture-derived product-layout tests increased coverage to 94.20% lines and 64.66% branches.
- The Ktor transport contract increased coverage to 95.44% lines and 64.94% branches.
- Current-first routing, compatibility, and legacy-mapper contracts now cover 95.32% of lines (`1141/1197`) and 65.46% of branches (`580/886`) across the expanded production surface.
- The enforced non-regression floors are 95% line coverage and 64% branch coverage, leaving a small margin for compiler mapping changes.
- Threshold increases require tests that exercise intended behavior; production code or fixtures must not be excluded merely to inflate the metric.

## 6. Limitations

Kover instruments JVM bytecode. Its report includes `commonMain` and JVM production code exercised by JVM tests, but not iOS- or Wasm-specific coverage. Passing iOS and Wasm tests remains a separate requirement.

Coverage measures execution, not assertion quality, endpoint validity, food safety, or correctness. Contract tests and the repository verification matrix remain authoritative for required behaviors.

## 7. Verification

```shell
./gradlew :integration:picnic-client:koverLog \
  :integration:picnic-client:koverHtmlReport \
  :integration:picnic-client:koverXmlReport \
  :integration:picnic-client:koverVerify \
  lineCountCheck
./gradlew check
```

## 8. Completion Criteria

- [x] Kover uses the centrally managed latest stable compatible version.
- [x] HTML and XML reports are generated from deterministic JVM tests.
- [x] The measured line and branch baseline is documented.
- [x] Coverage verification runs as part of `check`.
- [x] Existing JVM, iOS Simulator, and Wasm tests still pass.
- [x] README and tech-stack documentation describe report commands and scope.

## 9. Next Loop

Add root aggregation when a second module with JVM production code is introduced.
