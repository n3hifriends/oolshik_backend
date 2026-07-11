# Implementation Plan — Service Area Gate (Post-Auth Onboarding)

## 1. Goal

Restrict the active Oolshik experience to declared geographic service zones while keeping auth (OTP + Google) open to everyone. A user who authenticates but is outside any active zone is held at a "Coming Soon" screen. A user inside a zone completes the normal onboarding flow.

---

## 2. Pre-Implementation Decisions

All ambiguities from the original problem statement are resolved here. No implementation begins until this section is stable.

### 2.1 Flyway position — DDL vs. DML

The problem statement said "not Flyway migration." This conflated table schema creation (DDL) with zone data seeding (DML).

**Decision:** Flyway migrations are **required** for table schemas (`service_zone`, `zone_waitlist`, and the `app_user` column additions). This is consistent with every other table in this project and avoids startup failures. Only the zone **data rows** (actual circle/polygon definitions) are seeded via direct DB insert — not via migration files. No deploy is needed when zone data changes; only a schema change requires a migration.

### 2.2 Zone check HTTP method

`GET /api/zone/check?lat=&lng=` puts coordinates in the URL, which appears in server access logs, load balancer logs, and CDN caches.

**Decision:** Use `POST /api/zone/check` with coordinates in the request body. Response shape is unchanged: `{ eligible: bool, zoneName: String }`.

### 2.3 Phase gate — exact endpoints named

The problem statement had a placeholder. After reading the code:

| Transition | Where it happens |
|---|---|
| `FRESH → INTENT_SET` | `PUT /api/auth/me` — frontend sends `{ "onboardingPhase": "INTENT_SET" }` on consent completion |
| `INTENT_SET → FIRST_ACTION` | `HelpRequestService.create()` (requester) and `HelpRequestService.accept()` (helper) |
| `FIRST_ACTION → GRADUATED` | `HelpRequestService.confirmCompletion()` |

The zone guard sits at `PUT /api/auth/me`. Out-of-zone users never pass FRESH, so they cannot reach FIRST_ACTION or GRADUATED through normal flow.

### 2.4 Backend enforcement model for already-graduated users

**Decision:** Backend enforcement is **frontend-only** for graduated users. The backend's `POST /api/zone/check` returns `eligible: false` and the app shows the "Coming Soon" screen, but action endpoints (`create`, `accept`, `confirmCompletion`) do not re-check zone eligibility per call. The `zoneConfirmed` DB field is a last-known-state cache, not a live gate for action endpoints. This is an explicit, deliberate choice — revisit if abuse or fraud becomes a concern.

### 2.5 `zoneConfirmed` semantics on UserEntity

Naming the fields "session-level zone state" was misleading since they live in the DB.

**Decision:** These are "zone cache fields." They record the result of the most recent zone check call. They are not a permanent grant and not a real-time gate — they exist so the system has a record of the last check. A fresh call to `POST /api/zone/check` always overwrites them.

### 2.6 No active zones — fallback behavior

If no zones are active, every user gets `eligible: false`, which would block the entire app before Phase 1 zone data is seeded.

**Decision:** Add a config flag `app.zone.bypass-when-no-zones-active: true` (default `true` in dev, `false` in prod). When true and the zone table has zero active rows, the check returns `{ eligible: true, zoneName: null }`. The flag must be explicitly set to `false` in prod only **after** zone data has been seeded — this is a manual deployment step, not a code default.

### 2.7 Bucket4j dependency

Not in `pom.xml`. Spring Boot version is **3.3.2**.

**Decision:** Add `io.github.bucket4j:bucket4j-spring-boot-starter:8.10.1`. The `8.x` line supports Spring Boot 3.x; older `7.x` does not. Pin the version explicitly — Spring Boot's BOM does not manage Bucket4j.

### 2.8 `boundary` column mapping in `ServiceZoneEntity`

