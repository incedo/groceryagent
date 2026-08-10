# Catalog Importer CronJob Runbook

This runbook describes the explicit operator action for importing a reviewed manifest into the
homelab PostgreSQL database. Merging to `main`, publishing the importer image, and ArgoCD sync do
not start an import. The CronJob must remain suspended; every import is a named one-off Job.

## Safety model

- `CronJob/catalog-importer` has `spec.suspend: true` and must stay suspended.
- Import Jobs use `backoffLimit: 0`; a non-zero exit is reported for operator review and never
  retries an entire shard automatically.
- `history-only` writes historical price events without loading Picnic credentials or making
  retailer requests.
- `products-and-history` reads every listed product from Picnic before storing product, current
  offer, and historical price events. It defaults to a three-second delay between products; use it
  only with an approved account and pacing policy.
- `search-replacements` searches by historical product name and package, imports only an
  unambiguous current product, and records its old ID. It performs one search and at most one detail
  request per item, sequentially, with the same configured delay between items.
- A Job name becomes `IMPORT_BATCH_ID`. Reusing the same name after a failed attempt preserves
  idempotency for product imports; historical observations are idempotent by observation ID.
- Deleting a Job stops future work but does not roll back events already committed to PostgreSQL.

## 1. Preconditions

Set an unambiguous run name and use an absolute manifest path:

```shell
export IMPORT_NAMESPACE=grocery-automate
export IMPORT_RUN=history-YYYYMMDD-NNN
export IMPORT_JOB=catalog-import-$IMPORT_RUN
export IMPORT_CONFIG=catalog-import-manifest-$IMPORT_RUN
export IMPORT_FILE=/absolute/path/import-products-with-history.json
export IMPORT_MODE=history-only
```

Validate local input and cluster access before creating anything:

```shell
test -f "$IMPORT_FILE"
./gradlew :apps:importer:run \
  --args="--validate-manifest $IMPORT_FILE" --no-daemon

kubectl auth can-i create jobs.batch -n "$IMPORT_NAMESPACE"
kubectl auth can-i create configmaps -n "$IMPORT_NAMESPACE"
kubectl -n "$IMPORT_NAMESPACE" get cronjob catalog-importer \
  -o jsonpath='suspend={.spec.suspend}{" backoffLimit="}{.spec.jobTemplate.spec.backoffLimit}{"\n"}'
kubectl -n "$IMPORT_NAMESPACE" get secret grocery-automate-database
kubectl -n "$IMPORT_NAMESPACE" get jobs
```

The query must print `suspend=true backoffLimit=0`. Stop if a previous importer Job is active,
required secrets are missing, the manifest is unreviewed, or the selected mode is not intentional.

For `products-and-history` or `search-replacements`, also verify
`Secret/grocery-automate-picnic` and obtain explicit approval
for the account, delay, retry, and maximum-request policy. The homelab CronJob sets
`PICNIC_REQUEST_DELAY_MILLIS=3000`; retain or deliberately increase it for sequential shard runs.

### Split a large product batch into bounded manifests

Generate deterministic, non-overlapping shards in a new private directory before delivery. This
example keeps at most 50 products in each shard and performs no provider or database requests:

```shell
export IMPORT_SHARD_DIR=/absolute/private/path/import-shards-$IMPORT_RUN
./gradlew :apps:importer:run \
  --args="--split-manifest $IMPORT_FILE $IMPORT_SHARD_DIR 50" --no-daemon
```

The directory contains ordered `manifest-part-NNN.json` files and `manifest-index.json`. Verify the
exact source and shard bytes before using them:

```shell
test "$(shasum -a 256 "$IMPORT_FILE" | awk '{print $1}')" = \
  "$(jq -r '.sourceSha256' "$IMPORT_SHARD_DIR/manifest-index.json")"
(
  cd "$IMPORT_SHARD_DIR"
  jq -r '.shards[] | "\(.sha256)  \(.fileName)"' manifest-index.json |
    shasum -a 256 -c -
)
```

Do not edit a generated shard. Regenerate the complete directory from the reviewed source instead.
Run one shard at a time in index order and wait for database verification before submitting the
next. Reuse the same shard and batch ID when resuming a failed run.

The checked-in `config/picnic-failed-product-replacements.json` is a sanitized technical dataset:
it contains only old product ID, historical name/package, and optional image ID. It contains no
orders, prices, purchase counts, or purchase times. Review and shard it before a K3s run. Merely
merging this file does not create a Job or unsuspend the CronJob.

## 2. Manifest delivery boundary

The checked-in CronJob mounts a ConfigMap. Kubernetes ConfigMaps are limited to 1 MiB, so use this
path only for a manifest comfortably below that boundary:

```shell
wc -c "$IMPORT_FILE"
```

Use 900,000 bytes as the operational ceiling to leave room for Kubernetes object metadata. The
partial 284-order history manifest generated on 2026-08-09 is 7,016,062 bytes and therefore must
not be put in a ConfigMap. Full order history will be larger.

For shard sets, inspect every generated file:

```shell
find "$IMPORT_SHARD_DIR" -name 'manifest-part-*.json' -type f -exec wc -c {} +
```

For a large manifest or shard, stop here until a reviewed delivery mechanism mounts it read-only at
`/app/config/import-products.json`, such as a dedicated PVC or authenticated private object-store
init flow. Do not commit private generated manifests, split them into Git-managed ConfigMaps, place
them in Secrets, or paste them into Job arguments.

For a small manifest, create a run-specific ConfigMap. ArgoCD does not manage this unique name:

