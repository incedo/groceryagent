# Native Backend CI/CD Pipeline

**Status:** AGREED
**Last updated:** 2026-08-04
**Depends on:** Native event-sourced catalog backend

## 1. Overview

Deliver every code, build, deployment, or configuration change through one gated GitHub Actions
pipeline while keeping project-built images private. Documentation-only changes retain the two
required successful check contexts but skip Gradle, native builds, Docker, PostgreSQL, and image
publication. Code-bearing pull requests run the full multiplatform quality gate and build, start,
and exercise the GraalVM microservice with PostgreSQL on an isolated GitHub-hosted runner.
Successful code-bearing pushes to `main` and version tags run the same native checks on the homelab
runner, then publish the exact tested service and importer images only to the LAN registry at
`registry.home.intelliworks.nl:5000`.

## 2. Scope

### In scope

- Full repository tests and coverage before the backend image gate.
- One native-image build reused for PostgreSQL smoke verification and publication.
- One native importer image validated with a fixture manifest before publication.
- Liveness, readiness, persisted-search, cursor-feed, and non-root runtime checks.
- Immutable commit-SHA image tags on every eligible push.
- A mutable `main` tag for successful main builds.
- Semantic version and `latest` tags for successful `v*` Git tags.
- Publication from the LAN-connected, self-hosted `homelab` runner only.
- Isolation of pull-request code from the self-hosted LAN runner.
- Pull-request and manual builds that never publish images.
- Documentation-only branch pushes and pull requests complete lightweight required contexts
  without allocating macOS or native-build runners.

### Out of scope

- Publishing project images to GHCR, Docker Hub, or any other public registry.
- Deployment to Kubernetes, a VM, or a cloud runtime; no deployment target is selected.
- Registry TLS, authentication, public ingress, or availability changes.
- Registering or administering the homelab runner for this repository.
- Multi-architecture manifests; the current homelab runner builds Linux amd64.
- Production database migrations, secrets, rollback orchestration, signing, and promotion between
  environments.
- Provider credentials or live Picnic requests in CI.

## 3. Pipeline

```text
pull request / main push / v* tag / manual run
  -> classify changed paths
       |-> docs-only: lightweight required contexts; stop
       +-> code/config/build: macOS ./gradlew check
  -> native Docker build
       |-> pull request/manual: GitHub-hosted Ubuntu
       +-> main push/v* tag: self-hosted homelab
  -> Docker Compose PostgreSQL startup
  -> API and non-root smoke checks
       |-> pull request/manual: stop
       |-> main push: private-registry sha + main tags
       +-> v* tag: private-registry sha + version + latest tags
```

The native job starts only after the complete Gradle gate succeeds. Pull requests and manual runs
use GitHub-hosted Ubuntu so untrusted fork code never executes inside the LAN. Eligible pushes build
and test on the homelab runner, then tag and publish the already-tested local image without
rebuilding it or moving a private image through a GitHub workflow artifact.

Files below `docs/` and files whose names end in `.md` are documentation. Any other changed path,
including workflows, Kubernetes manifests, Dockerfiles, Gradle files, source, tests, fixtures, and
configuration, triggers the complete pipeline. Manual runs and version tags always run the complete
pipeline. The two lightweight documentation-only jobs deliberately keep the exact protected-branch
contexts `Multiplatform check` and `Build, verify, and deliver native backend`; using workflow path
filters would leave those required contexts pending and block a documentation pull request.

## 4. Image Contract

- Registry repository: `registry.home.intelliworks.nl:5000/grocery-automate/catalog-service`.
- Importer repository: `registry.home.intelliworks.nl:5000/grocery-automate/catalog-importer`.
- Immutable tag: `sha-<full-git-sha>`.
- Main-channel tag: `main`.
- Release tags: the Git tag without its leading `v`, plus `latest`.
- Source revision is traceable through the immutable tag and workflow run.
- Runtime remains the non-root `grocery` user.
- Images contain no database password, Picnic environment file, provider token, or registry secret.
- Public registries are permitted only as read-only sources for pinned upstream dependencies.