The existing codebase pattern for JSON-like columns (e.g., `NotificationOutboxEntity.payload_json`) uses `@Column(columnDefinition = "TEXT")` with manual Jackson serialization at the service layer — no Hibernate type handler, no third-party library.

**Decision:** Match that pattern. The `boundary` column is `TEXT` in both the Flyway migration and the entity. `ZoneCheckService` is the only consumer and is responsible for deserializing the JSON string into a list of lat/lng value objects before running the ray-casting algorithm.

### 2.9 Zone delete behavior

Hard-deleting a zone creates a FK integrity problem: `app_user.confirmed_zone_id` references the zone row. The `active` flag already serves as functional soft-delete.

**Decision:** The Phase 2 admin `DELETE /api/admin/zone/{id}` endpoint **does not delete the row**. It sets `active = false`. Hard-delete is not exposed via the API — it is a rare, manual DB-level operation if ever needed for data hygiene.

### 2.10 Waitlist re-engagement notification

The existing notification infrastructure (Kafka, notification-worker, FCM sender, `NotificationEventType` enum) is already in place.

**Decision:** Add `ZONE_NOW_AVAILABLE` to `NotificationEventType`. The trigger is the Phase 2 admin zone-activate action (`PUT /api/admin/zone/{id}` flipping `active` to `true`, or updating geometry of an already-active zone). At that point, the backend runs a batch geometry scan across all `zone_waitlist` rows, applies the same `ZoneCheckService` geometry logic, and publishes a `ZONE_NOW_AVAILABLE` notification event for each user whose stored approximate lat/lng now falls within the zone. This is a Phase 2 concern — Phase 1 has no admin API to trigger it.

### 2.11 Admin UI tech surface

There is already an admin web app at `../../oolshik_web` (`oolshik-admin`, Expo-based).

**Decision:** Phase 3 admin zone management UI is built inside the existing `oolshik_web` admin app — not a new surface. No new tech surface decision is needed.

---

## 3. Database Schema Additions

Three Flyway migrations, in order. File names follow the existing project convention.

### Migration 1 — `service_zone` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `name` | VARCHAR(128), NOT NULL | Human-readable label |
| `type` | VARCHAR(16), NOT NULL | `CIRCLE` or `POLYGON` |
| `active` | BOOLEAN, NOT NULL, default false | Admin toggles this to activate. This flag is the soft-delete mechanism — no `deleted_at` column needed. |
| `center_lat` | DOUBLE PRECISION, nullable | Circle only; must be null for polygon |
| `center_lng` | DOUBLE PRECISION, nullable | Circle only; must be null for polygon |
| `radius_km` | DOUBLE PRECISION, nullable | Circle only; must be null for polygon |
| `boundary` | TEXT, nullable | Polygon only; must be null for circle. Stored as a JSON string — ordered array of `{lat, lng}` objects. Deserialized by `ZoneCheckService` at runtime. |
| `created_at` | TIMESTAMPTZ, NOT NULL | |
| `updated_at` | TIMESTAMPTZ, NOT NULL | |

Index on `active` for the query that loads active zones on each check. No geometry indexes needed at Phase 1 scale.

### Migration 2 — `zone_waitlist` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `user_id` | UUID, NOT NULL, FK → `app_user.id` | Authenticated user, not raw phone |
| `approx_lat` | DOUBLE PRECISION | Rounded to two decimal places (~1 km precision) before storage |
| `approx_lng` | DOUBLE PRECISION | Same rounding |
| `created_at` | TIMESTAMPTZ, NOT NULL | |
| `updated_at` | TIMESTAMPTZ, NOT NULL | |

Unique constraint on `user_id`. Repeated "Check Again" failures upsert on conflict — update `approx_lat`, `approx_lng`, `updated_at`.

### Migration 3 — Zone cache fields on `app_user`

Add three nullable columns to the existing `app_user` table:

| Column | Type | Notes |
|---|---|---|
| `zone_confirmed` | BOOLEAN, NOT NULL, default false | Result of most recent zone check call |
| `confirmed_zone_id` | UUID, nullable | FK → `service_zone.id`; null if out-of-zone or never checked |
| `zone_confirmed_at` | TIMESTAMPTZ, nullable | Timestamp of last `POST /api/zone/check` call |

---

## 4. New Backend Components

### 4.1 Entities

**`ServiceZoneEntity`**
Maps to `service_zone`. All fields as above. The `boundary` field is a plain `String` (`@Column(columnDefinition = "TEXT")`), consistent with the existing `NotificationOutboxEntity.payload_json` pattern. No `@Type` annotation, no Hibernate type handler.

**`ZoneWaitlistEntity`**
Maps to `zone_waitlist`. Holds `userId` (raw UUID, no `@ManyToOne`) and the two approximate coordinate fields.

### 4.2 Repositories

**`ServiceZoneRepository`**
One custom query: find all rows where `active = true`. No pagination — active zone count is expected to be in single digits at launch.

**`ZoneWaitlistRepository`**
Needs: find by `userId`, and a native upsert query (`INSERT ... ON CONFLICT (user_id) DO UPDATE`) for the idempotent waitlist write.

### 4.3 Geometry Service — `ZoneCheckService`

Stateless service. No DB access, no Spring dependencies beyond construction — fully unit-testable.

Takes a lat/lng point and a list of `ServiceZoneEntity` records. For each zone:

- **CIRCLE:** compute Haversine distance from point to (`center_lat`, `center_lng`). Eligible if distance ≤ `radius_km`.
- **POLYGON:** deserialize `boundary` TEXT to a list of lat/lng vertices using Jackson, then apply ray-casting algorithm. Eligible if point is inside.

Returns the first matching zone (wrapped in `Optional`), or empty if none match. The iteration order is not guaranteed — when multiple zones overlap, whichever matches first is returned.

### 4.4 Zone Check Endpoint — `ZoneController`

`POST /api/zone/check` — JWT-authenticated (covered by `anyRequest().authenticated()` in `SecurityConfig`; no filter chain change needed).

Rate-limited via Bucket4j (`bucket4j-spring-boot-starter:8.10.1`). One bucket per authenticated user ID. Suggested limit: 10 calls per minute. Exceeding the limit returns HTTP 429.

Request body: `{ lat: double, lng: double }`

Behavior:
1. Load all active zones from `ServiceZoneRepository`.
2. If zero active zones and `app.zone.bypass-when-no-zones-active` is `true` → return `{ eligible: true, zoneName: null }` without updating zone cache fields.
3. Delegate to `ZoneCheckService`.
4. Update `zone_confirmed`, `confirmed_zone_id`, `zone_confirmed_at` on the current user record regardless of result (so a `false` result also clears a previously `true` cache).
5. Return `{ eligible: bool, zoneName: String | null }`.

Does not advance `onboardingPhase`. Phase advancement is a separate, explicit client action via `PUT /api/auth/me`.

### 4.5 Waitlist Endpoint — `ZoneController` (same controller)

`POST /api/zone/waitlist` — JWT-authenticated.

Request body: `{ lat: double, lng: double }`

Behavior:
1. Round coordinates to two decimal places before storing.
2. Upsert into `zone_waitlist` on `user_id` conflict — update `approx_lat`, `approx_lng`, `updated_at`.
3. Return `{ queued: true }`.

---

## 5. Changes to Existing Files

### 5.1 `entity/UserEntity.java`

Add the three zone cache fields: `zoneConfirmed` (boolean), `confirmedZoneId` (UUID), `zoneConfirmedAt` (OffsetDateTime). All nullable except `zoneConfirmed` (default false). Store `confirmedZoneId` as a raw UUID — no `@ManyToOne` — to keep `UserEntity` decoupled from zone management.

### 5.2 `web/AuthController.java` — `PUT /api/auth/me`

