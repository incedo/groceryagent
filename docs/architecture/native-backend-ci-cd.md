# Native Backend CI/CD Pipeline

**Status:** SATISFIED
**Last updated:** 2026-08-04
**Depends on:** Native event-sourced catalog backend

## 1. Overview

Deliver every backend change through one gated GitHub Actions pipeline. Pull requests run the full
multiplatform quality gate, build the GraalVM microservice image, start it with PostgreSQL, and
exercise its API without publishing. Successful pushes to `main` and version tags publish the exact
image that passed the container smoke test to GitHub Container Registry (GHCR).

## 2. Scope

### In scope

- Full repository tests and coverage before the backend image gate.
- One native-image build reused for PostgreSQL smoke verification and publication.
- Liveness, readiness, persisted-search, and cursor-feed smoke requests.
- Non-root runtime verification.
- Immutable commit-SHA image tags on every eligible push.
- A mutable `main` tag for successful main builds.
- Semantic version and `latest` tags for successful `v*` Git tags.
- GHCR authentication through the short-lived GitHub Actions token.
- Pull-request builds that never receive package-write permission or publish images.

### Out of scope

- Deployment to Kubernetes, a VM, or a cloud runtime; no deployment target is selected.
- Multi-architecture manifests; each workflow currently builds Linux amd64 on `ubuntu-latest`.
- Production database migrations, secrets, rollback orchestration, signing, and promotion between
  environments.
- Provider credentials or live Picnic requests in CI.

## 3. Pipeline

```text
pull request / main push / v* tag / manual run
  -> macOS ./gradlew check ----------------------+
  -> Linux native Docker build                   |
  -> Docker Compose PostgreSQL startup
  -> API and non-root smoke checks
  -> one-day tested-image artifact
  -> read-only artifact download/load verification
       |-> pull request/manual: stop
       |-> main push: GHCR sha + main tags
       +-> v* tag: GHCR sha + version + latest tags
```

The test and native jobs run in parallel for lower pull-request latency. Every run downloads and
loads the one-day image artifact in a read-only verification job. Publication depends on the full
test gate and that artifact verification, then loads the same artifact rather than rebuilding it.

## 4. Image Contract

- Registry: `ghcr.io/<owner>/grocery-catalog-service`.
- Immutable tag: `sha-<full-git-sha>`.
- Main-channel tag: `main`.
- Release tags: the Git tag without its leading `v`, plus `latest`.
- Source revision is traceable through the immutable tag and workflow run.
- Runtime remains the non-root `grocery` user.
- Images contain no database password, Picnic environment file, or provider token.

## 5. Permissions and Safety

- Workflow-level permission is read-only repository content.
- Only the push-only publication job receives `packages: write`.
- Registry login and push steps run only for `push` events on `main` or `v*` tags.
- Pull requests, including forks, build, test, and validate the image handoff but cannot publish.
- GitHub masks the short-lived `GITHUB_TOKEN`; it is piped directly into `docker login` and is not
  written to the workspace or image.
- The image handoff uses
  [`actions/upload-artifact@v7.0.1`](https://github.com/actions/upload-artifact/releases/tag/v7.0.1)
  and
  [`actions/download-artifact@v8.0.1`](https://github.com/actions/download-artifact/releases/tag/v8.0.1),
  the latest stable releases verified from their official release pages on 2026-08-04. Download
  digest mismatch fails closed.
- Provider tests remain deterministic and credential-free.

## 6. Verification

Local equivalence:

```shell
./gradlew check
docker build --tag grocery-catalog-service:ci .
CATALOG_SERVICE_IMAGE=grocery-catalog-service:ci docker compose up --no-build --wait
docker compose exec -T catalog-service /app/grocery-catalog-service --healthcheck
docker compose down --volumes
```

Workflow validation also checks the rendered Compose configuration and observes one pull-request
run where no registry publication step executes.

## 7. Completion Criteria

- [x] Pull requests run the complete Gradle and native backend gates.
- [x] The pipeline builds the GraalVM microservice once per workflow run.
- [x] The tested image runs with PostgreSQL and passes API health/query checks.
- [x] A separate read-only job downloads and validates the tested image artifact.
- [x] The runtime is verified as non-root.
- [x] Pull requests and manual runs cannot publish packages.
- [x] Successful main pushes publish SHA and `main` tags.
- [x] Successful `v*` tags publish SHA, version, and `latest` tags.
- [x] GHCR uses only the scoped GitHub Actions token.
- [x] README and architecture documentation describe triggers, tags, and limitations.

Pull request run `30905657416` verified the final workflow topology: the 102-task multiplatform gate
passed in 5m16s, the native build/PostgreSQL smoke and artifact upload passed in 4m40s, and the
read-only artifact download/load check passed in 14s. The package publication job was skipped as
required for a pull request. Its GHCR push path becomes active only after this pipeline reaches
`main` or a matching version tag.

## 8. Next Loop

Select a production runtime and define environment promotion, signed images and attestations,
database migration policy, secrets, observability, rollback, and deployment health gates.
