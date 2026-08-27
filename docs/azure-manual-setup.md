# TicketNest Azure Dev/Test setup — detailed portal walkthrough

This guide creates a production-like learning environment using the Azure credit from a Visual Studio subscription. It uses the Azure portal for resource creation. This remains a Dev/Test application, not a business production service with an uptime commitment.

Portal labels change occasionally. Use the **portal search term** below; the **resource name** is what you type after opening that service. Searching for `cae-ticketnest-devtest` before creating it will find nothing.

## 0. Resource directory and naming sheet

| Purpose | Search for this service | Name to enter |
|---|---|---|
| Resource group | `Resource groups` | `rg-ticketnest-devtest` |
| Network | `Virtual networks` | `vnet-ticketnest-devtest` |
| Logs | `Log Analytics workspaces` | `log-ticketnest-devtest` |
| Docker registry | `Container registries` | globally unique, e.g. `tncurs3m4rkdev` |
| Secrets | `Key vaults` | globally unique, e.g. `kv-ticketnest-curs3m4rk` |
| Runtime identity | `Managed Identities` | `id-ticketnest-runtime` |
| Hosting boundary | `Container Apps Environments` | `cae-ticketnest-devtest` |
| Database server | `Azure Database for PostgreSQL flexible servers` | globally unique, e.g. `psql-ticketnest-curs3m4rk` |
| One-time DB task | `Container Apps Jobs` | `job-ticketnest-dbinit` |
| Application | `Container Apps` | `ca-ticketnest-devtest` |
| GitHub identity | `Microsoft Entra ID` > `App registrations` | `github-ticketnest-devtest` |

Use these fixed values:

```text
Subscription:     Visual Studio Dev/Test subscription
Region:           Central India
Resource group:   rg-ticketnest-devtest
VNet:             vnet-ticketnest-devtest       10.20.0.0/16
App subnet:       snet-containerapps             10.20.0.0/23
Database subnet:  snet-postgres                  10.20.2.0/28
```

ACR, Key Vault, and PostgreSQL names are globally unique. Add a short suffix if an example is unavailable. Record the actual values because they must later match GitHub and the JDBC URL:

```text
MY_ACR_NAME=
MY_KEY_VAULT_NAME=
MY_POSTGRES_SERVER_NAME=
MY_POSTGRES_FQDN=
MY_SUBSCRIPTION_ID=
MY_TENANT_ID=
MY_RUNTIME_IDENTITY_RESOURCE_ID=
```

Use the same subscription, group, and region on every creation page. Wait for **Review + create** validation before clicking **Create**.

## 1. Preflight: subscription, permissions, providers, and budget

### Select the subscription and copy IDs

1. Sign in to <https://portal.azure.com>.
2. Search for **Subscriptions** and open the subscription identifying the Visual Studio benefit.
3. Copy **Subscription ID** from Overview.
4. Search for **Microsoft Entra ID** and copy **Tenant ID** from Overview.

You need permission to create resources and role assignments. If **Add role assignment** is disabled later, `Contributor` alone is insufficient; the subscription owner must give you `Owner`, `User Access Administrator`, or `Role Based Access Control Administrator` at the intended scope.

### Check resource providers

Open **Subscriptions** > your subscription > **Settings** > **Resource providers**. Search for each namespace and click **Register** if it is not registered:

```text
Microsoft.App
Microsoft.ContainerRegistry
Microsoft.DBforPostgreSQL
Microsoft.KeyVault
Microsoft.ManagedIdentity
Microsoft.Network
Microsoft.OperationalInsights
```

Registration can take several minutes. Azure often registers them automatically, but doing this now avoids unclear deployment failures.

### Create the budget first

1. Open **Subscriptions** > your subscription > **Cost Management** > **Budgets**. If absent, search for **Cost Management + Billing**, select the subscription scope, then open **Budgets**.
2. Click **Add**.
3. Name: `budget-ticketnest-devtest`; reset period: `Monthly`; expiration: at least one year from now.
4. Set the amount slightly below the monthly Visual Studio credit.
5. Add actual-cost alerts at `50%`, `80%`, and `100%`, each sent to your email.
6. Create the budget.

