# TicketNest free Dev/Test deployment: Render + Neon

This runbook creates a production-like learning deployment without Azure:

- Render builds and hosts the TicketNest Docker container.
- Neon hosts PostgreSQL.
- GitHub Actions runs build validation, unit tests, and integration tests on
  every pushed branch.
- Render watches only `master` and deploys a commit only after its GitHub checks
  pass.
- Flyway creates and upgrades the schema when the application starts.
- Hibernate validates the Flyway-created schema.

This is a free Dev/Test environment, not a business production environment with
an uptime or recovery guarantee. Render Free sleeps when idle, and both providers
enforce free-plan limits.

## 0. Architecture, names, and important limits

Use these names where the provider asks you to choose one:

| Purpose | Provider | Suggested name |
|---|---|---|
| Source and CI | GitHub | `TicketNest` |
| Database project | Neon | `ticketnest-devtest` |
| Database | Neon | `ticketnest` |
| Application database role | Neon | `ticketnest_app` |
| Application service | Render | `ticketnest-devtest` |
| Protected deployment branch | GitHub | `master` |

The traffic flow is:

```text
Browser/API client
        |
        | HTTPS
        v
Render Web Service
        |
        | PostgreSQL protocol over TLS
        v
Neon PostgreSQL
```

The delivery flow is:

```text
push to any branch
        |
        v
GitHub Actions: Build Validation + Unit Tests + Integration Tests
        |
        +-- feature branch: stop; never deploy
        |
        +-- merged master commit: Render waits for all checks
                                  |
                                  +-- failed check: do not deploy
                                  +-- passed checks: build Docker image and deploy
```

Current free-tier constraints to accept before continuing:

- Render Free has 0.1 CPU and 512 MB RAM.
- A free Render web service spins down after 15 minutes without inbound traffic.
  Its first request after sleeping can take about a minute.
- Render provides 750 free instance-hours per workspace per month. Build minutes
  and outbound bandwidth also have limits.
- Render's filesystem is ephemeral. Do not store uploads or database files in
  the application container.
- Neon Free has usage, compute, storage, branch, and network-transfer limits.
- The Neon endpoint is reached over the public Internet with TLS and database
  authentication; this design does not provide Azure-style private networking.
- Free services can restart, sleep, or change limits and have no production SLA.

Do not use Render Free PostgreSQL for this setup. Render's free PostgreSQL
databases expire after 30 days; Neon is the persistent database provider here.

Before moving company-owned source code or data to either provider, confirm that
your organization permits it. An Azure permission problem is not permission to
move corporate assets to a personal SaaS account.

## 1. Accounts and prerequisites

You need:

1. A GitHub account with admin access to the TicketNest repository.
2. A free Neon account at <https://console.neon.tech>.
3. A free Render account at <https://dashboard.render.com>.
4. The repository pushed to GitHub.
5. Docker Desktop running locally for Testcontainers-based verification.
6. Java 21 available locally.

Use personal accounts only for code and data you are allowed to place there.
Neither Render nor Neon requires Azure Entra ID.

### GitHub plan warning

GitHub Actions can run on GitHub Free. Enforced protected branches and rulesets
are available on public repositories with GitHub Free, but protecting a private
repository requires GitHub Pro, Team, or Enterprise.

If this repository is private and the branch-protection UI is unavailable, choose
one of these deliberately:

1. Use GitHub Pro.
2. Make the repository public only after confirming it contains no credentials,
   private keys, proprietary code, or sensitive history.
3. Keep it private and follow the pull-request process voluntarily, accepting
   that GitHub will not technically block a direct push.

Do not make a repository public just to avoid paying without reviewing its full
Git history.

## 2. Repository preflight

The repository already contains the required building blocks:

```text
Dockerfile
.github/workflows/ci-cd.yml
src/main/resources/application.yml
src/main/resources/application-prod.yml
src/main/resources/db/migration/V1__initial_schema.sql
```

The production behavior is already designed to:

- enable Flyway;
- run migrations from `classpath:db/migration`;
- disable Flyway clean;
- use Hibernate `ddl-auto: validate`;
- disable Swagger and OpenAPI in `prod`;
- expose only `/actuator/health` through Actuator;
- terminate TLS at the hosting provider and run plain HTTP inside the container.

