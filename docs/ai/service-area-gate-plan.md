# Implementation Plan — Service Area Gate (Post-Auth Onboarding)

## 1. Goal

Restrict the active Oolshik experience to declared geographic service zones while keeping auth (OTP + Google) open to everyone. A user who authenticates but is outside any active zone is held at a "Coming Soon" screen. A user inside a zone completes the normal onboarding flow.

The gate is runtime-controllable by admins — it can be disabled globally without a redeploy, which allows the full app experience to be tested end-to-end during Play Store / App Store review from any location.

---

## 2. Pre-Implementation Decisions

All ambiguities are resolved here. No implementation begins until this section is stable.

### 2.1 Flyway position — DDL vs. DML

The problem statement said "not Flyway migration." This conflated table schema creation (DDL) with zone data seeding (DML).

**Decision:** Flyway migrations are **required** for all table schemas (`service_zone`, `zone_waitlist`, `system_config`, and the `app_user` column additions). This is consistent with every other table in this project and avoids startup failures. Only zone **data rows** (actual circle/polygon definitions) are seeded via direct DB insert — not via migration files. No deploy is needed when zone data changes; only a schema change requires a migration.

### 2.2 Zone check HTTP method

`GET /api/zone/check?lat=&lng=` puts coordinates in the URL, which appears in server access logs, load balancer logs, and CDN caches.

**Decision:** Use `POST /api/zone/check` with coordinates in the request body. Response shape: `{ eligible: bool, zoneName: String | null }`.

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

**Decision:** Add a static config flag `app.zone.bypass-when-no-zones-active: true` (default `true` in dev, `false` in prod). When true and the zone table has zero active rows, the check returns `{ eligible: true, zoneName: null }`. The flag must be set to `false` in prod only **after** zone data has been seeded. This flag handles the early-setup scenario only — it is separate from the global gate flag (§2.12).

### 2.7 Bucket4j dependency

Not in `pom.xml`. Spring Boot version is **3.3.2**.

**Decision:** Add `io.github.bucket4j:bucket4j-spring-boot-starter:8.10.1`. The `8.x` line supports Spring Boot 3.x; older `7.x` does not. Pin the version explicitly — Spring Boot's BOM does not manage Bucket4j.

### 2.8 `boundary` column mapping in `ServiceZoneEntity`

The existing codebase pattern for JSON-like columns (e.g., `NotificationOutboxEntity.payload_json`) uses `@Column(columnDefinition = "TEXT")` with manual Jackson serialization at the service layer — no Hibernate type handler, no third-party library.

**Decision:** Match that pattern. The `boundary` column is `TEXT` in both the Flyway migration and the entity. `ZoneCheckService` deserializes it into a list of lat/lng value objects before running the ray-casting algorithm.

### 2.9 Zone delete behavior

Hard-deleting a zone creates a FK integrity problem: `app_user.confirmed_zone_id` references the zone row. The `active` flag already serves as functional soft-delete.

**Decision:** `DELETE /api/admin/zone/{id}` sets `active = false`. It does **not** delete the row. Hard-delete is a rare, manual DB-level operation only — never exposed via the API.

### 2.10 Waitlist re-engagement notification

The existing notification infrastructure (Kafka, notification-worker, FCM sender, `NotificationEventType` enum) is already in place.

**Decision:** Add `ZONE_NOW_AVAILABLE` to `NotificationEventType`. Triggered from the Phase 2 admin zone-activate action. On activate, the backend batch-scans all `zone_waitlist` rows using `ZoneCheckService` and publishes a `ZONE_NOW_AVAILABLE` event for each user now in-zone. This is a Phase 2 concern — Phase 1 has no admin API to trigger it.

### 2.11 Admin UI tech surface

There is already an admin web app at `../../oolshik_web` (`oolshik-admin`, Expo-based).

**Decision:** Phase 3 admin zone management UI is built inside the existing `oolshik_web` admin app — not a new surface.

### 2.12 Global gate flag — DB-driven, admin-controlled

A static config file flag (`application.yml`) cannot be toggled at runtime without a redeploy. For Play Store / App Store review, the gate must be globally disableable while the app is already live — reviewers authenticate from arbitrary locations and must reach the full app experience.

