# Step-by-Step Deployment Plan for Oolshik Backend on DigitalOcean

This guide assumes you are new to backend deployment and provides a comprehensive walkthrough for deploying the `n3hifriends/oolshik_backend` repository to DigitalOcean App Platform in the `BLR1` region.

It follows the constraints and prerequisites described for this project, including:

- DigitalOcean Managed PostgreSQL with PostGIS
- DigitalOcean Spaces for media
- RBI/NBFC-oriented deployment considerations

## 1. Prerequisites

### Install `doctl` and authenticate

Install the DigitalOcean CLI (`doctl`) on your local machine from the DigitalOcean download page or via your package manager.

Verify installation:

```bash
doctl version
```

Generate a Personal Access Token in the DigitalOcean dashboard:

`Account -> API -> Tokens`

Save the token securely, then authenticate `doctl`:

```bash
doctl auth init --access-token <YOUR_DIGITALOCEAN_ACCESS_TOKEN>
```

### Clone the repository

```bash
git clone https://github.com/n3hifriends/oolshik_backend.git
cd oolshik_backend
```

### Generate a JWT secret

Use a password manager or random-string generator to create a strong 32+ character secret.

This value will be used for `JWT_SECRET`.

### Choose a temporary CORS origin

If you do not yet have a frontend, you can temporarily use:

- `*`
- `http://localhost`

Important:

Replace this with the actual frontend domain before going live.

### Prepare a `.env` file (optional)

You may mirror values from [.env.production.example](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.env.production.example) into a local `.env` file for convenience.

Secrets should ultimately be stored in:

- DigitalOcean App Platform
- GitHub repository secrets

## 2. Provision Managed PostgreSQL with PostGIS

1. In the DigitalOcean Control Panel, open `Databases`.
2. Click `Create Database Cluster`.
3. Choose:
   - Engine: `PostgreSQL`
   - Region: `BLR1 (Bangalore)`
   - Size: a small testing tier such as `1 vCPU / 1 GB RAM`
4. Under additional options, enable `PostGIS`.

Once the cluster is created, open `Connection Details` and copy:

- `Host`
- `Port` (usually `25060`)
- `Database Name`
- `User`
- `Password`

Allow trusted sources:

- your App Platform app
- optionally your current public IP for setup/testing

## 3. Provision DigitalOcean Spaces for Media Storage

1. In the DigitalOcean Control Panel, go to `Spaces`.
2. Click `Create`.
3. Choose region `BLR1`.
4. Name the space, for example:

```text
oolshik-media
```

Choose one of these visibility modes:

- `Private`: recommended for sensitive audio recordings
- `Public`: simpler, but objects are directly accessible

If the space is private, plan to configure a CDN or custom base URL later and set:

```text
MEDIA_S3_PUBLIC_BASE_URL
```

After the space is created:

1. Open the `API` section.
2. Generate an access key and secret key.
3. Save them securely.

These will be used as:

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

## 4. Update the `.do/app.yaml` Specification

Open [.do/app.yaml](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.do/app.yaml).

### Set the GitHub repository

```yaml
repo: n3hifriends/oolshik_backend
branch: main
```

If your default branch is different, update `branch` accordingly.

### Replace database placeholders

```yaml
- key: DB_HOST
  value: <YOUR_DB_HOST>
- key: DB_PORT
  value: "<YOUR_DB_PORT>"
- key: DB_NAME
  value: <YOUR_DB_NAME>
- key: DB_USER
  value: <YOUR_DB_USER>
- key: DB_PASSWORD
  value: <YOUR_DB_PASSWORD>
- key: DB_SSLMODE
  value: require
```

### Set JWT and CORS values

```yaml
- key: JWT_SECRET
  value: <YOUR_32_PLUS_CHAR_SECRET>
- key: APP_CORS_ALLOWED_ORIGINS
  value: "*" # or http://localhost for testing
```

### Configure media storage for DigitalOcean Spaces

```yaml
- key: MEDIA_STORAGE
  value: s3
- key: MEDIA_S3_BUCKET
  value: oolshik-media
- key: AWS_ACCESS_KEY_ID
  value: <YOUR_SPACES_ACCESS_KEY>
- key: AWS_SECRET_ACCESS_KEY
  value: <YOUR_SPACES_SECRET_KEY>
```

