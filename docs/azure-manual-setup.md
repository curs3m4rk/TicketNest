# Azure Dev/Test manual setup

This runbook creates TicketNest as a production-like learning environment. Visual Studio subscriber credits are for individual Dev/Test use, not a real production workload.

## 1. Names and region

Use the Visual Studio Azure subscription and **Central India**. If a globally unique name is unavailable, append the same short numeric suffix to it.

| Resource | Name |
|---|---|
| Resource group | `rg-ticketnest-devtest` |
| Virtual network | `vnet-ticketnest-devtest` |
| Container Apps subnet | `snet-containerapps` |
| PostgreSQL subnet | `snet-postgres` |
| Log Analytics | `log-ticketnest-devtest` |
| Container Apps environment | `cae-ticketnest-devtest` |
| Container App | `ca-ticketnest-devtest` |
| Container Registry | `tncurs3m4rkdev` |
| Key Vault | `kv-ticketnest-curs3m4rk` |
| PostgreSQL server | `psql-ticketnest-curs3m4rk` |
| Runtime identity | `id-ticketnest-runtime` |

Before provisioning, create a monthly Cost Management budget just below the available credit. Add email notifications at 50%, 80%, and 100%. A budget sends notifications; it does not stop resources.

## 2. Network and platform

1. Create the resource group.
2. Create `vnet-ticketnest-devtest` with address space `10.20.0.0/16`.
3. Add `snet-containerapps` as `10.20.0.0/23`.
4. Add `snet-postgres` as `10.20.2.0/28` and delegate it to PostgreSQL Flexible Server.
5. Create `log-ticketnest-devtest` with 30-day retention.
6. Create `cae-ticketnest-devtest` on the Consumption workload profile, connected to `snet-containerapps` and the Log Analytics workspace.
7. Create Basic ACR `tncurs3m4rkdev`. Keep the admin account disabled and select Azure RBAC registry permissions.
8. Create the Key Vault with Azure RBAC, soft delete, and purge protection.

Create these independent random Key Vault secrets without printing or committing their values:

- `postgres-admin-password`
- `ticketnest-db-password`
- `ticketnest-jwt-secret` (at least 32 random bytes)

## 3. PostgreSQL

Create PostgreSQL Flexible Server 17 with:

- Burstable `B1ms`, 32-GB storage, seven-day backups.
- No high availability and no geo-redundant backup.
- Administrator `ticketnestadmin` using the Key Vault administrator password.
- Private access through `snet-postgres` and the private DNS zone offered by the portal.
- Public access disabled.

Because the server is private, create a temporary manual Container Apps Job in `cae-ticketnest-devtest` using `postgres:17`. Supply its passwords through secret references, not command arguments. Run `psql` to create login `ticketnest_app` with `ticketnest-db-password`, then create database `ticketnest` owned by that role. Verify the login and delete the job and its administrator-secret access.

## 4. Runtime identity and app

Create `id-ticketnest-runtime` and assign it:

- `AcrPull` on the registry.
- `Key Vault Secrets User` scoped to `ticketnest-db-password` and `ticketnest-jwt-secret` only.

Create `ca-ticketnest-devtest` with a temporary Microsoft hello-world image, the runtime identity, external ingress, single-revision mode, 0.5 CPU, 1 GiB memory, minimum zero replicas, and maximum one replica. Before the first TicketNest deployment, change ingress target port to 8080 and configure startup/readiness/liveness probes at `/actuator/health`.

Add plain environment variables:

```text
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
SERVER_SSL_ENABLED=false
DB_USERNAME=ticketnest_app
DB_URL=jdbc:postgresql://psql-ticketnest-curs3m4rk.postgres.database.azure.com:5432/ticketnest?sslmode=require&currentSchema=public&timezone=UTC
```

Create Container Apps secrets as versionless Key Vault references and map them to `DB_PASSWORD` and `JWT_SECRET`. Redirect HTTP to HTTPS.

## 5. GitHub OIDC

In GitHub, create environment `azure-devtest`, permit deployments only from `master`, and do not add reviewers.

In Microsoft Entra ID, create app registration `github-ticketnest-devtest`. Add a GitHub Actions federated credential for owner `curs3m4rk`, repository `TicketNest`, entity type **Environment**, and environment `azure-devtest`. Do not create a client secret.

Assign its service principal `AcrPush` on the registry and `Container Apps Contributor` on the Container App. Add any resource-group read permission Azure needs to resolve those resources.

Create these GitHub environment variables:

```text
AZURE_CLIENT_ID=<application client ID>
AZURE_TENANT_ID=<directory tenant ID>
AZURE_SUBSCRIPTION_ID=<subscription ID>
AZURE_RESOURCE_GROUP=rg-ticketnest-devtest
AZURE_CONTAINER_APP=ca-ticketnest-devtest
AZURE_CONTAINER_REGISTRY=tncurs3m4rkdev
```

## 6. Protect master

First push this workflow on a feature branch so its check names exist. Then create an active GitHub branch ruleset targeting `master`:

- Require pull requests with zero approvals for the solo workflow.
- Require `Build Validation`, `Unit Tests`, and `Integration Tests` from GitHub Actions.
- Require the branch to be up to date and conversations to be resolved.
- Block direct/force pushes and deletion, including for administrators.
- Configure no routine bypass and use squash merging.

Feature pushes run CI only. The protected `master` push produced by merging a PR reruns CI and deploys the immutable commit-SHA image only after every check succeeds.

## 7. Verify

1. Push a feature commit: three CI jobs run and Azure does not deploy.
2. Open and merge a green PR: master CI reruns, ACR receives a SHA tag, and Container Apps creates a healthy revision.
3. Confirm `/actuator/health` reports `UP` while `/swagger`, `/swagger-ui/index.html`, and `/v3/api-docs` return 404.
4. In PostgreSQL, confirm `flyway_schema_history` contains successful version `1` and the nine application tables exist.
5. Inspect Log Analytics and GitHub logs for accidental passwords, JWTs, or authorization headers.
