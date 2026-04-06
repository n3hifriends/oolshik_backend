# Oolshik Backend — Phase 1 (OTP-first Auth) — Spring Boot 3, Java 21

A clean, extensible backend for **Oolshik Phase 1** with **mobile number + OTP login** as primary auth flow and **optional email** capture. Structured for easy extension into next phases.

## Highlights

- **OTP-first auth** (SMS). Email is optional during OTP verify or later via profile update
- **JWT** access & refresh tokens
- **Users & Roles**: NETA, KARYAKARTA, ADMIN
- **Help Requests**: create, nearby search, accept, complete, cancel
- **PostgreSQL + Flyway** schema migrations
- **Swagger/OpenAPI** docs
- **Docker compose** for 1-command local bring-up
- **Clean structure**: domain, entity, repo, service, web, security, util

## Quick Start

### A) Docker (recommended)

```bash
docker compose up -d --build <- this will build all i.e. backend, stt-worker, notification-worker
docker compose logs -f api
docker compose logs -f stt-worker
docker compose logs -f notification-worker
docker exec -it oolshik-backend-otp-db-1 psql -U oolshik -d oolshik
docker exec -it oolshik-backend-otp-api-1 sh -lc 'ls -R ./data/audio || ls -R /data/audio'
docker compose exec -T db \
  pg_dump -U oolshik -d oolshik \
  --schema-only --no-owner --no-privileges > ./postgres_V1__baseline_schema.sql
docker compose exec -T db \
  pg_dump -U oolshik -d oolshik \
  --data-only --inserts --no-owner --no-privileges > ./postgres_V2__seed_data.sql
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui/index.html
- Dev mode can return OTP code in the response payload when `APP_OTP_DEV_ENABLED=true`.

### B) Local (no Docker)

1. Start PostgreSQL and create DB `oolshik` (user/pass `oolshik`), or set env vars below.
2. Run:

```bash
JWT_SECRET=devsecret_at_least_32_chars_long_123456 ADMIN_EMAIL=admin@oolshik.app ADMIN_PASSWORD=Admin@123 SPRING_PROFILES_ACTIVE=dev APP_OTP_PROVIDER=dev APP_OTP_DEV_ENABLED=true ./mvnw spring-boot:run
```

## API (Phase 1)

### Auth (OTP-first)

- `POST /api/auth/otp/request`
  ```json
  { "phone": "+919876543210" }
  ```
  Dev mode response includes `"devCode"` only when `APP_OTP_DEV_ENABLED=true`.
- `POST /api/auth/otp/verify`
  ```json
  {
    "phone": "+919876543210",
    "code": "123456",
    "displayName": "Nitin",
    "email": "n@example.com"
  }
  ```
  Returns `{ "accessToken": "...", "refreshToken": "..." }`. Creates the user if needed and stores optional fields.
- `POST /api/auth/refresh` → `{ "accessToken": "..." }`
- `GET  /api/auth/me` → profile
- `PUT  /api/auth/me` → update `displayName`, `languages`, `email`
- `POST /api/auth/login` (email+password; **admin only** for ops)

### Help Requests

- `POST /api/requests`
- `GET  /api/requests/nearby?lat=..&lon=..&radiusMeters=1000`
- `POST /api/requests/{id}/accept`
- `POST /api/requests/{id}/complete`
- `POST /api/requests/{id}/cancel`

### Transcription (STT)

- `GET /api/transcriptions/{jobId}` → returns job status + transcript when ready

## Configuration

Environment variables (defaults in `application.yml`):

- `DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=oolshik`, `DB_USER=oolshik`, `DB_PASSWORD=oolshik`
- `JWT_SECRET` (**required**; 32+ chars recommended)
- `SPRING_PROFILES_ACTIVE=dev`
- `APP_OTP_PROVIDER=dev|msg91`
- `APP_OTP_DEV_ENABLED=false`
- `APP_OTP_TTL_SECONDS=300`, `APP_OTP_COOLDOWN_SECONDS=30`, `APP_OTP_MAX_ATTEMPTS=5`
- `APP_SECURITY_IDENTITY_PROVIDER=local|firebase`
- `APP_OTP_MSG91_API_KEY`, `APP_OTP_MSG91_TEMPLATE_ID`, `APP_OTP_MSG91_SENDER_ID`, `APP_OTP_MSG91_ENTITY_ID`
- `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`
- `KAFKA_CONSUMER_GROUP=oolshik-stt-backend`
- `KAFKA_TOPIC_STT_JOBS=stt.jobs`, `KAFKA_TOPIC_STT_RESULTS=stt.results`, `KAFKA_TOPIC_STT_DLQ=stt.jobs.dlq`
- `STT_REPUBLISH_DELAY_MS=30000`
- `STT_AUDIO_SOURCE_MODE=REQUEST` (`REQUEST` | `DEMO_FIXED` | `S3_ONLY`)
- `STT_DEMO_AUDIO_URL=https://...` (used only when `STT_AUDIO_SOURCE_MODE=DEMO_FIXED`)
- `STT_S3_ALLOWED_HOST_SUFFIXES=.amazonaws.com,.cloudfront.net` (used when `STT_AUDIO_SOURCE_MODE=S3_ONLY`)
- `STT_REWRITE_LOCAL_PUBLIC_STREAM_FOR_WORKER=true` (in `REQUEST`, rewrites local stream URL host for worker reachability)
- `STT_LOCAL_WORKER_BASE_URL=http://api:8080` (worker-reachable API base in Docker network)
- `MEDIA_LOCAL_PUBLIC_STREAM_ENABLED=false` (enable `/api/public/media/audio/{id}/stream` in local/demo only)

