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

The `catalog-importer` is deployed as a suspended CronJob. Before enabling or manually starting it,
replace the placeholder product values in `catalog-import-manifest` and provision the
platform-owned `grocery-automate-picnic` Secret with an `auth.env` key. The manifest and provider
secret are mounted read-only. Keeping `suspend: true` is the safe default: it prevents placeholder
imports and unreviewed retailer traffic. The generated Kubernetes Job name overrides `batchId`, so
pod retries are idempotent while each later scheduled Job records fresh observations. A batch may
be started explicitly after those prerequisites are met:

```shell
kubectl -n grocery-automate create job \
  --from=cronjob/catalog-importer catalog-import-YYYYMMDD
```
