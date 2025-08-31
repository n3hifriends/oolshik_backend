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
docker compose build api
docker compose up -d api
docker compose logs -f api
docker exec -it postgres psql -U oolshik -d oolshik
docker exec -it oolshik-backend-otp-api-1 sh -lc 'ls -R ./data/audio || ls -R /data/audio'
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui/index.html
- Dev mode returns OTP code in response payload (for easy mobile integration/testing).

### B) Local (no Docker)

1. Start PostgreSQL and create DB `oolshik` (user/pass `oolshik`), or set env vars below.
2. Run:

```bash
JWT_SECRET=devsecret_at_least_32_chars_long_123456 ADMIN_EMAIL=admin@oolshik.app ADMIN_PASSWORD=Admin@123 SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

## API (Phase 1)

### Auth (OTP-first)

- `POST /api/auth/otp/request`
  ```json
  { "phone": "+919876543210" }
  ```
  Dev mode response includes `"devCode"`.
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

## Configuration

Environment variables (defaults in `application.yml`):

- `DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=oolshik`, `DB_USER=oolshik`, `DB_PASSWORD=oolshik`
- `JWT_SECRET` (**required**; 32+ chars recommended)
- `SPRING_PROFILES_ACTIVE=dev`
- `app.otp.ttlSeconds=300`, `app.otp.cooldownSeconds=30`, `app.otp.maxAttempts=5`

**Admin seeding (ops):**

```
ADMIN_EMAIL=admin@oolshik.app
ADMIN_PASSWORD=Admin@123
```

Seeds an admin with placeholder phone `+910000000000`. Admin can login via `/api/auth/login`.

## Design Notes

- **OTP codes** are stored **hashed** (BCrypt), with TTL, resend cooldown, and attempt throttling.
- **Phone normalization**: simple E.164-ish helper defaults to +91 when obvious (10-digit input). Replace with libphonenumber later if needed.
- **SMS sending**: pluggable `SmsSender` interface; dev profile logs messages; add Twilio/SNS later.
- **Nearby search**: simple equirectangular distance in SQL; swap to PostGIS later without changing controller/service contracts.
- **Clean layering**: Controllers → Services → Repos → Entities; DTOs for requests/responses; exception handler for neat API errors.

## Build & Run

```bash
./mvnw -q -DskipTests package
java -jar target/oolshik-backend-0.0.1-SNAPSHOT.jar
```

---

This codebase is intentionally minimal yet production-leaning so you can extend it in next phases (chat, media, ratings, categories, payments) without refactoring auth or user domains.
