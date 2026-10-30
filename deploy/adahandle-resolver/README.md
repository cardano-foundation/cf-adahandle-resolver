# adahandle-resolver Helm chart

Deploys the ADA Handle Resolver. By default it deploys the resolver release the chart belongs to
(`appVersion`); set `image.tag` to deploy another one.

## Database

| Mode | Values | Database |
|---|---|---|
| Embedded H2 (default) | `values.yaml` | H2 on a PersistentVolumeClaim (`database.h2.persistence`, 20Gi) |
| Zalando Postgres operator | `-f values-zpg.yaml` | A `postgresql` cluster created through an installed [Zalando operator](https://github.com/zalando/postgres-operator) |
| Your own PostgreSQL | `-f values-external-postgres.yaml` | Any PostgreSQL; connection details from an existing secret (see the file) |

```sh
helm install adahandle-resolver deploy/adahandle-resolver
helm install adahandle-resolver deploy/adahandle-resolver -f deploy/adahandle-resolver/values-zpg.yaml
helm install adahandle-resolver deploy/adahandle-resolver -f deploy/adahandle-resolver/values-external-postgres.yaml
```

A synced mainnet database is about 5 GB and grows with the chain; a first sync from the first ADA
Handle mint takes several hours.

## Memory

A mainnet sync needs about 4 GB of heap (`javaToolOptions`, default `-Xmx4g`). Keep
`resources.limits.memory` above the heap, roughly `-Xmx` + 0.5Gi, or the pod is OOMKilled.