**Decision:** The global gate flag is stored in a `system_config` table (key-value, general purpose) as `key = 'zone.gate.enabled'`, `value = 'true' | 'false'`. It is toggled at runtime via the Phase 2 admin API (`PUT /api/admin/zone/gate`), visible and controllable in the Phase 3 admin UI, and cached in memory with a 60-second TTL to avoid a DB hit on every zone check.

When the gate is disabled:
- `POST /api/zone/check` returns `{ eligible: true, zoneName: null }` immediately — no geometry check, no zone cache update on the user.
- `PUT /api/auth/me` skips the `zoneConfirmed` guard — any FRESH user may advance to INTENT_SET.
- All other app behavior is unchanged.

The 60-second TTL means a gate toggle takes effect across all running instances within 60 seconds — acceptable for the review use case. When the review is approved, the admin re-enables the gate from the admin portal.

**Audit:** The `system_config` table stores `updated_by` (UUID → `app_user.id`) and `updated_at` on every row, giving a lightweight audit trail of who last changed each flag and when.

---

## 3. Database Schema Additions

Four Flyway migrations, in order.

### Migration 1 — `service_zone` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `name` | VARCHAR(128), NOT NULL | Human-readable label |
| `type` | VARCHAR(16), NOT NULL | `CIRCLE` or `POLYGON` |
| `active` | BOOLEAN, NOT NULL, default false | Soft-delete mechanism — deactivating is the admin-facing "delete." No `deleted_at` column. |
| `center_lat` | DOUBLE PRECISION, nullable | Circle only; null for polygon |
| `center_lng` | DOUBLE PRECISION, nullable | Circle only; null for polygon |
| `radius_km` | DOUBLE PRECISION, nullable | Circle only; null for polygon |
| `boundary` | TEXT, nullable | Polygon only; null for circle. JSON string — ordered array of `{lat, lng}` objects. |
| `created_at` | TIMESTAMPTZ, NOT NULL | |
| `updated_at` | TIMESTAMPTZ, NOT NULL | |

Index on `active`.

### Migration 2 — `zone_waitlist` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `user_id` | UUID, NOT NULL, FK → `app_user.id` | Authenticated user, not raw phone |
| `approx_lat` | DOUBLE PRECISION | Rounded to two decimal places (~1 km) before storage |
| `approx_lng` | DOUBLE PRECISION | Same rounding |
| `created_at` | TIMESTAMPTZ, NOT NULL | |
| `updated_at` | TIMESTAMPTZ, NOT NULL | |

Unique constraint on `user_id`. Repeated "Check Again" failures upsert on conflict — update `approx_lat`, `approx_lng`, `updated_at`.

### Migration 3 — Zone cache fields on `app_user`

Three nullable columns added to the existing `app_user` table:

| Column | Type | Notes |
|---|---|---|
| `zone_confirmed` | BOOLEAN, NOT NULL, default false | Result of most recent zone check |
| `confirmed_zone_id` | UUID, nullable | FK → `service_zone.id`; null if out-of-zone or never checked |
| `zone_confirmed_at` | TIMESTAMPTZ, nullable | Timestamp of last `POST /api/zone/check` call |

### Migration 4 — `system_config` table + seed

General-purpose runtime configuration table. Scoped to this feature at launch but designed to hold future runtime flags without schema changes.

| Column | Type | Notes |
|---|---|---|
| `key` | VARCHAR(128), PK | Dotted namespace, e.g. `zone.gate.enabled` |
| `value` | TEXT, NOT NULL | String representation of the value |
| `updated_at` | TIMESTAMPTZ, NOT NULL | |
| `updated_by` | UUID, nullable, FK → `app_user.id` | Who last changed this value; null for system-set defaults |

**Seeded in the same migration (DML):**
```
INSERT INTO system_config (key, value, updated_at, updated_by)
VALUES ('zone.gate.enabled', 'true', now(), null);
```
The gate starts enabled. Admins disable it before Play Store review; re-enable after approval.

---

## 4. New Backend Components

### 4.1 Entities

**`ServiceZoneEntity`**
Maps to `service_zone`. The `boundary` field is `String` with `@Column(columnDefinition = "TEXT")` — matches `NotificationOutboxEntity.payload_json` pattern. No `@Type`, no Hibernate type handler.