```shell
kubectl -n "$IMPORT_NAMESPACE" create configmap "$IMPORT_CONFIG" \
  --from-file=import-products.json="$IMPORT_FILE"
```

## 3. Create one history-only Job

Generate the Job from the suspended CronJob, replace its manifest ConfigMap, and inject the mode
before submitting it. This pipeline creates no intermediate file containing the manifest:

```shell
kubectl -n "$IMPORT_NAMESPACE" create job "$IMPORT_JOB" \
  --from=cronjob/catalog-importer --dry-run=client -o json |
jq --arg config "$IMPORT_CONFIG" --arg mode "$IMPORT_MODE" '
  (.spec.template.spec.volumes[] |
    select(.name == "import-manifest").configMap.name) = $config |
  (.spec.template.spec.containers[] | select(.name == "catalog-importer")) |= (
    .env = ((.env // []) |
      map(select(.name != "IMPORT_MODE" and .name != "PICNIC_ENV_FILE")) +
      [{"name":"IMPORT_MODE","value":$mode}]) |
    .volumeMounts = ((.volumeMounts // []) |
      map(select(.name != "picnic-environment")))
  ) |
  .spec.template.spec.volumes |=
    map(select(.name != "picnic-environment"))
' |
kubectl apply -f -
```

Immediately inspect the submitted contract. For `history-only`, the mode must be present and the
run-specific ConfigMap must be mounted, and `backoffLimit` must be zero:

```shell
kubectl -n "$IMPORT_NAMESPACE" get job "$IMPORT_JOB" -o yaml
test "$(kubectl -n "$IMPORT_NAMESPACE" get job "$IMPORT_JOB" \
  -o jsonpath='{.spec.backoffLimit}')" = "0"
```

Never run the plain `kubectl create job --from=cronjob/...` command for historical order data: the
default mode is `products-and-history`, which performs Picnic requests.

For `search-replacements`, retain the Picnic secret volume from the CronJob and override only the
manifest ConfigMap and mode. Do not use the history-only transformation above because that
deliberately removes Picnic authentication:

```shell
kubectl -n "$IMPORT_NAMESPACE" create job "$IMPORT_JOB" \
  --from=cronjob/catalog-importer --dry-run=client -o json |
jq --arg config "$IMPORT_CONFIG" '
  (.spec.template.spec.volumes[] |
    select(.name == "import-manifest").configMap.name) = $config |
  (.spec.template.spec.containers[] | select(.name == "catalog-importer")).env |=
    (map(select(.name != "IMPORT_MODE")) +
      [{"name":"IMPORT_MODE","value":"search-replacements"}])
' |
kubectl apply -f -
```

For a shard set, repeat this section manually in `manifest-index.json` sequence order. Set
`IMPORT_FILE`, `IMPORT_RUN`, `IMPORT_JOB`, and `IMPORT_CONFIG` from the selected shard and its batch
ID. Never create the next Job while another importer Job is active. Running the same shards later
in `products-and-history` mode safely retries historical observations by observation ID, but product
requests still require the separately approved account and pacing policy.

## 4. Monitor and verify

```shell
kubectl -n "$IMPORT_NAMESPACE" get pods -l job-name="$IMPORT_JOB" -w
kubectl -n "$IMPORT_NAMESPACE" logs -f job/"$IMPORT_JOB"
kubectl -n "$IMPORT_NAMESPACE" wait \
  --for=condition=complete job/"$IMPORT_JOB" --timeout=30m
kubectl -n "$IMPORT_NAMESPACE" get job "$IMPORT_JOB" -o jsonpath='{.status}{"\n"}'
```

Each product result is a single structured line with `product`, `status`, and `events`. Unsuccessful
results also include `failure_category`; provider HTTP failures include `http_status`, while
current/legacy compatibility failures include `route_attempts` such as
`CURRENT/404/ROUTE_UNAVAILABLE,LEGACY/410/ROUTE_UNAVAILABLE`. Unexpected failures expose only the
exception type. The importer deliberately never logs exception messages, provider response bodies,
request headers, or auth values in these result lines.

A successful process exit is necessary but not sufficient. Query a known product through the
backend projection and confirm its historical observations:

```shell
curl --fail --silent --show-error \
  'https://grocery.home.intelliworks.nl/api/v1/catalog/products/picnic:nl:s1001/price-history?limit=1000'
```

Record the Job name, manifest checksum, mode, image digest, observation count, start/end time, and
verification result in the operational change record. Never record credentials or raw order data.
Calculate the local checksum with `shasum -a 256 "$IMPORT_FILE"` before cleanup.

## 5. Failure and resume

Stop a running import with:

```shell
kubectl -n "$IMPORT_NAMESPACE" delete job "$IMPORT_JOB" --wait=true
```

Inspect the redacted logs and database/event counts before retrying. Recreate the Job with the same
`IMPORT_JOB` value and unchanged manifest to resume idempotently. Use a new Job name only for a
deliberately new observation batch. Never increase `backoffLimit` to automate a provider retry;
review the failed products and account state first.

## 6. Cleanup

After successful verification and the agreed retention window:

```shell
kubectl -n "$IMPORT_NAMESPACE" delete job "$IMPORT_JOB" --wait=true
kubectl -n "$IMPORT_NAMESPACE" delete configmap "$IMPORT_CONFIG"
kubectl -n "$IMPORT_NAMESPACE" get cronjob catalog-importer \
  -o jsonpath='{.spec.suspend}{"\n"}'
```

The final query must still print `true`. Cleanup removes only Kubernetes run objects; PostgreSQL
events and projections remain intact. Keep the private local manifest only as long as required by
the agreed retention policy, then move it to Trash.