A budget notifies; it does not stop services. Cost data is delayed, so review **Cost analysis** too.

## 2. Create the resource group

1. Search **Resource groups** > **Create**.
2. Choose the Visual Studio subscription.
3. Name: `rg-ticketnest-devtest`; region: `Central India`.
4. **Review + create** > **Create**.

Put every resource below in this group. Never delete the group unless the complete environment is no longer needed.

## 3. Create the VNet and two subnets

1. Search **Virtual networks** > **Create**.
2. Basics: group `rg-ticketnest-devtest`, name `vnet-ticketnest-devtest`, region `Central India`.
3. On **IP addresses**, set IPv4 space to `10.20.0.0/16`. Delete the generated `default` subnet if present.
4. **Add a subnet** for Container Apps:
   - Name: `snet-containerapps`
   - Starting address: `10.20.0.0`
   - Size: `/23`
   - NAT gateway, NSG, route table: `None`
   - Service endpoints: none
   - Subnet delegation: `None`
5. Add the database subnet:
   - Name: `snet-postgres`
   - Starting address: `10.20.2.0`
   - Size: `/28`
   - NAT gateway, NSG, route table: `None`
   - Delegation: `Microsoft.DBforPostgreSQL/flexibleServers`; display text may be **PostgreSQL flexible server**
6. **Review + create** > **Create**.

Checkpoint: VNet > **Settings** > **Subnets** must show these two subnets. The app subnet has no delegation; the database subnet is delegated only to PostgreSQL Flexible Server.

## 4. Create Log Analytics

1. Search **Log Analytics workspaces** > **Create**.
2. Use the common subscription/group, name `log-ticketnest-devtest`, region `Central India`.
3. Create it.
4. Open it > **Settings** > **Usage and estimated costs** > **Data Retention**. Set or confirm `30 days`. If 30 days is the non-editable included default, leave it.

## 5. Create Azure Container Registry

1. Search **Container registries** > **Create**.
2. Set the common subscription/group, globally unique `MY_ACR_NAME`, `Central India`, pricing plan `Basic`. Registry names contain only letters and digits—no hyphens.
3. Choose the permission mode deliberately:
   - Prefer **RBAC Registry Permissions** if offered. It uses `AcrPull` and `AcrPush` below.
   - If using **RBAC Registry + ABAC Repository Permissions**, substitute `Container Registry Repository Reader` for `AcrPull` and `Container Registry Repository Writer` for `AcrPush`. Legacy AcrPull/AcrPush are not honored in ABAC mode.
4. Leave public network access enabled so GitHub-hosted runners can push. Authentication is still required.
5. Create the registry.
6. Open **Settings** > **Access keys** and confirm **Admin user** is `Disabled`.
7. Record the login server, normally `<MY_ACR_NAME>.azurecr.io`.

Do not create a registry username/password. Container Apps pulls with managed identity; GitHub pushes with OIDC.

## 6. Create Key Vault and secrets

### Create the vault

1. Search **Key vaults** > **Create**.
2. Set the common subscription/group, globally unique `MY_KEY_VAULT_NAME`, region `Central India`, tier `Standard`.
3. On recovery options, enable **Purge protection** and leave the soft-delete retention default.
4. On **Access configuration**, choose **Azure role-based access control**, not legacy access policies.
5. For this learning environment, leave public network access enabled from all networks. Entra authentication and RBAC still protect the data. A private Key Vault would require another private endpoint/DNS setup.
6. Create the vault.

Purge protection cannot be disabled later. A deleted vault and its name remain reserved during retention.

### Give yourself permission to create secrets

Vault > **Access control (IAM)** > **Add role assignment**:

1. Role: `Key Vault Administrator`, or `Key Vault Secrets Officer` if only secret management is needed.
2. Members: **User, group, or service principal** > your signed-in account.
3. Assign, wait several minutes, refresh, then open **Objects** > **Secrets**.

### Create three independent secret values

Generate strong values in a password manager. Do not use a terminal command that prints them into history. The JWT secret must contain at least 32 random bytes; a longer base64 value is fine.

Create each at **Objects** > **Secrets** > **Generate/Import**:

```text
postgres-admin-password   password entered during PostgreSQL creation
ticketnest-db-password    password for database role ticketnest_app
ticketnest-jwt-secret     JWT signing secret, at least 32 random bytes
```

Keep each enabled and leave activation/expiration empty for this exercise. PostgreSQL creation cannot consume a Key Vault reference, so enter the same admin-password value manually during server creation.

## 7. Create the runtime identity and permissions

### Create the identity

1. Search **Managed Identities** > **Create**.
2. Use the common group/region and name `id-ticketnest-runtime`.
3. Create it and copy its **Resource ID**. Do not confuse Resource ID, Client ID, and Principal ID.

### Allow image pull, not push

Open ACR > **Access control (IAM)** > **Add role assignment**:

1. RBAC-only registry: role `AcrPull`. ABAC registry: `Container Registry Repository Reader`.
2. Members > **Managed identity** > **Select members**.
3. Type: **User-assigned managed identity**; select `id-ticketnest-runtime`.
4. Assign.

### Allow only the two runtime secrets

Repeat these steps separately on `ticketnest-db-password` and `ticketnest-jwt-secret`:

1. Key Vault > **Objects** > **Secrets** > open the individual secret.
2. Its **Access control (IAM)** > **Add role assignment**.
3. Role `Key Vault Secrets User`; member managed identity `id-ticketnest-runtime`.
4. Assign.

Do not grant the admin-password secret yet. That access is temporary during DB initialization.

## 8. Create the Container Apps environment

The environment is the hosting/network boundary; it is not the application.

1. Search **Container Apps Environments** > **Create**. If that result is absent, search **Container Apps**, begin creating an app, and click **Create new environment** in its Basics tab. Complete the environment pane, then cancel the app wizard afterward.
2. Basics:
   - Name: `cae-ticketnest-devtest`
   - Region: `Central India`
   - Environment type: **Workload profiles**, not legacy Consumption-only
   - Zone redundancy: `Disabled`
3. **Workload profiles**: keep the built-in `Consumption` profile; add no dedicated profile.
4. **Networking**:
   - Use your own virtual network: `Yes`
   - VNet: `vnet-ticketnest-devtest`
   - Infrastructure subnet: `snet-containerapps`
   - Internal/private environment: `No`
   - Public network access: `Enabled`
5. **Monitoring**:
   - Logs destination: `Azure Log Analytics`
   - Workspace: `log-ticketnest-devtest`
6. Create it; provisioning can take several minutes.

Checkpoint: its Overview must show Workload profiles, the intended subnet, and Log Analytics. The environment and database share the VNet, enabling private DB traffic.

## 9. Create PostgreSQL Flexible Server 17 privately

1. Search **Azure Database for PostgreSQL flexible servers**. Do not select PostgreSQL on a VM, Single Server, or Cosmos DB for PostgreSQL.
2. **Create** > **Flexible server** if asked for a deployment option.
3. Basics:
   - Common subscription/group
   - Server name: globally unique `MY_POSTGRES_SERVER_NAME`
   - Region: `Central India`
   - PostgreSQL version: `17`
   - Workload type: `Development`
   - Availability zone: `No preference`
   - High availability: `Disabled`
   - Authentication: `PostgreSQL authentication only`
   - Administrator login: `ticketnestadmin`
   - Password: exact `postgres-admin-password` value