**`ZoneWaitlistEntity`**
Maps to `zone_waitlist`. `userId` as raw UUID (no `@ManyToOne`).

**`SystemConfigEntity`**
Maps to `system_config`. `key` is the `@Id`. `updatedBy` as raw UUID (no `@ManyToOne`).

### 4.2 Repositories

**`ServiceZoneRepository`**
One custom query: find all rows where `active = true`.

**`ZoneWaitlistRepository`**
Find by `userId`. Native upsert (`INSERT ... ON CONFLICT (user_id) DO UPDATE`).

**`SystemConfigRepository`**
Find by `key`. Standard `findById(key)`.

### 4.3 Geometry Service — `ZoneCheckService`

Stateless, no DB access, no Spring dependencies beyond construction — fully unit-testable.

For each active zone:
- **CIRCLE:** Haversine distance from point to (`center_lat`, `center_lng`). Eligible if ≤ `radius_km`.
- **POLYGON:** Deserialize `boundary` TEXT via Jackson to a vertex list, then apply ray-casting. Eligible if inside.

Returns the first matching zone (`Optional<ServiceZoneEntity>`), or empty.

### 4.4 System Config Service — `SystemConfigService`

Reads the `system_config` table with an in-memory cache (Caffeine or a simple `AtomicReference` + timestamp). TTL: 60 seconds per key.

Exposes:
- `boolean isZoneGateEnabled()` — reads `zone.gate.enabled`, parses to boolean, returns `true` if missing (safe default).
- `void setZoneGateEnabled(boolean enabled, UUID updatedBy)` — writes to DB and **immediately invalidates** the cached value so the calling instance reflects the change in under 1 second. Other instances pick it up within 60 seconds via TTL expiry.

### 4.5 Zone Check Endpoint — `ZoneController`

`POST /api/zone/check` — JWT-authenticated (covered by `anyRequest().authenticated()`; no filter chain change needed).

Rate-limited via Bucket4j. One bucket per authenticated user ID. Limit: 10 calls per minute → HTTP 429 on breach.

Request body: `{ lat: double, lng: double }`

Behavior (in order):
1. **Gate check:** call `SystemConfigService.isZoneGateEnabled()`. If `false` → return `{ eligible: true, zoneName: null }` immediately. No geometry check. No zone cache update on the user.
2. Load all active zones from `ServiceZoneRepository`.
3. If zero active zones and `app.zone.bypass-when-no-zones-active` is `true` → return `{ eligible: true, zoneName: null }`. No zone cache update.
4. Delegate to `ZoneCheckService`.
5. Update `zone_confirmed`, `confirmed_zone_id`, `zone_confirmed_at` on the current user — for both `true` and `false` results (a `false` result clears a previously `true` cache).
6. Return `{ eligible: bool, zoneName: String | null }`.

Does not advance `onboardingPhase`.

### 4.6 Waitlist Endpoint — `ZoneController` (same controller)

`POST /api/zone/waitlist` — JWT-authenticated.

Request body: `{ lat: double, lng: double }`

Behavior:
1. Round coordinates to two decimal places before storing.
2. Upsert into `zone_waitlist` on `user_id` conflict — update `approx_lat`, `approx_lng`, `updated_at`.
3. Return `{ queued: true }`.

---

## 5. Changes to Existing Files

### 5.1 `entity/UserEntity.java`

Add three zone cache fields: `zoneConfirmed` (boolean, default false), `confirmedZoneId` (UUID, nullable), `zoneConfirmedAt` (OffsetDateTime, nullable). `confirmedZoneId` stored as raw UUID — no `@ManyToOne` — to keep `UserEntity` decoupled from zone management.

### 5.2 `web/AuthController.java` — `PUT /api/auth/me`

This is the `FRESH → INTENT_SET` gate. When the request body contains `onboardingPhase: INTENT_SET` and the current user's phase is `FRESH`:

1. **Gate check first:** call `SystemConfigService.isZoneGateEnabled()`. If `false` → skip zone check entirely, allow the advancement. The gate is globally disabled (e.g., during Store review).
2. If gate is enabled: check `user.isZoneConfirmed()`.
   - If `false` → HTTP 403, `{ error: "zone_not_confirmed" }`. Phase stays FRESH.
   - If `true` → allow the existing forward-advancement logic unchanged.

