# Catalog Importer CronJob Runbook

This runbook describes the explicit operator action for importing a reviewed manifest into the
homelab PostgreSQL database. Merging to `main`, publishing the importer image, and ArgoCD sync do
not start an import. The CronJob must remain suspended; every import is a named one-off Job.

## Safety model

- `CronJob/catalog-importer` has `spec.suspend: true` and must stay suspended.
- `history-only` writes historical price events without loading Picnic credentials or making
  retailer requests.
- `products-and-history` reads every listed product from Picnic before storing product, current
  offer, and historical price events. Use it only with an approved account and pacing policy.
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
  -o jsonpath='{.spec.suspend}{"\n"}'
kubectl -n "$IMPORT_NAMESPACE" get secret grocery-automate-database
kubectl -n "$IMPORT_NAMESPACE" get jobs
```

The suspend query must print `true`. Stop if a previous importer Job is active, required secrets are
missing, the manifest is unreviewed, or the selected mode is not intentional.

For `products-and-history`, also verify `Secret/grocery-automate-picnic` and obtain explicit approval
for the account, delay, retry, and maximum-request policy. The current importer is sequential but
does not yet impose a delay between Picnic product requests.

## 2. Manifest delivery boundary

The checked-in CronJob mounts a ConfigMap. Kubernetes ConfigMaps are limited to 1 MiB, so use this
path only for a manifest comfortably below that boundary:

```shell
wc -c "$IMPORT_FILE"
```

Use 900,000 bytes as the operational ceiling to leave room for Kubernetes object metadata. The
partial 284-order history manifest generated on 2026-08-09 is 7,016,062 bytes and therefore must
not be put in a ConfigMap. Full order history will be larger.

For a large manifest, stop here until a reviewed delivery mechanism mounts it read-only at
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
run-specific ConfigMap must be mounted:

```shell
kubectl -n "$IMPORT_NAMESPACE" get job "$IMPORT_JOB" -o yaml
```

Never run the plain `kubectl create job --from=cronjob/...` command for historical order data: the
default mode is `products-and-history`, which performs Picnic requests.

## 4. Monitor and verify

```shell
kubectl -n "$IMPORT_NAMESPACE" get pods -l job-name="$IMPORT_JOB" -w
kubectl -n "$IMPORT_NAMESPACE" logs -f job/"$IMPORT_JOB"
kubectl -n "$IMPORT_NAMESPACE" wait \
  --for=condition=complete job/"$IMPORT_JOB" --timeout=30m
kubectl -n "$IMPORT_NAMESPACE" get job "$IMPORT_JOB" -o jsonpath='{.status}{"\n"}'
```

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
deliberately new observation batch.

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