4. **Configure server** under Compute + storage:
   - Compute tier: `Burstable`
   - Size: `Standard_B1ms` / `B1ms` (1 vCore, 2 GiB)
   - Storage: `32 GiB`
   - Storage autogrow: enabled if offered
   - Backup retention: `7 days`
   - Backup redundancy: `Locally redundant`
   - Geo-redundant backup: disabled

   If B1ms is unavailable in Central India, select the smallest available Burstable size with at least 2 GiB and recheck estimated cost.

5. **Networking**:
   - Connectivity: **Private access (VNet Integration)**
   - VNet: `vnet-ticketnest-devtest`
   - Delegated subnet: `snet-postgres`
   - Let the portal create a new private DNS zone
   - Check **Link the private DNS zone to your virtual network** if shown
   - Do not enable public access or add firewall rules
6. **Security**: keep the service-managed encryption key.
7. Review estimated cost and create.
8. On server **Overview**, copy **Endpoint/Server name** into `MY_POSTGRES_FQDN`. Use this exact value later.
9. Open **Networking** and confirm private connectivity and `snet-postgres`.
10. Search **Private DNS zones**, open the zone created for this server, then
    **Virtual network links**. Verify it is linked to
    `vnet-ticketnest-devtest`.

Your laptop intentionally cannot connect directly. Private VNet integration is chosen at server creation and cannot simply be switched later.

## 10. Initialize the empty application database with a one-time job

This `psql` job runs inside the Container Apps environment, where it can resolve the private DB. It creates only the empty `ticketnest` database and its owner. Flyway creates all application tables on first app startup.

### Temporarily grant the admin secret

Key Vault > `postgres-admin-password` > **Access control (IAM)** > **Add role assignment**: role `Key Vault Secrets User`, member `id-ticketnest-runtime`. Assign and wait several minutes.

### Create a harmless manual job first

1. Search **Container Apps Jobs** > **Create**.
2. Set job name `job-ticketnest-dbinit`, environment `cae-ticketnest-devtest`, trigger `Manual`, replica timeout `600`, retry limit `0`, parallelism `1`, completion count `1`.
3. Container settings:
   - Name: `postgres-client`
   - Public image / **Docker Hub or other registries**
   - Registry server: `docker.io`
   - Image/tag: `library/postgres:17`
   - CPU `0.25`, memory `0.5 GiB`
   - Command override `/bin/sh`
   - Argument item 1: `-c`
   - Argument item 2: `echo configuration-pending`
4. Create it, but do not start it.

### Assign identity and secret references

1. Job > **Identity** > **User assigned** > **Add** `id-ticketnest-runtime`.
2. Job > **Secrets** > add:

| Job secret | Type | Versionless Key Vault URL | Identity |
|---|---|---|---|
| `pg-admin-password` | Key Vault reference | `https://<MY_KEY_VAULT_NAME>.vault.azure.net/secrets/postgres-admin-password` | runtime identity |
| `app-db-password` | Key Vault reference | `https://<MY_KEY_VAULT_NAME>.vault.azure.net/secrets/ticketnest-db-password` | runtime identity |

Do not include a version GUID at the URL end. If retrieval fails, wait for RBAC propagation and confirm the identity is attached.

### Configure and execute initialization

Open Job > **Containers** > **Edit** (sometimes **Edit and deploy**) and edit `postgres-client`. Add:

| Environment variable | Source | Value |
|---|---|---|
| `PGHOST` | Manual | exact `MY_POSTGRES_FQDN` |
| `PG_ADMIN_USER` | Manual | `ticketnestadmin` |
| `PG_ADMIN_PASSWORD` | Secret reference | `pg-admin-password` |
| `APP_DB_PASSWORD` | Secret reference | `app-db-password` |

Set command `/bin/sh`. Argument item 1 is `-c`; paste this entire block as argument item 2:

```sh
set -eu
export PGPASSWORD="$PG_ADMIN_PASSWORD"
psql "host=$PGHOST port=5432 dbname=postgres user=$PG_ADMIN_USER sslmode=require" --set=ON_ERROR_STOP=1 --set=app_password="$APP_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE ticketnest_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ticketnest_app') \gexec
SELECT 'CREATE DATABASE ticketnest OWNER ticketnest_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ticketnest') \gexec
ALTER DATABASE ticketnest OWNER TO ticketnest_app;
SQL
unset PGPASSWORD
export PGPASSWORD="$APP_DB_PASSWORD"
psql "host=$PGHOST port=5432 dbname=ticketnest user=ticketnest_app sslmode=require" --set=ON_ERROR_STOP=1 -c 'SELECT current_user, current_database();'
```

The definition stores `$APP_DB_PASSWORD`, not its value. Save, then **Execution history** > **Start a new execution**. Wait for `Succeeded`. Console logs should end with `ticketnest_app | ticketnest`.

Troubleshooting:

- `could not translate host name`: private DNS zone is not linked to the VNet.
- timeout: wrong Container Apps environment/VNet or PostgreSQL network configuration.
- authentication failed: Key Vault admin value differs from the password used to create PostgreSQL.
- Key Vault reference error: identity, secret-scope RBAC, or propagation delay.

### Remove administrative access immediately

After success:

1. Key Vault > `postgres-admin-password` > **Access control (IAM)** > role assignments.
2. Remove only the runtime identity's `Key Vault Secrets User` assignment at this secret scope.
3. Delete only `job-ticketnest-dbinit`, after confirming its exact name.

Keep the admin secret in Key Vault for emergency administration. The application can no longer read it.

## 11. Create and configure the Container App

### Create a bootstrap app listening on 8080

1. Search **Container Apps** > **Create** > **Container App**.
2. Name `ca-ticketnest-devtest`, region `Central India`, environment `cae-ticketnest-devtest`, deployment source `Container image`.
3. Container:
   - Name: `ticketnest`
   - Docker Hub or other registries
   - Registry server: `mcr.microsoft.com`
   - Image/tag: `azuredocs/aci-helloworld:latest`
   - CPU `0.5`, memory `1 GiB`
   - Environment variable `PORT=8080`
4. Ingress:
   - Enabled; **Accepting traffic from anywhere** / External
   - Transport `Auto`/HTTP
   - Target port `8080`
   - Allow insecure connections `Disabled` so HTTP redirects to HTTPS
5. Create it and open its HTTPS Application URL.

Do not yet add `/actuator/health` probes: the bootstrap image has no Spring endpoint. Azure supplies default TCP probes until the first TicketNest image deploys.

### Attach runtime identity and configure registry authentication

1. App > **Identity** > **User assigned** > **Add** `id-ticketnest-runtime`.
2. App > **Settings** > **Registries** > **Add**:
   - Registry: `<MY_ACR_NAME>.azurecr.io`
   - Authentication: `Managed identity`
   - Identity: `id-ticketnest-runtime`
3. Save.

If **Registries** is absent, use **Revision management** > **Create new revision** > edit container > **Azure Container Registry** > authentication **Managed identity**. The required result is a mapping from the registry server to the runtime identity. A pull role alone is insufficient; admin credentials remain disabled.

### Add Key Vault-backed app secrets

App > **Secrets** > **Add**:

| App secret | Type | Versionless Key Vault URL | Identity |
|---|---|---|---|
| `db-password` | Key Vault reference | `https://<MY_KEY_VAULT_NAME>.vault.azure.net/secrets/ticketnest-db-password` | runtime identity |
| `jwt-secret` | Key Vault reference | `https://<MY_KEY_VAULT_NAME>.vault.azure.net/secrets/ticketnest-jwt-secret` | runtime identity |

### Add production variables

App > **Application** > **Containers** > **Edit and deploy** > edit
`ticketnest`. Keep the bootstrap `PORT=8080` variable for now (TicketNest
ignores it) and add:

| Name | Source | Value |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Manual | `prod` |
| `SERVER_PORT` | Manual | `8080` |
| `SERVER_SSL_ENABLED` | Manual | `false` |
| `DB_USERNAME` | Manual | `ticketnest_app` |
| `DB_URL` | Manual | `jdbc:postgresql://<MY_POSTGRES_FQDN>:5432/ticketnest?sslmode=require&currentSchema=public&timezone=UTC` |
| `DB_PASSWORD` | Secret reference | `db-password` |
| `JWT_SECRET` | Secret reference | `jwt-secret` |

Use the exact PostgreSQL endpoint. Never put the password in `DB_URL`. Save/deploy; the bootstrap image ignores Spring variables and still uses port 8080.

### Revision and scale configuration

1. **Revision management** > **Choose revision mode** > `Single`.
2. **Application** > **Scale** > **Edit and deploy**.
3. Minimum replicas `0`, maximum `1`; keep default HTTP scaling.

Scale-to-zero saves cost but produces cold starts. The first request starts Java, runs Flyway if needed, validates Hibernate, and connects to PostgreSQL.

## 12. Create the GitHub OIDC deployment identity

The runtime identity belongs to the running app. This separate Entra application lets GitHub push and deploy. Never give the runtime identity deployment/push permission.

### App registration and federated credential

1. Search **Microsoft Entra ID** > **App registrations** > **New registration**.
2. Name `github-ticketnest-devtest`; choose accounts in this organizational directory only; leave Redirect URI empty.
3. Register and copy **Application (client) ID** and **Directory (tenant) ID**. Do not create a client secret.
4. Open **Certificates & secrets** > **Federated credentials** > **Add credential**.
5. Scenario: **GitHub Actions deploying Azure resources**.
6. Enter the exact, case-sensitive GitHub owner and repository (`TicketNest`).
7. Entity type `Environment`; environment `azure-devtest`; credential name `github-ticketnest-azure-devtest`.
8. Confirm subject `repo:<owner>/TicketNest:environment:azure-devtest`, then add.

This environment must exactly match `environment: azure-devtest` in the workflow. Do not choose Branch here.

### Assign GitHub's two roles

On ACR > **Access control (IAM)**, assign the service principal `github-ticketnest-devtest`:

- RBAC-only registry: `AcrPush`
- ABAC registry: `Container Registry Repository Writer`

On the `ca-ticketnest-devtest` Container App itself > **Access control (IAM)**, assign that service principal `Container Apps Contributor`. Scope it to the app, not subscription. Do not grant Owner or broad Contributor.

## 13. Configure the GitHub environment

Repository > **Settings** > **Environments** > **New environment**:

1. Name `azure-devtest`.
2. Add no required reviewers.
3. **Deployment branches and tags** > **Selected branches and tags** > add Branch `master`.
4. Under **Environment variables**, add:

| Variable | Value |
|---|---|
| `AZURE_CLIENT_ID` | Entra application/client ID |
| `AZURE_TENANT_ID` | tenant ID |
| `AZURE_SUBSCRIPTION_ID` | Visual Studio subscription ID |
| `AZURE_RESOURCE_GROUP` | `rg-ticketnest-devtest` |
| `AZURE_CONTAINER_APP` | `ca-ticketnest-devtest` |
| `AZURE_CONTAINER_REGISTRY` | actual ACR name, without `.azurecr.io` |

The workflow reads `vars`, so use environment variables, not secrets. These IDs are not passwords. Never add `AZURE_CLIENT_SECRET`; OIDC replaces it.

## 14. Run CI once, then protect `master`

### Make required check names appear

`.github/workflows/ci-cd.yml` runs on every branch push; deployment is allowed only on `master`. Create a feature branch, inspect status, commit the intended files, and push. Example:

```powershell
git switch -c setup/azure-devtest
git status
git add .github/workflows/ci-cd.yml docs/azure-manual-setup.md pom.xml src Dockerfile docker-compose.yml .gitignore .mvn mvnw mvnw.cmd README.md
git status
git commit -m "Add Flyway and Azure CI/CD deployment"
git push -u origin setup/azure-devtest
```

Do not blindly commit unrelated files. In GitHub **Actions**, confirm `Build Validation`, `Unit Tests`, and `Integration Tests` pass, while `Deploy Azure DevTest` is skipped.