No change for `INTENT_SET → FIRST_ACTION` or `FIRST_ACTION → GRADUATED`.

### 5.3 `service/UserService.java`

No change to `advanceOnboardingPhase`. Gate enforcement stays at the controller boundary.

### 5.4 `pom.xml`

Add:
- `io.github.bucket4j:bucket4j-spring-boot-starter:8.10.1`

### 5.5 `src/main/resources/application.yml`

Add:
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

Set this **only after** zone data has been seeded in prod. Seeding zone rows before flipping this flag is a mandatory deployment prerequisite.

---

## 6. Phase 2 — Admin Zone API

New controller: `AdminZoneController` under `/api/admin/zone/**`. Already protected by `hasRole("ADMIN")` in `SecurityConfig` — no filter chain change needed.

### 6.1 Zone CRUD Endpoints

- `GET /api/admin/zone` — list all zones (active and inactive), paginated
- `POST /api/admin/zone` — create a new zone
- `PUT /api/admin/zone/{id}` — update zone definition or toggle `active`
- `DELETE /api/admin/zone/{id}` — sets `active = false`, does **not** delete the row

### 6.2 Zone Input Validation

- **Circle:** `center_lat`, `center_lng`, `radius_km` all required; `boundary` must be absent or null.
- **Polygon:** `boundary` required, minimum 3 vertices; `center_lat`, `center_lng`, `radius_km` must be absent or null.
- `name`: required, non-blank, max 128 chars.
- `type`: must be `CIRCLE` or `POLYGON`.

### 6.3 Global Gate Toggle Endpoints

Two endpoints on the same `AdminZoneController`:

- `GET /api/admin/zone/gate` — returns `{ gateEnabled: bool, updatedAt: timestamp, updatedBy: userId | null }`. Used by the admin UI to show current gate state on load.
- `PUT /api/admin/zone/gate` — body: `{ enabled: bool }`. Updates `system_config` via `SystemConfigService.setZoneGateEnabled()`, which writes to DB and immediately invalidates the local cache. Returns the same shape as GET. Requires ADMIN role (already covered).