### Verify locally before using any cloud service

From PowerShell in the repository root:

```powershell
.\mvnw.cmd clean package -DskipTests
.\mvnw.cmd test
.\mvnw.cmd verify -DskipUnitTests
.\mvnw.cmd verify
docker build --tag ticketnest:manual-check .
```

All commands must pass. The integration-test commands require Docker because
they start PostgreSQL 17 with Testcontainers.

Also check that no production secret is tracked:

```powershell
git status --short
git grep -n -I -E "postgres(ql)?://|DB_PASSWORD=|JWT_SECRET=|BEGIN (RSA |OPENSSH )?PRIVATE KEY"
```

Review every match. Local placeholder values are acceptable only in local
configuration; real Neon passwords and JWT secrets must never be committed.

## 3. Remove the Azure deployment job before merging

The current `.github/workflows/ci-cd.yml` still contains a job named
`deploy-azure-devtest`. Delete that entire job, from:

```yaml
deploy-azure-devtest:
```

through the end of that job. Keep these three jobs unchanged:

```text
Build Validation
Unit Tests
Integration Tests
```

Keep the workflow trigger:

```yaml
on:
  push:
    branches:
      - "**"
```

No Render deployment job is required in GitHub Actions. Render itself watches
`master` and waits for these GitHub checks. Removing the Azure job is mandatory:
otherwise a merged master build will try to authenticate to Azure, fail, and
prevent Render's **After CI Checks Pass** policy from deploying.

Do not remove the three CI jobs or change their user-facing `name` values; those
names are used by GitHub branch protection.

## 4. Create the Neon PostgreSQL project

1. Open <https://console.neon.tech> and sign in.
2. Confirm that the selected organization is on the `Free` plan.
3. Select **New Project**.
4. Configure:
   - Project name: `ticketnest-devtest`
   - PostgreSQL version: `17`, if the version selector is offered
   - Cloud provider: use the default supported provider
   - Region: choose the region closest to Render Singapore that Neon offers
5. Select **Create Project**.

Do not create development data in the default database. TicketNest gets its own
database and login role below.

Neon may create `production` and `development` branches automatically. Use the
main/default `production` branch for this deployment. If your account instead
shows a branch named `main`, use that branch consistently.

Record only these non-secret selections in your notes:

```text
NEON_PROJECT=ticketnest-devtest
NEON_BRANCH=production
NEON_DATABASE=ticketnest
NEON_ROLE=ticketnest_app
```

## 5. Create the application role and database in Neon

The exact sidebar labels can move as Neon updates its console. The supported
path is generally:

```text
Project > Branches > production > Roles & Databases
```

If **Roles & Databases** is not visible, open **Tables** > **Database studio**
and use its roles and databases controls.

### Create the role

1. Select **Add role** or **Create role**.
2. Role name: `ticketnest_app`.
3. Allow login.
4. Generate a strong password in Neon or your password manager.
5. Store it in your password manager as `TicketNest Neon DB password`.
6. Do not paste the password into source code, this document, an issue, or chat.
7. Create the role.

### Create the database

1. On the same branch, select **Add database**.
2. Database name: `ticketnest`.
3. Owner: `ticketnest_app`.
4. Create the database.

Database ownership matters. PostgreSQL 15 and later allow the database owner to
create objects in its `public` schema; Flyway needs that permission to create the
TicketNest schema.

### Optional least-privilege hardening

Neon roles created through its Console may inherit Neon's administrative
`neon_superuser` role. For a more production-like application login:

1. Open **SQL Editor**.
2. Connect the editor to branch `production` and database `ticketnest` using the
   default project-owner role, not `ticketnest_app`.
3. Run:

```sql
REVOKE neon_superuser FROM ticketnest_app;

SELECT
    has_database_privilege('ticketnest_app', 'ticketnest', 'CONNECT') AS can_connect,
    has_schema_privilege('ticketnest_app', 'public', 'USAGE') AS can_use_schema,
    has_schema_privilege('ticketnest_app', 'public', 'CREATE') AS can_create_schema_objects;
```