### Create an active ruleset

1. Repository **Settings** > **Rules** > **Rulesets** > **New ruleset** > **New branch ruleset**.
2. Name `protect-master`; enforcement `Active`; leave bypass list empty, including repository admins.
3. Target branches > include by pattern `master`.
4. Enable:
   - Restrict deletions
   - Block force pushes
   - Require a pull request before merging
   - Required approvals `0` for the solo workflow
   - Require conversation resolution
   - Require status checks and require branch up to date
5. Add required GitHub Actions checks:
   - `Build Validation`
   - `Unit Tests`
   - `Integration Tests`
6. Save.

If check search is empty, complete the feature-branch Actions run first.

Repository **Settings** > **General** > **Pull Requests**: enable squash merging. Disable merge commits/rebase if squash should be the only merge button. Optionally auto-delete head branches.

## 15. First deployment, then Spring health probes

### First deployment

1. Open a PR from `setup/azure-devtest` to `master`.
2. Wait for all checks, resolve conversations, then **Squash and merge**.
3. Watch the new `master` Actions run. All three CI jobs rerun against the merged commit, then deployment authenticates with OIDC, pushes `ticketnest:<full-git-sha>`, updates the app, waits for health, and verifies Swagger is absent.
4. ACR > **Repositories** > `ticketnest`: confirm the tag is the commit SHA, not `latest`.
5. Container App > **Revision management**: confirm the active revision uses the same SHA image.

If pulling fails, check all three requirements: correct ACR read/pull role, runtime identity attached to the app, and registry configured to authenticate with that identity.

### Add probes after the real app is healthy

1. App > **Application** > **Containers** > **Edit and deploy** > edit `ticketnest` > **Health probes**.
2. Remove the automatically generated TCP probe of the same type before adding HTTP; only one of each type is allowed.
3. Add:

| Type | Protocol | Path | Port | Initial delay | Period | Timeout | Failure threshold | Success |
|---|---|---|---:|---:|---:|---:|---:|---:|
| Startup | HTTP | `/actuator/health` | 8080 | 5 s | 10 s | 5 s | 30 | 1 |
| Readiness | HTTP | `/actuator/health` | 8080 | 5 s | 10 s | 5 s | 6 | 1 |
| Liveness | HTTP | `/actuator/health` | 8080 | 30 s | 30 s | 5 s | 3 | 1 |

4. Save/deploy. In single-revision mode, Azure keeps the prior revision receiving traffic until the new revision passes startup/readiness.

For a future highly available production design, expose separate liveness/readiness health groups so a temporary DB outage does not cause JVM restarts. This single endpoint follows the current learning plan.

## 16. Acceptance checks

### CI/CD behavior

1. Push a new feature branch: the three CI jobs run and deployment is skipped.
2. A failing test makes a check red and blocks merge.
3. A direct master push is rejected.
4. Merging a green PR reruns master CI and only then deploys.

### HTTP behavior

Copy the Container App Application URL and verify:

```text
GET /actuator/health       -> 200 and {"status":"UP"}
GET /swagger               -> 404
GET /swagger-ui/index.html -> 404
GET /v3/api-docs           -> 404
```

The first request after scale-to-zero can be slow. A Swagger 401/403 is not equivalent to 404; it would mean the endpoint exists.

### Flyway/database behavior

Container App > **Monitoring** > **Log stream** after a cold start should show Flyway applying version `1` or reporting the schema current, then successful Hibernate validation. No password/JWT should appear.

For an exact SQL check, temporarily create another manual `postgres:17` job using only the runtime DB password and run:

```sh
export PGPASSWORD="$APP_DB_PASSWORD"
psql "host=$PGHOST port=5432 dbname=ticketnest user=ticketnest_app sslmode=require" --set=ON_ERROR_STOP=1 -c 'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;' -c '\dt'
```