**Audit trail:** every toggle is recorded via `updated_by` (the calling admin's user ID) and `updated_at` on the `system_config` row. No separate audit table needed at this scale.

**Operational usage for Store review:**
1. Admin opens admin portal → Zone Gate screen → toggles gate OFF.
2. Change is effective within 60 seconds across all instances.
3. Submit app for review. Reviewer logs in from any location, passes through gate.
4. After approval, admin toggles gate back ON.

### 6.4 Waitlist Re-engagement on Zone Activate

When `PUT /api/admin/zone/{id}` results in a zone becoming active:

1. Load all rows from `zone_waitlist`.
2. Run `ZoneCheckService` against the newly active/updated zone for each row.
3. Publish `ZONE_NOW_AVAILABLE` event to Kafka for each user now in-zone.
4. Notification-worker delivers via FCM.

`ZONE_NOW_AVAILABLE` must be added to `NotificationEventType` in both the main app and the notification-worker.

Batch scan runs synchronously at Phase 2. If waitlist grows large, move to an async job in Phase 3.

---

## 7. Phase 3 — Admin UI

Built inside the existing `oolshik_web` admin app (`oolshik-admin`, Expo). No new tech surface.

### 7.1 Zone Management Screen

- Zone list: active/inactive status, toggle, edit, deactivate.
- Create zone: circle (center pin + radius slider on map) or polygon (vertex drawing tool on map).
- Waitlist viewer: list of waitlisted users with approximate location pins on a map.

### 7.2 Zone Gate Control Screen

A dedicated screen (or section within the zone management screen) showing the current gate state and a toggle:

- **Gate ON (normal):** gate is active. Users outside zones see "Coming Soon."
- **Gate OFF (review mode):** gate is globally disabled. All authenticated users reach full app regardless of location.

The screen must display:
- Current state (ON / OFF).
- Who last changed it and when (`updated_by`, `updated_at` from `system_config`).
- A high-visibility warning when gate is OFF: "Zone gate is disabled. All users bypass location check. Re-enable after Store review is complete."

The toggle calls `PUT /api/admin/zone/gate`. The screen polls `GET /api/admin/zone/gate` or refreshes on navigation to show live state.

---

## 8. Phase 4 — Mobile App (Oolshik)

The backend contract ends at `POST /api/zone/check`. This phase wires the mobile app to that contract.

### 8.1 API Layer — `app/api/client.ts`

Add type and two methods:

```typescript
export type ZoneCheckResponse = {
  eligible: boolean
  zoneName: string | null
}
```

```typescript
checkZone: (lat: number, lng: number) =>
  api.post<ZoneCheckResponse>("/zone/check", { lat, lng }),
joinWaitlist: (lat: number, lng: number) =>
  api.post<{ queued: boolean }>("/zone/waitlist", { lat, lng }),
```

### 8.2 i18n — `app/i18n/en.ts` and `app/i18n/mr.ts`

Add a `zone` block inside the `oolshik` namespace (sibling of `consent`):

```typescript
zone: {
  title: "Oolshik isn't in your area yet",
  body: "We're expanding soon. Join the waitlist and we'll notify you when Oolshik reaches your location.",
  checkAgain: "Check again",
  joinWaitlist: "Join waitlist",
  waitlistJoined: "You're on the list! We'll notify you when we're available near you.",
  nowAvailable: "Great news — Oolshik is now available in your area!",
  continuing: "Taking you in…",
},
```

### 8.3 Zone Gate Screen — `app/screens/ZoneGateScreen.tsx`

New screen mounted inside `OolshikNavigator`. Reached by navigating from `OnboardingConsentScreen` when zone check returns `eligible: false`.

State:
- `joined: boolean` — waitlist join confirmation
- `checking: boolean` — in-flight zone re-check

Behaviour:
- **"Check Again"** — acquires location via `useForegroundLocation`, calls `checkZone`. If now eligible: calls `OolshikApi.setOnboardingPhase("INTENT_SET")`, sets `onboarding.v1.completed = "true"` in MMKV, `navigation.replace("OolshikHome")`. If still not eligible: shows "not yet" state again.
- **"Join Waitlist"** — calls `joinWaitlist` with current coords. Shows `waitlistJoined` confirmation text; button disabled after join.

### 8.4 Navigator — `app/navigators/OolshikNavigator.tsx`

Add `OolshikZoneGate: undefined` to `OolshikParamList`.

Add screen to the stack:
```tsx
<Stack.Screen name="OolshikZoneGate" component={ZoneGateScreen} />
```

### 8.5 Onboarding Consent Screen — `app/screens/OnboardingConsentScreen.tsx`

Change the "Continue" button handler. Current behaviour: set `onboarding.v1.completed = "true"` → navigate to Home.

New behaviour:
1. Destructure `coords` from `useForegroundLocation` (already imported via hook).
2. Call `OolshikApi.checkZone(coords.latitude, coords.longitude)`.
3. If `eligible: true` → call `OolshikApi.setOnboardingPhase("INTENT_SET")` → set `onboarding.v1.completed = "true"` → `navigation.replace("OolshikHome")`.
4. If `eligible: false` → `navigation.navigate("OolshikZoneGate")`. Do NOT set `onboarding.v1.completed` — user returns to onboarding on next open until they pass the gate.

Error handling: if zone check call fails (network error), fall through to Home so a network blip does not permanently block the user. Log the failure.

### 8.6 Validation Steps

1. With gate ON and an active zone: in-zone coordinates on continue → onboarding completes → Home reached.
2. With gate ON and an active zone: out-of-zone coordinates on continue → Zone Gate screen shown. `onboarding.v1.completed` NOT set. Back button returns to consent screen.
3. On Zone Gate screen: "Check Again" with now in-zone coordinates → phase advances → Home reached.
4. On Zone Gate screen: "Join Waitlist" → button shows joined confirmation, disabled after one tap.
5. With gate OFF: any coordinates on continue → onboarding completes → Home reached (zone check returns `eligible: true` from backend).
6. Network error during zone check → user proceeds to Home (fail-open).

---

## 9. "Coming Soon" Screen — Backend Responsibility Boundary

The backend's responsibility ends at the `POST /api/zone/check` response. The "Coming Soon" screen, "Check Again" button, waitlist submission, and all session-level UI gating are frontend concerns. The backend never returns a redirect or special HTTP status to trigger the screen.

Phase 2 map visualization (user location relative to zone boundary) is frontend-only. The backend already exposes everything needed: zone geometry via the admin API, eligibility status via the zone check response.

---

## 9. Enforcement Summary

| Condition | `POST /api/zone/check` result | `PUT /api/auth/me` (FRESH → INTENT_SET) |
|---|---|---|
| Gate OFF (admin disabled) | `{ eligible: true, zoneName: null }` — no geometry check | Allowed regardless of `zoneConfirmed` |
| Gate ON, no active zones, dev bypass ON | `{ eligible: true, zoneName: null }` — no geometry check | Allowed if `zoneConfirmed` is true (was set by a prior real check) |
| Gate ON, user in-zone | `{ eligible: true, zoneName: <name> }` | Allowed |
| Gate ON, user out-of-zone | `{ eligible: false, zoneName: null }` | HTTP 403 `{ error: "zone_not_confirmed" }` |
| Gate ON, no active zones, dev bypass OFF | `{ eligible: false }` | HTTP 403 |
| GRADUATED, moved out of zone, gate ON | `{ eligible: false }` — session blocked by frontend | No backend block on action endpoints |
| GRADUATED, zone deactivated, gate ON | `{ eligible: false }` | Same — frontend-only for current session |

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
2. `system_config` seeded with `zone.gate.enabled = true`. Confirm row exists.
3. With gate ON and no active zones and `bypass-when-no-zones-active: true` → `POST /api/zone/check` returns `{ eligible: true, zoneName: null }`. User zone cache NOT updated.
4. Insert circle zone (`active = false`) → check returns `eligible: false`. Zone cache updated: `zoneConfirmed: false`.
5. Set circle zone `active = true` → check with in-radius coordinates → `{ eligible: true, zoneName: <name> }`, `zoneConfirmed: true`. Out-of-radius coordinates → `eligible: false`, `zoneConfirmed: false`.
6. Insert polygon zone (`active = true`) → repeat in/out checks.
7. With `zoneConfirmed = false`: `PUT /api/auth/me { onboardingPhase: INTENT_SET }` → HTTP 403, `{ error: "zone_not_confirmed" }`. Phase stays FRESH.
8. Zone check with in-zone coordinates → `zoneConfirmed = true` → retry `PUT /api/auth/me` → phase advances to INTENT_SET.
9. `POST /api/zone/waitlist` → `{ queued: true }`. Repeat → no duplicate row.
10. Exceed rate limit (>10/min) on `POST /api/zone/check` → HTTP 429.
11. Run existing auth tests: `./mvnw -q -Dtest=AuthControllerOtpWebMvcTest,GoogleAuthServiceTest test`.

### Phase 2 — Zone CRUD

12. `POST /api/admin/zone` with circle missing `center_lat` → HTTP 400.
13. `POST /api/admin/zone` with polygon fewer than 3 vertices → HTTP 400.
14. `DELETE /api/admin/zone/{id}` → row exists in DB, `active = false`. No FK errors on `app_user.confirmed_zone_id`.
15. `PUT /api/admin/zone/{id}` activating a zone with waitlisted users inside → `ZONE_NOW_AVAILABLE` events on Kafka → FCM delivery.
16. All admin endpoints called as non-ADMIN user → HTTP 403.

### Phase 2 — Gate Toggle

17. `GET /api/admin/zone/gate` → `{ gateEnabled: true, updatedAt: ..., updatedBy: null }` (initial seeded state).
18. `PUT /api/admin/zone/gate { enabled: false }` → `{ gateEnabled: false, updatedAt: ..., updatedBy: <adminUserId> }`. `system_config` row updated.
19. With gate OFF: `POST /api/zone/check` from any coordinates → `{ eligible: true, zoneName: null }`. User zone cache NOT updated.
20. With gate OFF: `PUT /api/auth/me { onboardingPhase: INTENT_SET }` for a FRESH user with `zoneConfirmed = false` → phase advances to INTENT_SET (gate bypass active).
21. `PUT /api/admin/zone/gate { enabled: true }` → gate re-enabled. Subsequent zone check resumes geometry evaluation.
22. `GET /api/admin/zone/gate` as non-ADMIN → HTTP 403.
23. Cache TTL: toggle gate OFF, wait 61 seconds, confirm non-admin zone check reflects disabled state (gate cache expired and reloaded).