All three checks should be `true`. If `can_create_schema_objects` is false, run
this as the database owner/admin role:

```sql
GRANT USAGE, CREATE ON SCHEMA public TO ticketnest_app;
```

Do not grant `ticketnest_app` access to unrelated databases or projects.

## 6. Obtain and translate the Neon connection details

1. Open the Neon project dashboard.
2. Select **Connect**.
3. Select:
   - Branch: `production` (or the main branch selected earlier)
   - Database: `ticketnest`
   - Role: `ticketnest_app`
   - Connection pooling: `Off` for the initial setup
4. Neon displays a connection string similar to:

```text
postgresql://ticketnest_app:PASSWORD@ep-example.ap-southeast-1.aws.neon.tech/ticketnest?sslmode=require
```

Do not put that complete string into Git, because it contains the password.
Split it into these Render variables:

```text
DB_URL=jdbc:postgresql://ep-example.ap-southeast-1.aws.neon.tech:5432/ticketnest?sslmode=require&currentSchema=public
DB_USERNAME=ticketnest_app
DB_PASSWORD=<the role password>
```

Rules:

- Add the `jdbc:` prefix to the URL.
- Exclude `username:password@` from `DB_URL`.
- Keep `DB_PASSWORD` separate.
- Retain `sslmode=require`.
- Use the exact hostname shown by Neon.
- Do not use the `-pooler` hostname initially. The direct connection is simpler
  for Flyway and this single small application instance.

## 7. Prepare production environment values

Generate a JWT signing secret containing at least 32 random bytes. Prefer a
password manager's secure generator. Store it as `TicketNest Render JWT secret`.

You will enter these values in Render:

| Key | Value | Secret? |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | No |
| `PORT` | `8080` | No |
| `SERVER_PORT` | `8080` | No |
| `SERVER_SSL_ENABLED` | `false` | No |
| `DB_URL` | Neon JDBC URL from section 6 | Treat as sensitive metadata |
| `DB_USERNAME` | `ticketnest_app` | No |
| `DB_PASSWORD` | Neon role password | Yes |
| `JWT_SECRET` | at least 32 random bytes | Yes |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError` | No |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `5` | No |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | `0` | No |

The production profile currently listens on port 8080. Setting Render's `PORT`
to 8080 makes the platform's expected port match the application and Dockerfile.
The JVM options keep the Java heap below the free service's 512 MB total memory;
they do not guarantee that every workload will fit.

## 8. Connect Render to GitHub safely

1. Open <https://dashboard.render.com> and sign in.
2. Open **Account Settings**.
3. Find **Git Deployment Credentials** and select **Add credential**, or connect
   GitHub when creating the service.
4. GitHub opens the Render GitHub App authorization page.
5. Prefer **Only select repositories**.
6. Select only the TicketNest repository.
7. Authorize the installation.

If the repository belongs to a GitHub organization, its administrator may need
to approve the Render GitHub App. Do not copy an organization-owned repository
to a personal account to bypass that policy.

## 9. Create the Render Web Service manually

1. In Render, select **+ New** > **Web Service**.
2. Choose the connected TicketNest repository and select **Connect**.
3. Configure the service:

| Field | Value |
|---|---|
| Name | `ticketnest-devtest` |
| Project/environment | optional; use a Dev/Test environment if prompted |
| Language/runtime | `Docker` |
| Branch | `master` |
| Region | `Singapore` |
| Root directory | blank |
| Dockerfile path | `./Dockerfile` |
| Docker build context | `.` |
| Docker command | blank; use the Dockerfile `ENTRYPOINT` |
| Compute/instance type | `Free` |

Do not select **Existing Image**. Render must connect to the Git repository so it
can associate deployments with commits and GitHub check results.

4. Expand **Advanced** or **Environment Variables**.
5. Add every variable from section 7.
6. Mark `DB_PASSWORD` and `JWT_SECRET` as secret values if the UI distinguishes
   secrets from ordinary variables.
7. Set the health-check path to:

```text
/actuator/health
```

8. Do not add a persistent disk.
9. Create the Web Service.

Creating the service triggers a one-time bootstrap deployment of the current
`master` commit. This is expected. Subsequent deployments are CI-gated in the
next section.

## 10. Diagnose and verify the first deployment

Open the Render service > **Events** or **Logs**. A healthy first startup should
show this sequence:

1. Render builds the repository Dockerfile.
2. The Java process starts with the `prod` profile.
3. Hikari connects to Neon using TLS.
4. Flyway creates `flyway_schema_history` and applies version `1`.
5. Hibernate validates the resulting schema.
6. The embedded server binds to port 8080.
7. Render's `/actuator/health` check returns success.
8. The service becomes `Live`.

The first deployment can be slow on 0.1 CPU. Render allows a new web service up
to its deployment health deadline; do not repeatedly restart it while the image
is still building or Flyway is running.

Copy the service URL, normally:

```text
https://ticketnest-devtest.onrender.com
```

Verify from PowerShell:

```powershell
$baseUrl = "https://ticketnest-devtest.onrender.com"
Invoke-RestMethod "$baseUrl/actuator/health"
```

Expected response:

```json
{"status":"UP"}
```

Confirm production API documentation is disabled:

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" "$baseUrl/swagger"
curl.exe -s -o NUL -w "%{http_code}`n" "$baseUrl/swagger-ui/index.html"
curl.exe -s -o NUL -w "%{http_code}`n" "$baseUrl/v3/api-docs"
```

Each should return `404`.

### Confirm Flyway in Neon

Open Neon > project `ticketnest-devtest` > **SQL Editor**. Select the production
branch and `ticketnest` database, then run:

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Expected: one successful row for version `1`, description `initial schema`.

List the tables:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
```