Optional, if you are using a CDN or custom public media domain:

```yaml
- key: MEDIA_S3_PUBLIC_BASE_URL
  value: https://cdn.example.com
```

### Keep optional features disabled unless needed

```yaml
- key: APP_SECURITY_FIREBASE_ENABLED
  value: "false"
- key: APP_MESSAGING_KAFKA_ENABLED
  value: "false"
- key: APP_DOCS_ENABLED
  value: "false"
- key: ADMIN_SEED_ENABLED
  value: "false"
```

Save the file and commit the changes to GitHub.

## 5. Set Secrets and Environment Variables

### GitHub repository secrets

In GitHub:

`Settings -> Secrets and variables -> Actions`

Add:

- `DIGITALOCEAN_ACCESS_TOKEN`
- `DIGITALOCEAN_APP_ID` after the first deployment

### DigitalOcean-managed secrets

Sensitive values defined in `.do/app.yaml` should be treated as deployment secrets and managed via DigitalOcean App Platform.

Do not commit real secrets to source control.

## 6. Validate the App Spec

From the root of the repository, run:

```bash
doctl apps spec validate .do/app.yaml --schema-only
```

If validation fails, correct the reported issues first.

## 7. Deploy the Application

Because this is the first deployment, there is no existing App ID yet.

Run:

```bash
doctl apps create --spec .do/app.yaml --wait
```

The `--wait` flag blocks until the app is created and deployment finishes.

When deployment completes:

- note the App ID from the output
- save it securely
- add it to GitHub as `DIGITALOCEAN_APP_ID`

## 8. Verify the Deployment

Find the default domain assigned to your app from:

- `doctl` output
- DigitalOcean dashboard

It will usually look like:

```text
https://<app-name>.<random>.ondigitalocean.app
```

Verify the following:

### Health endpoint

Open:

```text
https://<app-domain>/actuator/health
```

Expected result:

- JSON response
- `"status": "UP"`

### OTP flow

Use Postman or `curl` to test:

- `POST /api/v1/auth/otp/send`
- `POST /api/v1/auth/otp/verify`

### JWT-protected routes

Call an authenticated endpoint and confirm JWT authentication is working.

### Swagger access

Swagger/OpenAPI should be disabled in production.

### Media upload

Test media upload through the API and confirm:

- files are stored in Spaces
- files are reachable through the configured media URL strategy

## 9. Prepare for Production

Once you have a real frontend domain, update:

```text
APP_CORS_ALLOWED_ORIGINS
```

Then redeploy:

```bash
doctl apps update <APP_ID> --spec .do/app.yaml --update-sources --wait
```

Also review:

- trusted database sources
- whether optional features should remain disabled
- monitoring and logging
- CPU and memory usage
- DB connection usage
- application error rates

## 10. Environment Variable Reference

Recommended production values to review:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `DB_SSLMODE`
- `JWT_SECRET`
- `APP_CORS_ALLOWED_ORIGINS`
- `MEDIA_STORAGE`
- `MEDIA_S3_BUCKET`
- `MEDIA_S3_PUBLIC_BASE_URL`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

Optional feature flags:

- `APP_SECURITY_FIREBASE_ENABLED`
- `APP_MESSAGING_KAFKA_ENABLED`
- `APP_DOCS_ENABLED`
- `ADMIN_SEED_ENABLED`

## 11. Summary

By following this plan, you will:

- install and configure `doctl`
- provision a managed PostgreSQL cluster with PostGIS in `BLR1`
- create a DigitalOcean Spaces bucket and obtain credentials
- update [.do/app.yaml](/Users/nitinkalokhe/Ni3/spring_boot_proj/oolshik-backend-otp/.do/app.yaml) with repository, database, JWT, CORS, and Spaces values
- add required secrets to GitHub and DigitalOcean
- validate the app spec
- deploy with `doctl`
- verify health, authentication, and media behavior

This plan is intended to be actionable for someone without prior backend deployment experience while still respecting security, localization, and compliance expectations.

As the application grows, you can later adjust:

- resource sizes
- media strategy
- Kafka and worker services
- monitoring depth
- access policies