This is the `FRESH → INTENT_SET` gate. When the request body contains `onboardingPhase: INTENT_SET` and the current user's phase is `FRESH`:

- Check `user.isZoneConfirmed()`.
- If `false`: reject with HTTP 403 and `{ error: "zone_not_confirmed" }`. Do not advance the phase.
- If `true`: allow the existing forward-advancement logic to proceed unchanged.

No change needed for `INTENT_SET → FIRST_ACTION` or `FIRST_ACTION → GRADUATED` — those are unreachable by out-of-zone users.

### 5.3 `service/UserService.java`

No change to `advanceOnboardingPhase`. The zone check is enforced upstream in the controller before `advanceOnboardingPhase` is called. Keeping the gate at the API boundary is simpler and keeps `UserService` free of zone dependencies.

### 5.4 `pom.xml`

Add:
```
io.github.bucket4j:bucket4j-spring-boot-starter:8.10.1
```

### 5.5 `src/main/resources/application.yml`

Add under `app`:
```
app:
  zone:
    bypass-when-no-zones-active: true
```

### 5.6 `src/main/resources/application-prod.yml`

Add:
```
app:
  zone:
    bypass-when-no-zones-active: false
```

This override must be in place **before** any prod deployment, but zone data must be seeded **before** this flag takes effect. Order of operations: seed zone rows first, then set flag to false, then deploy. Sequencing this wrong locks out all users.

---

## 6. Phase 2 — Admin Zone API

New controller: `AdminZoneController` under `/api/admin/zone/**`. Already protected by `hasRole("ADMIN")` in `SecurityConfig` — no filter chain change needed.

### 6.1 Endpoints

- `GET /api/admin/zone` — list all zones (active and inactive), paginated
- `POST /api/admin/zone` — create a new zone
- `PUT /api/admin/zone/{id}` — update zone definition or toggle `active`
- `DELETE /api/admin/zone/{id}` — sets `active = false`. Does **not** delete the row. Hard-delete is a manual DB operation only.

### 6.2 Input Validation

- **Circle:** `center_lat`, `center_lng`, `radius_km` all required; `boundary` must be absent or null.
- **Polygon:** `boundary` required, minimum 3 vertices; `center_lat`, `center_lng`, `radius_km` must be absent or null.
- `name`: required, non-blank, max 128 chars.
- `type`: must be `CIRCLE` or `POLYGON`.

### 6.3 Waitlist Re-engagement on Zone Activate

When `PUT /api/admin/zone/{id}` results in a zone becoming active (either `active` toggled to `true`, or geometry updated on an already-active zone):

1. Load all rows from `zone_waitlist`.
2. For each row, deserialize `approx_lat`/`approx_lng` and run `ZoneCheckService` against the updated/activated zone only (not all zones).
3. For each waitlisted user whose location falls within the zone, publish a `ZONE_NOW_AVAILABLE` event to the existing Kafka notification pipeline.
4. The notification-worker picks this up and delivers via FCM using an existing template or a new `ZONE_NOW_AVAILABLE` template in `AdminNotificationTemplateEntity`.

`ZONE_NOW_AVAILABLE` must be added to `NotificationEventType` in the main app and the notification-worker's copy if it maintains its own.

This batch scan runs synchronously on the activate request for Phase 2. If waitlist size grows large, move to an async job — but that is a Phase 3 concern.

---

## 7. Phase 3 — Admin UI

Built inside the existing `oolshik_web` admin app (`oolshik-admin`, Expo). No new tech surface.

Features:
- Zone list view with active/inactive status and toggle.
- Create zone: circle (map-based center pin + radius slider) or polygon (vertex drawing tool on map).
- Edit zone geometry or rename.
- "Delete" (deactivate) with confirmation.
- Waitlist viewer: list of waitlisted users with approximate location pins on a map.

The Phase 2 admin API is the only backend dependency for this UI — all zone management operations go through it.

---

## 8. "Coming Soon" Screen — Backend Responsibility Boundary

