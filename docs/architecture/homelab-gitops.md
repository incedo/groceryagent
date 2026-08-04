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

The database URL points at the shared homelab PostgreSQL host:

```text
jdbc:postgresql://postgres.home.intelliworks.nl:5432/grocery_automate
```

DNS is requested from this app repository with ExternalDNS `DNSEndpoint`
resources. The records point at edge Traefik on `192.168.68.21`; edge Traefik
then forwards the hostnames to k3s Traefik.