Expect one successful version `1` and nine tables: `users`, `refresh_tokens`, `venues`, `seats`, `shows`, `bookings`, `booking_seats`, `payments`, `notifications`. Delete the verification job afterward; it never needs admin access.

### Security behavior

- PostgreSQL Networking shows private VNet integration and no public endpoint.
- ACR admin user remains disabled.
- Container App secrets are Key Vault references, not literal values.
- Runtime identity cannot read the admin password or push images.
- GitHub identity cannot read Key Vault or administer PostgreSQL.
- Git contains no DB/JWT/admin password, keystore, or Azure client secret.

## 17. Troubleshooting map

| Symptom | Check |
|---|---|
| Cannot find an Azure resource | Search the service name in section 0, not an instance name not yet created |
| Create is disabled | Red dot on a wizard tab, provider registration, region/SKU availability |
| Add role assignment disabled | Your account lacks role-assignment write permission |
| Key Vault reference error | Identity attached, secret-scope role, versionless URI, RBAC propagation delay |
| ACR `UNAUTHORIZED` | Permission mode matches role: AcrPull/Push vs Repository Reader/Writer |
| `MANIFEST_UNKNOWN` | SHA image tag was not pushed or registry/repository name is wrong |
| `ImagePullBackOff` | App's registry mapping does not use runtime identity |
| DB hostname does not resolve | Private DNS zone is not linked to the VNet |
| DB timeout | Wrong environment subnet/VNet or PostgreSQL networking |
| Hibernate validation fails | Flyway schema differs from entities; create/fix a new migration |
| Revision restarts | **Diagnose and solve problems**, console logs, health path/port |
| OIDC login fails | Exact owner/repo/environment casing, IDs, and workflow `id-token: write` |
| GitHub login works but push fails | Missing registry writer/push role matching ACR mode |
| Deploy job is skipped on master | Environment branch rule, job condition, or failed CI dependency |

## 18. Cost control and eventual cleanup

- App min replicas `0` removes idle Container Apps compute cost.
- PostgreSQL is the main continuous cost. Its Overview has **Stop/Start** for Dev/Test. Azure can auto-restart a stopped server after its maximum stop interval, so monitor it.
- ACR storage, Key Vault operations, Log Analytics ingestion, network, PostgreSQL storage/backups can cost money even while compute is idle.
- A budget is not a hard cap.

When permanently finished, inspect and back up anything needed, then delete the exact `rg-ticketnest-devtest` resource group. Purge-protected Key Vault remains soft-deleted through retention.

## Official references

- [Container Apps custom VNet integration](https://learn.microsoft.com/azure/container-apps/vnet-custom)
- [Container Apps networking](https://learn.microsoft.com/azure/container-apps/networking)
- [Container Apps Key Vault references](https://learn.microsoft.com/azure/container-apps/manage-secrets)
- [ACR pull with managed identity](https://learn.microsoft.com/azure/container-apps/managed-identity-image-pull)
- [Container Apps health probes](https://learn.microsoft.com/azure/container-apps/health-probes)
- [Container Apps jobs](https://learn.microsoft.com/azure/container-apps/jobs)
- [PostgreSQL private networking](https://learn.microsoft.com/azure/postgresql/flexible-server/concepts-networking-private)
- [Create PostgreSQL Flexible Server](https://learn.microsoft.com/azure/postgresql/configure-maintain/quickstart-create-server)
- [Create Key Vault](https://learn.microsoft.com/azure/key-vault/general/quick-create-portal)
- [Create ACR](https://learn.microsoft.com/azure/container-registry/container-registry-get-started-portal)
- [ACR RBAC versus ABAC roles](https://learn.microsoft.com/azure/container-registry/container-registry-rbac-abac-repository-permissions)
- [GitHub Actions Azure OIDC](https://learn.microsoft.com/azure/developer/github/connect-from-azure-openid-connect)
- [GitHub deployment environments](https://docs.github.com/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments)
- [GitHub ruleset rules](https://docs.github.com/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets)