The backend's responsibility ends at the `POST /api/zone/check` response. The "Coming Soon" screen, the "Check Again" button, the waitlist submission call, and all session-level UI gating are frontend concerns. The backend never returns a redirect or special HTTP status to trigger the screen — the frontend reads `eligible: false` and renders it.

Phase 2 map visualization (showing user location relative to zone boundary) is frontend-only. The backend already exposes everything needed: zone geometry is readable via the admin API, and the zone check response provides eligibility status.

---

## 9. Enforcement Summary

| User state | Zone check result | Backend action |
|---|---|---|
| FRESH, first session | `eligible: false` | `PUT /api/auth/me` rejects INTENT_SET. Frontend shows "Coming Soon." |
| FRESH, first session | `eligible: true` | `PUT /api/auth/me` allows INTENT_SET. Normal onboarding continues. |
| GRADUATED, moved out of zone | `eligible: false` | No backend block on action endpoints. Frontend gates the session. |
| GRADUATED, zone deactivated by admin | `eligible: false` | Same — frontend-only enforcement for current session. |
| Any, no active zones, dev | `eligible: true` (bypass flag) | Normal flow. |
| Any, no active zones, prod | `eligible: false` | Full block — do not flip bypass flag to `false` before seeding zone data. |

---

## 10. Do Not Break

- `POST /api/auth/otp/request`
- `POST /api/auth/otp/verify`
- `POST /api/auth/google`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `PUT /api/auth/me` — for all fields other than `onboardingPhase` advancement past FRESH
- Bearer token authentication in `JwtAuthFilter`
- Existing `advanceOnboardingPhase` behavior in `UserService`
- Existing `HelpRequestService` phase transitions (FIRST_ACTION, GRADUATED)

---

## 11. Validation Steps

### Phase 1

1. `./mvnw -q -DskipTests compile` — clean build with Bucket4j added.
2. Start app, no active zones, `bypass-when-no-zones-active: true` → `POST /api/zone/check` returns `{ eligible: true, zoneName: null }`. Zone cache fields on user are NOT updated (bypass path).
3. Insert a circle zone with `active = false` → check returns `eligible: false`. Zone cache updated to `zoneConfirmed: false`.
4. Set circle zone `active = true` → check with coordinates inside radius → `{ eligible: true, zoneName: <name> }`, `zoneConfirmed: true`. Check with outside coordinates → `eligible: false`, `zoneConfirmed: false`.
5. Insert a polygon zone (`active = true`) → repeat in/out checks.
6. With `zoneConfirmed = false` on user: `PUT /api/auth/me` with `{ onboardingPhase: INTENT_SET }` → HTTP 403, `{ error: "zone_not_confirmed" }`. Phase remains FRESH.
7. Call `POST /api/zone/check` with in-zone coordinates → `zoneConfirmed` flips to true → retry `PUT /api/auth/me` → phase advances to INTENT_SET.
8. `POST /api/zone/waitlist` while out-of-zone → `{ queued: true }`. Call again → same response, no duplicate row.
9. Exceed rate limit (>10 calls/min) on `POST /api/zone/check` → HTTP 429.
10. Run existing auth tests: `./mvnw -q -Dtest=AuthControllerOtpWebMvcTest,GoogleAuthServiceTest test`.

### Phase 2

11. `POST /api/admin/zone` with a circle (missing `center_lat`) → HTTP 400 validation error.
12. `POST /api/admin/zone` with a polygon (fewer than 3 vertices) → HTTP 400 validation error.
13. `DELETE /api/admin/zone/{id}` → row still exists in DB, `active = false`. No FK errors on `app_user.confirmed_zone_id`.
14. `PUT /api/admin/zone/{id}` flipping `active` to `true` with waitlisted users in that zone → `ZONE_NOW_AVAILABLE` events published to Kafka → notification-worker delivers FCM messages.
15. Call all admin endpoints as a non-ADMIN user → HTTP 403.
