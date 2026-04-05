# Oolshik DigitalOcean MVP Deployment

## Executive Summary

This repo is not a pure OTP-only service. It contains:

- the Spring Boot API
- Kafka-backed notification and transcription integrations
- media upload/storage code
- Firebase Admin integration
- PostGIS-backed schema migrations

For the MVP deployment, the recommended scope is **API-only** on **DigitalOcean App Platform** plus **Managed PostgreSQL in BLR1**.

### In scope

- OTP request and verify flows
- JWT access/refresh flows
- authenticated API access using app JWTs
- managed PostgreSQL with Flyway and PostGIS
- App Platform deployment with environment-variable-based configuration
- CI/CD via GitHub Actions

### Out of scope for MVP

- Kafka-backed workers
- STT worker deployment
- notification worker deployment
- production media upload/storage
- Firebase-backed auth flows

These are explicitly disabled in the `prod` profile so the MVP can deploy cleanly.

## Repo Findings

The following production blockers were addressed in code:

- API JWT auth now works with existing controller principals.
- Firebase is optional in `prod`.
- Kafka-backed publishers/listeners are optional in `prod`.
- Swagger/OpenAPI is disabled in `prod`.
- CORS is no longer wildcard by default.
- admin seeding is disabled by default in `prod`.
- Flyway seed data was moved out of the default production migration path.
- Docker now defaults to `prod` and runs as a non-root user.

## Step 0: Prerequisites

Prepare these before touching DigitalOcean:

1. Push this repo to GitHub.
2. Decide the production frontend origin that will call this API.
3. Generate a strong `JWT_SECRET` of at least 32 characters.
4. Install `terraform` and `doctl`.
5. Create a DigitalOcean personal access token.
6. Confirm you are deploying the API-only MVP and not the Kafka/media/STT stack.

## Step 1: Provision the DigitalOcean Foundation

From [infra/digitalocean/terraform](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/infra/digitalocean/terraform):

```bash
cd /Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/infra/digitalocean/terraform
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -var="do_token=$DIGITALOCEAN_ACCESS_TOKEN"
terraform apply -var="do_token=$DIGITALOCEAN_ACCESS_TOKEN"
```

Expected result:

- one BLR1 VPC
- one BLR1 managed PostgreSQL cluster
- one database named `oolshik`
- one application DB user

## Step 2: Enable PostGIS

Connect to the managed database using the private host output and run:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

The application migrations also contain `CREATE EXTENSION IF NOT EXISTS postgis`, but you should validate this explicitly as part of provisioning because the app depends on PostGIS-backed columns and indexes.

## Step 3: Prepare Production Environment Values

Start from [.env.production.example](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.env.production.example).

Required values:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `DB_SSLMODE=require`
- `JWT_SECRET`
- `APP_CORS_ALLOWED_ORIGINS`

For the API-only MVP, keep these disabled:

- `APP_SECURITY_FIREBASE_ENABLED=false`
- `APP_MESSAGING_KAFKA_ENABLED=false`
- `APP_DOCS_ENABLED=false`
- `ADMIN_SEED_ENABLED=false`

## Step 4: Update the App Platform Spec

Edit [.do/app.yaml](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.do/app.yaml):

1. Replace `YOUR_GITHUB_ORG/YOUR_REPO`.
2. Replace the DB placeholders with the managed DB outputs.
3. Replace `JWT_SECRET`.
4. Replace `APP_CORS_ALLOWED_ORIGINS`.

Validate the spec locally:

```bash
doctl apps spec validate /Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.do/app.yaml --schema-only
```

## Step 5: Create the App Platform App

Create the app the first time:

```bash
doctl apps create \
  --spec /Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.do/app.yaml \
  --wait
```

Capture the returned App ID. You need it for GitHub Actions.

## Step 6: Wire GitHub Actions Secrets

In GitHub repository secrets, add:

- `DIGITALOCEAN_ACCESS_TOKEN`
- `DIGITALOCEAN_APP_ID`

The workflows provided are:

- [.github/workflows/ci.yml](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.github/workflows/ci.yml)
- [.github/workflows/deploy-app-platform.yml](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.github/workflows/deploy-app-platform.yml)

## Step 7: Deploy

Manual deploy:

```bash
doctl apps update APP_ID \
  --spec /Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.do/app.yaml \
  --update-sources \
  --wait
```

Or push to `main` and let the deploy workflow run.

## Step 8: Validate the Running Service

Run these checks in order:

1. Confirm the app reaches `ACTIVE` state in App Platform.
2. Open `/actuator/health` and confirm `UP`.
3. Verify Flyway completed successfully.
4. Request an OTP using `/api/auth/otp/request`.
5. Verify the OTP using `/api/auth/otp/verify`.
6. Call an authenticated endpoint using the returned JWT.
7. Confirm the database has no seed/demo rows from `V2__seed_data.sql`.
8. Confirm Swagger endpoints are not publicly accessible in `prod`.
9. Confirm cross-origin requests only work from the configured frontend origin.

## Step 9: Security and Compliance Checks

Infrastructure-aligned controls in this MVP:

- persistent data stays in BLR1
- DB traffic uses managed PostgreSQL with TLS required
- app secrets are externalized
- public docs are disabled
- non-essential messaging dependencies are disabled
- App Platform health checks are configured

Controls still requiring process, policy, or audit work:

- formal RBI/NBFC control mapping approval
- security policy sign-off
- VAPT execution and remediation tracking
- log retention policy approval
- backup/restore evidence capture
- DR test evidence
- audit trail review and data-retention sign-off

## Step 10: Immediate Post-Deployment Tasks

1. Add your custom domain to App Platform.
2. Enforce HTTPS-only client usage.
3. Configure App Platform alerts and DB alerts in the control plane.
4. Take and store a backup/restore validation record.
5. Record the exact deployed commit SHA.
6. Save the DB host, app URL, app ID, and rotation owners in the runbook.

## Hardening Backlog

- replace placeholder JWT default with a strict fail-fast secret policy
- add explicit rate limiting for OTP endpoints
- add structured audit events for login, refresh, and privileged actions
- add Prometheus metrics export if deeper observability is required
- decide whether Firebase auth remains part of the product direction
- design India-resident media storage before enabling media/STT in production
- design Kafka and worker deployment separately before re-enabling messaging features

## Future Scale Path

When you outgrow the MVP:

1. move from single App Platform instance to multiple instances
2. enable autoscaling after load data exists
3. deploy Kafka-backed workers as separate components
4. move media to an approved India-resident object storage design
5. introduce stronger log shipping and SIEM integration
6. enable Firebase only if the mobile auth architecture requires it and credentials are handled safely