## 5. Private Boundary and Runner Requirements

- The registry is LAN-only and intentionally serves plain HTTP on port 5000. It must not receive
  public ingress or be treated as an insecure registry outside managed homelab Docker daemons.
- The repository must have access to a self-hosted runner labeled `homelab` with Docker, LAN DNS,
  and network access to the registry.
- Only trusted `push` events use the homelab runner; pull-request and manual events use isolated
  GitHub-hosted Ubuntu.
- That runner's Docker daemon must trust exactly `registry.home.intelliworks.nl:5000` as an insecure
  registry. This workflow does not weaken Docker trust settings itself.
- The registry currently accepts LAN-local pushes without a workflow credential. If authentication
  is introduced, use a scoped GitHub Actions secret and never write it to the workspace, image,
  artifact, or logs.
- If the runner or registry is unavailable, delivery remains failed or pending. It never falls back
  to a public registry.

## 6. Permissions and Safety

- Every job has read-only repository-content permission; no job receives `packages: write`.
- Registry reachability and the exact allowed host are checked immediately before any push.
- Push steps run only for `push` events on `main` or `v*` tags.
- Pull requests, including forks, build and smoke-test on GitHub-hosted Ubuntu but cannot publish.
- No container archive is uploaded to GitHub Actions artifacts.
- Provider tests remain deterministic and credential-free.
- Documentation-only changes cannot build or publish images and do not use the self-hosted runner.

## 7. Verification

Local build and smoke-test equivalence:

```shell
./gradlew check
docker compose config --quiet
docker build --tag grocery-catalog-service:ci .
docker build --file Dockerfile.importer --tag grocery-catalog-importer:ci .
docker run --rm grocery-catalog-importer:ci \
  --validate-manifest /app/import-products.example.json
CATALOG_SERVICE_IMAGE=grocery-catalog-service:ci docker compose up --no-build --wait
docker compose exec -T catalog-service /app/grocery-catalog-service --healthcheck
docker compose down --volumes
```

LAN-connected operators can verify registry reachability without publishing:

```shell
curl --fail --silent --show-error http://registry.home.intelliworks.nl:5000/v2/
```

Do not test this contract by pushing an unverified local image. Workflow validation must also
observe a pull-request run where the build and smoke test pass and no publication step executes.

## 8. Completion Criteria

- [x] Pull requests run the complete Gradle and isolated native backend gates.
- [x] The pipeline builds the GraalVM microservice once per workflow run.
- [x] The tested image runs with PostgreSQL and passes API health/query checks.
- [x] The importer image validates a versioned fixture manifest before it can be published.
- [x] The runtime is verified as non-root.
- [x] Pull requests and manual runs cannot publish images.
- [x] Project images and image archives are not published to GitHub or another public registry.
- [x] Successful main pushes are configured to publish the same image under SHA and `main` tags.
- [x] Successful `v*` tags are configured to publish the same image under SHA, version, and
  `latest` tags.
- [x] README, contributor rules, and architecture guidance define the private boundary.
- [x] Documentation-only changes preserve required contexts without running expensive code gates.

The loop becomes `SATISFIED` after a pull-request run proves the isolated native path and the first
eligible push proves this repository can schedule the `homelab` job, complete the native smoke test,
and publish privately. A missing repository runner assignment is an operational prerequisite, not
permission to use a public registry.

Pull request run `30923418466` proved the isolated path: the 102-task multiplatform gate passed in
6m30s and the native PostgreSQL/API/non-root smoke passed on GitHub-hosted `ubuntu-latest` in 4m48s.
The registry boundary and publication steps were both skipped as required for a pull request.

## 9. Next Loop

Select a production runtime and define environment promotion, signed images and attestations,
database migration policy, secrets, observability, rollback, and deployment health gates.