STT audio source modes:

- `REQUEST`: STT uses the incoming `voiceUrl` from frontend/backend request.
- `DEMO_FIXED`: STT ignores request `voiceUrl` and always uses `STT_DEMO_AUDIO_URL`.
- `S3_ONLY`: STT requires HTTPS URL and allowed S3 host suffixes, and rejects local `/api/media/audio/.../stream` URLs.

Recommended modes:

- Local end-to-end (recorded audio -> helper playback + STT): `STT_AUDIO_SOURCE_MODE=REQUEST`, `MEDIA_LOCAL_PUBLIC_STREAM_ENABLED=true`, `STT_REWRITE_LOCAL_PUBLIC_STREAM_FOR_WORKER=true`.
- Production S3: `media.storage=s3`, `STT_AUDIO_SOURCE_MODE=S3_ONLY`, `MEDIA_LOCAL_PUBLIC_STREAM_ENABLED=false`.

**Admin seeding (ops):**

```
ADMIN_EMAIL=admin@oolshik.app
ADMIN_PASSWORD=Admin@123
```

Seeds an admin with placeholder phone `+910000000000`. Admin can login via `/api/auth/login`.

## Design Notes

- **OTP codes** are stored **hashed** (BCrypt), with TTL, resend cooldown, provider metadata, and attempt throttling.
- **Phone normalization**: simple E.164-ish helper defaults to +91 when obvious (10-digit input). Replace with libphonenumber later if needed.
- **OTP delivery**: pluggable `OtpProvider` with `dev` and `msg91` implementations, selected by `app.otp.provider`.
- **Identity verification**: `app.security.identity-provider` independently controls Firebase bearer-token verification.
- **Auditability**: OTP generation, delivery, cooldown blocks, and verification attempts are persisted in `otp_audit_log`.
- **Logging**: OTPs, tokens, passwords, and full phone numbers are redacted from application logs.
- **Nearby search**: simple equirectangular distance in SQL; swap to PostGIS later without changing controller/service contracts.
- **Clean layering**: Controllers → Services → Repos → Entities; DTOs for requests/responses; exception handler for neat API errors.

## Build & Run

```bash
./mvnw -q -DskipTests package
java -jar target/oolshik-backend-0.0.1-SNAPSHOT.jar
```

---

This codebase is intentionally minimal yet production-leaning so you can extend it in next phases (chat, media, ratings, categories, payments) without refactoring auth or user domains.