Expected application tables:

```text
booking_seats
bookings
notifications
payments
refresh_tokens
seats
shows
users
venues
```

`flyway_schema_history` should also appear. Do not manually create or edit the
application tables in Neon.

## 11. Require CI before every Render deployment

After the first service is healthy:

1. Render > `ticketnest-devtest` > **Settings**.
2. Find **Auto-Deploy**.
3. Select **After CI Checks Pass**.
4. Confirm the linked branch is exactly `master`.
5. Save.

Do not use **On Commit**. With **After CI Checks Pass**, Render detects GitHub
Actions checks on each new `master` commit and deploys only when all detected
checks are successful, neutral, or skipped. It will not deploy when no checks are
detected or when any check fails.

Do not create a Render deploy hook for this design. A hook is unnecessary and
would introduce another secret capable of triggering deployments.

## 12. Configure GitHub `master` protection manually

First push the workflow changes to a feature branch so GitHub has seen the three
check names at least once:

```powershell
git switch -c setup/render-neon
git add .github/workflows/ci-cd.yml
git add -A docs
git commit -m "Replace Azure deployment plan with Render and Neon"
git push -u origin setup/render-neon
```

Wait for these checks to appear and pass:

```text
Build Validation
Unit Tests
Integration Tests
```

Then open the GitHub repository:

1. **Settings** > **Rules** > **Rulesets**.
2. Select **New ruleset** > **New branch ruleset**.
3. Name: `protect-master`.
4. Enforcement status: `Active`.
5. Target branches > include default branch, or include by pattern `master`.
6. Enable **Restrict deletions**.
7. Enable **Block force pushes**.
8. Enable **Require a pull request before merging**.
9. For a solo project, do not require approving reviews; PR creation is still
   mandatory.
10. Enable **Require status checks to pass** and select:
    - `Build Validation`
    - `Unit Tests`
    - `Integration Tests`
11. Enable **Require branches to be up to date before merging**.
12. Enable **Require conversation resolution before merging**.
13. Enable **Require linear history** if squash merging will be used.
14. Do not configure a routine bypass.
15. Apply the rules to administrators if the UI and plan support it.
16. Save the ruleset.

Repository **Settings** > **General** > **Pull Requests**:

1. Enable **Allow squash merging**.
2. Disable merge methods you do not intend to use.
3. Optionally enable automatic deletion of head branches.

Now open a PR from `setup/render-neon` to `master`. Confirm all three checks pass,
then squash-merge it. A direct push to `master` should be rejected when protection
is supported by the repository plan.

## 13. Validate CI and CD behavior

