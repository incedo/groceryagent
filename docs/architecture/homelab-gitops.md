# Homelab GitOps

The homelab deployment is reconciled by ArgoCD from:

```text
repo: https://github.com/incedo/groceryagent.git
path: k8s/homelab
```

This repository owns the Kubernetes application contract:

```text
Namespace: grocery-automate
Image:     registry.home.intelliworks.nl:5000/grocery-automate/catalog-service:main
Importer:  registry.home.intelliworks.nl:5000/grocery-automate/catalog-importer:main
Hosts:     grocery.home.intelliworks.nl
           grocery-automate.home.intelliworks.nl
```

Database state is intentionally not deployed by this repository. The platform
repository owns the shared PostgreSQL VM and creates the Kubernetes Secret that
the app consumes:

```text
Secret: grocery-automate-database
Keys:   DATABASE_URL
        DATABASE_USER
        DATABASE_PASSWORD
```

The database URL points at the shared homelab PostgreSQL host IP:

```text
jdbc:postgresql://192.168.68.22:5432/grocery_automate
```

DNS is requested from this app repository with ExternalDNS `DNSEndpoint`
resources. The records point at edge Traefik on `192.168.68.21`; edge Traefik
then forwards the hostnames to k3s Traefik.

The `catalog-importer` is deployed as a suspended CronJob. Keeping `suspend: true` is the safe
default: merges, image publication, and ArgoCD reconciliation do not create importer Jobs. Each
reviewed run is a named one-off Job whose name overrides `batchId` for idempotent retries.

Follow the [Catalog Importer CronJob Runbook](../operations/catalog-importer-cronjob.md) for
preflight checks, mode selection, run-specific manifest delivery, monitoring, verification,
failure recovery, and cleanup. In particular, never use the plain `create job --from=cronjob`
command for historical order data: the default mode makes Picnic product requests. Large generated
history manifests also exceed the Kubernetes ConfigMap limit and require a separately reviewed
read-only delivery mechanism before a Job may start.