### Feature-branch test

1. Create another feature branch.
2. Push a harmless documentation change.
3. Confirm all three GitHub CI jobs run.
4. Confirm Render creates no deployment because it watches only `master`.

### Failed-CI test

1. On a feature branch, temporarily introduce a failing test.
2. Push it.
3. Confirm the failing status blocks the PR.
4. Fix the test; do not merge deliberately broken code.

### Master deployment test

1. Merge a passing PR into `master`.
2. Open GitHub **Actions** and watch the workflow run again for the actual merged
   master commit.
3. Open Render **Events**.
4. Confirm Render waits until Build Validation, Unit Tests, and Integration Tests
   complete.
5. Confirm Render deploys the same master commit only after they pass.
6. Confirm `/actuator/health` returns `UP`.
7. Confirm Swagger and OpenAPI still return 404.

## 14. How future Flyway migrations work

For every entity/schema change:

1. Change the Java entity and repository code on a feature branch.
2. Generate or write a new migration such as:

```text
src/main/resources/db/migration/V2__add_user_phone.sql
```

3. Review the SQL manually.
4. Run integration tests against a clean PostgreSQL 17 Testcontainer.
5. Push the branch; CI runs without touching Neon.
6. Merge the PR only after CI passes.
7. Render builds the merged `master` commit.
8. During new-instance startup, Flyway applies pending migrations to Neon.
9. Hibernate validates that the migrated schema matches the entities.
10. Render routes traffic only after the application passes its health check.

Never edit an already-applied migration. Add `V3`, `V4`, and so forth. Prefer
backward-compatible expand/contract changes.

Render can roll application code back, but it does not reverse a Flyway database
migration. A rollback must remain compatible with the migrated schema. Back up
important data before destructive migrations.

## 15. Secrets and configuration rules

- `application.yml` contains safe shared behavior and environment placeholders.
- `application-local.yml` may contain disposable local defaults.
- `application-prod.yml` disables Swagger and local TLS.
- Render owns production environment-variable values.
- Neon owns database credentials.
- GitHub Actions does not need production database credentials because its
  integration tests use Testcontainers.
- Never add the Neon connection string, database password, or JWT secret to
  GitHub repository variables, workflow logs, source files, Docker build args,
  issues, or chat.
- Rotate a Neon role password in Neon, update `DB_PASSWORD` in Render, and deploy
  or restart the service in a controlled sequence.
- Rotating `JWT_SECRET` invalidates existing access tokens.

Render environment changes do not always affect an already-running instance
until a new deployment/restart incorporates them. Verify the active deployment
after every secret change.

## 16. Monitoring, cold starts, and logs

### Render

Use service **Events**, **Logs**, **Metrics**, and **Settings**:

- Events show builds, deploys, restarts, and rollbacks.
- Logs contain structured Spring Boot output.
- Health check path must remain `/actuator/health`.
- A free service sleeping after 15 idle minutes is expected.
- Do not use artificial keep-alive traffic merely to evade free-tier sleeping.

### Neon

Use **Monitoring** and **SQL Editor**:

- Track compute, storage, and connection usage.
- Check `flyway_schema_history` after each schema release.
- Investigate excessive connections before increasing Hikari's pool size.
- Review the Free-plan usage page regularly.

Do not enable detailed Actuator health output publicly. The current production
profile returns only overall health status.

## 17. Rollback and recovery

If a new Render deployment fails its health check, Render keeps the prior healthy
deployment serving traffic. To roll back manually:

1. Render service > **Events**.
2. Find a known-good deployment.
3. Select its rollback/redeploy action.
4. Verify health and logs.

Before rollback, inspect `flyway_schema_history`. If the failed release applied a
migration, confirm the older application is compatible with the newer schema.
Never delete rows from `flyway_schema_history` to pretend a migration did not run.

For important data, schedule manual logical exports using `pg_dump` from a secure
machine. Test that a dump can be restored. Free hosting is not a substitute for a
documented backup and recovery policy.

## 18. Troubleshooting

| Symptom | Most likely cause/action |
|---|---|
| Render build fails | Open build logs; verify Dockerfile path is `./Dockerfile` |
| `OutOfMemoryError` or exit 137 | Confirm `JAVA_TOOL_OPTIONS`; reduce workload or move from 512 MB Free |
| No open port detected | Confirm `PORT=8080`, container listens on 8080, and Dockerfile exposes 8080 |
| Database hostname error | Copy the exact Neon host again; do not include `postgresql://` in the hostname |
| Password authentication failed | Confirm role `ticketnest_app` and update Render `DB_PASSWORD` |
| SSL/channel-binding error | Confirm `sslmode=require` and use the exact Neon JDBC hostname |
| Flyway permission denied on schema | Confirm `ticketnest_app` owns `ticketnest` or grant `USAGE, CREATE` on `public` |
| Flyway checksum mismatch | An applied migration was edited; restore it and create a new version |
| Hibernate validation fails | Migration schema and entity model differ; fix with a new migration |
| Health check fails | Inspect startup logs, database connectivity, Flyway, memory, and port binding |
| First request is slow | Normal Render Free cold start after idle sleep |
| Render deploys before CI | Change Auto-Deploy from **On Commit** to **After CI Checks Pass** |
| Render never deploys | Ensure master has GitHub checks and no check, including old Azure jobs, is failing |
| Direct master push succeeds | Branch protection unavailable/misconfigured or admin bypass remains enabled |
| Swagger is accessible | `SPRING_PROFILES_ACTIVE` is not `prod` or old deployment is still active |

## 19. Acceptance checklist

- [ ] Azure deployment job was removed from `.github/workflows/ci-cd.yml`.
- [ ] CI runs Build Validation, Unit Tests, and Integration Tests on every branch push.
- [ ] Neon project uses PostgreSQL 17 where available.
- [ ] Database `ticketnest` exists and is owned by `ticketnest_app`.
- [ ] Neon JDBC traffic requires TLS.
- [ ] Render service uses Docker, Free compute, Singapore, and branch `master`.
- [ ] Render contains all required environment variables and no secret is in Git.
- [ ] Render health check is `/actuator/health`.
- [ ] First startup applies Flyway `V1` successfully.
- [ ] Hibernate schema validation succeeds.
- [ ] `flyway_schema_history` contains one successful `V1` record.
- [ ] All nine application tables exist.
- [ ] `/actuator/health` returns 200/UP.
- [ ] Swagger and OpenAPI endpoints return 404.
- [ ] Render Auto-Deploy is **After CI Checks Pass**.
- [ ] Feature-branch pushes run CI and do not deploy.
- [ ] Failed checks block merge and deployment.
- [ ] A merged PR reruns CI on `master` and deploys only after checks pass.
- [ ] `master` rejects direct pushes when the GitHub plan supports protection.

## 20. Cleanup

Deleting these resources is permanent. Export any data you need first.

To remove the deployment:

1. Render > service `ticketnest-devtest` > **Settings** > delete the service.
2. Neon > project `ticketnest-devtest` > **Settings** > delete the project.
3. Remove Render's GitHub App repository access if it is no longer needed.
4. Remove obsolete GitHub Azure environment variables/secrets only after
   confirming no remaining workflow uses them.

Deleting the Neon project deletes its branches, databases, roles, and data. Do
not delete it merely to troubleshoot an application deployment.

## Official references

- [Render: first deployment](https://render.com/docs/your-first-deploy)
- [Render: Docker deployments](https://render.com/docs/docker)
- [Render: deploy only after CI checks pass](https://render.com/docs/deploys)
- [Render: health checks](https://render.com/docs/health-checks)
- [Render: port binding](https://render.com/docs/web-services#port-binding)
- [Render: regions](https://render.com/docs/regions)
- [Render: free-tier limitations](https://render.com/docs/free)
- [Neon: manage projects](https://neon.com/docs/manage/projects)
- [Neon: manage databases](https://neon.com/docs/manage/databases)
- [Neon: obtain connection details](https://neon.com/docs/connect/connection-errors)
- [Neon: connection pooling](https://neon.com/docs/connect/connection-pooling)
- [GitHub: protected branches](https://docs.github.com/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
- [GitHub: Actions billing and usage](https://docs.github.com/actions/concepts/billing-and-usage)
