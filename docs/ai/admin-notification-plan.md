# Admin Notification Plan

**Date:** 2026-06-14  
**Status:** Revised after review — blockers resolved, ready to implement

---

## Resolved Blockers (from review)

| # | Issue | Resolution |
|---|-------|-----------|
| P1 | `@EnableAsync` absent — `OolshikApplication` only has `@EnableScheduling` | **Drop `@Async`.** Use `@Scheduled` polling of `QUEUED` broadcasts from DB — same pattern as `NotificationOutboxPublisher`. Resilient to pod restarts. |
| P1 | `FirebaseAdminConfig` is `@Conditional(FirebaseIdentityCondition.class)`; prod uses `identity-provider: local` → Firebase SDK **not initialized** | **Drop Firebase FCM.** All push tokens are Expo format (`ExponentPushToken[...]`); use **Expo Push API** (`https://exp.host/--/api/v2/push/send`) via HTTP, no Firebase SDK needed. |
| P1 | `UserDeviceService` validates `ExponentPushToken|ExpoPushToken` only — tokens are Expo, not raw FCM/APNs | **Confirmed: Expo Push API is the correct push transport.** No device registration changes needed. |
| P2 | `Msg91OtpProvider.sendOtp()` is template-bound (requires `template_id`, `entity_id`) — not usable for free-text admin SMS | **SMS needs new implementation.** Add `AdminSmsSender` backed by Msg91's transactional SMS endpoint (separate from OTP flow) with dedicated config keys. |
| P2 | Renaming `GET /api/admin/notifications` to `/outbox` breaks the existing admin screen without a frontend update | **Keep existing route.** Add new broadcast routes alongside. No rename until the frontend update is done in the same change. |

---

## Context

- Push tokens already stored in `user_device` (provider=`EXPO`, token=`ExponentPushToken[...]`, is_active)
- Existing `notification_outbox` is for system event automation — admin broadcasts are separate
- `/api/admin/**` is already guarded by `hasRole("ADMIN")` in `SecurityConfig`
- Scheduling infra (`@EnableScheduling`) already present; no new annotations needed on `OolshikApplication`
- `UserDeviceRepository` currently has `findByUserIdAndIsActiveTrue(UUID)` — batch query for broadcast needs to be added
- Current highest Flyway migration: `V27`

---

## Delivery Channels

Admin can choose one or more per send:

- **PUSH** — Expo Push API (`https://exp.host/--/api/v2/push/send`), batched up to 100 tokens per request
- **SMS** — Msg91 transactional SMS (new config, separate from OTP flow)
- **IN_APP** — rows in `user_notification` table, fetched by the mobile app

---

## Targeting Options

| targetType | targetValue | Resolves to |
|------------|-------------|-------------|
| `ALL` | — | All active users |
| `ROLE` | role name (e.g. `NETA`) | All users with that role |
| `USER` | user UUID | Single user |
| `REQUEST` | help request UUID | Requester + assigned helper |

---

## 1. Data Model — 4 New DB Migrations

### V28: `admin_notification_template`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `name` | VARCHAR(100) | Unique, not null |
| `title` | VARCHAR(100) | Not null |
| `body` | TEXT | Not null |
| `created_by` | UUID FK → `app_user` | ON DELETE SET NULL |
| `created_at` | TIMESTAMPTZ | Default now() |
| `updated_at` | TIMESTAMPTZ | Default now() |

Index: `UNIQUE (name)`

### V29: `admin_broadcast`

One record per admin send action; also serves as the scheduler work queue.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `template_id` | UUID nullable FK → `admin_notification_template` | ON DELETE SET NULL |
| `title` | VARCHAR(100) | Not null |
| `body` | TEXT | Not null |
| `target_type` | VARCHAR(20) | `ALL`, `ROLE`, `USER`, `REQUEST` — not null |
| `target_value` | VARCHAR(100) | Null for ALL, role name or UUID otherwise |
| `channels` | VARCHAR(50) | Comma-separated: `PUSH`, `SMS`, `IN_APP` — not null |
| `status` | VARCHAR(20) | `QUEUED`, `PROCESSING`, `COMPLETED`, `PARTIAL_FAILURE` — not null |
| `total_recipients` | INT | Default 0 |
| `push_sent` | INT | Default 0 |
| `push_failed` | INT | Default 0 |
| `sms_sent` | INT | Default 0 |
| `sms_failed` | INT | Default 0 |
| `in_app_created` | INT | Default 0 |
| `created_by` | UUID FK → `app_user` | ON DELETE SET NULL |
| `created_at` | TIMESTAMPTZ | Default now() |
| `processing_started_at` | TIMESTAMPTZ nullable | Set when scheduler picks it up; used to detect stale `PROCESSING` records after restart |
| `completed_at` | TIMESTAMPTZ nullable | |

Index: `(status, created_at)` for scheduler polling

### V30: `admin_broadcast_delivery`

Per-user, per-channel delivery record (written in batch).

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `broadcast_id` | UUID FK → `admin_broadcast` | ON DELETE CASCADE |
| `user_id` | UUID FK → `app_user` | ON DELETE CASCADE |
| `channel` | VARCHAR(10) | `PUSH`, `SMS`, `IN_APP` — not null |
| `status` | VARCHAR(10) | `SENT`, `FAILED`, `SKIPPED` — not null |
| `error` | TEXT nullable | |
| `sent_at` | TIMESTAMPTZ | Default now() |

Index: `(broadcast_id)`, `(broadcast_id, channel, status)` for stats queries  
Constraint: `UNIQUE (broadcast_id, user_id, channel)` — idempotency

### V31: `user_notification`

In-app inbox entry per user.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `user_id` | UUID FK → `app_user` | ON DELETE CASCADE, not null |
| `broadcast_id` | UUID nullable FK → `admin_broadcast` | ON DELETE SET NULL |
| `title` | VARCHAR(100) | Not null |
| `body` | TEXT | Not null |
| `read_at` | TIMESTAMPTZ nullable | Null = unread |
| `created_at` | TIMESTAMPTZ | Default now() |

Index: `(user_id, created_at DESC)`, `(user_id) WHERE read_at IS NULL` (partial, for unread count)

---

## 2. New API Endpoints

### Admin APIs — `/api/admin/notifications`

> `GET /api/admin/notifications` (existing system outbox view) is **kept as-is**. New routes are added alongside.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/admin/notifications/send` | Queue a broadcast (returns immediately) |
| `GET` | `/api/admin/notifications/broadcasts` | List past broadcasts (paginated) |
| `GET` | `/api/admin/notifications/broadcasts/{id}` | Broadcast detail with delivery counts |
| `GET` | `/api/admin/notifications/broadcasts/{id}/deliveries` | Per-user delivery rows (paginated) |
| `POST` | `/api/admin/notifications/templates` | Create a template |
| `GET` | `/api/admin/notifications/templates` | List templates |
| `DELETE` | `/api/admin/notifications/templates/{id}` | Delete a template |

**`POST /api/admin/notifications/send` — request body:**

```json
{
  "targetType": "ALL | ROLE | USER | REQUEST",
  "targetValue": "<role-name or UUID — omit for ALL>",
  "channels": ["PUSH", "SMS", "IN_APP"],
  "title": "string (max 100 chars)",
  "body": "string (max 1000 chars)",
  "templateId": "<UUID or null>",
  "saveAsTemplate": false,
  "templateName": "<required if saveAsTemplate is true>"
}
```

**Response:**

```json
{
  "broadcastId": "<UUID>",
  "status": "QUEUED",
  "estimatedRecipients": 142
}
```

Returns immediately after writing `QUEUED` record. Scheduler processes it asynchronously.

**Safeguards on this endpoint:**
- `title` max 100 chars, `body` max 1000 chars (validated with `@Valid`)
- `estimatedRecipients` is returned before queuing — caller sees the reach before it goes out
- Max `totalRecipients` cap: configurable (`app.admin.notification.maxRecipients`, default 50,000)
- SMS channel requires SMS config to be enabled; endpoint returns 400 if SMS is requested but not configured
- Admin identity (`createdBy`) taken from JWT principal, stored on the record for audit

---

### User-facing APIs — `/api/notifications`

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/notifications` | Paginated in-app inbox for authenticated user |
| `GET` | `/api/notifications/unread-count` | Integer badge count |
| `PATCH` | `/api/notifications/{id}/read` | Mark one notification as read |
| `PATCH` | `/api/notifications/read-all` | Mark all as read |

---

## 3. Delivery Architecture — `@Scheduled` Polling

Replaces `@Async`. Pattern mirrors the existing `NotificationOutboxPublisher`.

### `AdminBroadcastScheduler` (new)

```
@Scheduled(fixedDelayString = "${app.admin.notification.schedulerIntervalMs:5000}")
processNextQueued():
  1. Atomically claim one QUEUED broadcast:
       UPDATE admin_broadcast SET status='PROCESSING', processing_started_at=now()
       WHERE id = (
         SELECT id FROM admin_broadcast WHERE status='QUEUED'
         ORDER BY created_at LIMIT 1
         FOR UPDATE SKIP LOCKED
       )
  2. Resolve recipient user IDs by targetType
  3. Update total_recipients
  4. For each user batch (size: app.admin.notification.batchSize, default 100):
       - IN_APP: bulk-insert user_notification rows
       - PUSH: collect active Expo tokens → call AdminExpoPushSender
       - SMS: collect phone numbers → call AdminSmsSender
       - Batch-insert admin_broadcast_delivery rows
  5. Set status = COMPLETED or PARTIAL_FAILURE with final counts

Also on startup / each scheduler tick:
  Reset stale PROCESSING records (processing_started_at < now() - staleTimeoutMinutes)
  back to QUEUED so they are retried.
```

This is naturally single-threaded per scheduler tick. Pod restart safety: a stale `PROCESSING` broadcast is reset to `QUEUED` on next scheduler run.

---

## 4. Service Layer

### `AdminNotificationService` (new)

Validates the `SendBroadcastRequest`, estimates recipients, writes the `admin_broadcast` row with status `QUEUED`, returns `broadcastId` immediately.

- Title/body length validated
- `targetValue` required for non-ALL targets (returns 400 otherwise)
- If `saveAsTemplate`: checks for name uniqueness (409 on conflict), persists template
- Records `createdBy` from `AuthenticatedUserPrincipal`

### `AdminBroadcastScheduler` (new)

See section 3 above.

### `AdminExpoPushSender` (new)

Replaces the original Firebase FCM plan. Uses `RestTemplate` (already wired in the app via `RestTemplateBuilder`).

- Sends to `https://exp.host/--/api/v2/push/send`
- Accepts array of message objects, up to 100 per request (Expo batch limit)
- Each message: `{ to: <expoToken>, title: <title>, body: <body> }`
- Parses response per-token: `{ status: "ok" | "error", details: { error: "..." } }`
- On `DeviceNotRegistered` → sets `UserDeviceEntity.isActive = false` (token cleanup)
- Returns `Map<token, SendResult>` for delivery row creation
- No Firebase SDK, no conditional bean — plain HTTP

Optional: Expo access token (`EXPO_ACCESS_TOKEN`) for higher rate limits. Can be added as a config property.

### `AdminSmsSender` (new interface + `Msg91AdminSmsSender` implementation)

`Msg91OtpProvider` is template-bound (`template_id` + `entity_id`) and cannot send free-text. A separate implementation is needed.

- New interface: `AdminSmsSender.send(String phoneE164, String text)`
- `Msg91AdminSmsSender`: calls Msg91's generic transactional SMS endpoint (distinct from the OTP flow endpoint)
- New config keys under `app.admin.notification.sms.*`: `enabled`, `msg91ApiKey`, `msg91SenderId`, `msg91BaseUrl`
- Bean activated only when `app.admin.notification.sms.enabled=true`; if not active and SMS channel is requested, endpoint returns 400

### `AdminNotificationTemplateService` (new)

CRUD backed by `AdminNotificationTemplateRepository`. Unique constraint on `name`; duplicate returns 409.

### `UserNotificationService` (new)

- `getInbox(userId, pageable)` → paginated `user_notification` sorted by `created_at DESC`
- `getUnreadCount(userId)` → count where `read_at IS NULL`
- `markRead(userId, notificationId)` → set `read_at`; guards that user owns the row (404 if not found or wrong user)
- `markAllRead(userId)` → bulk update

---

## 5. Files to Create

```
src/main/java/com/oolshik/backend/
  admin/
    AdminNotificationController.java
    AdminNotificationService.java
    AdminBroadcastScheduler.java
    AdminExpoPushSender.java
    AdminSmsSender.java                    (interface)
    Msg91AdminSmsSender.java               (implementation)
    AdminNotificationTemplateService.java
    AdminNotificationDtos.java

  entity/
    AdminBroadcastEntity.java
    AdminBroadcastDeliveryEntity.java
    AdminNotificationTemplateEntity.java
    UserNotificationEntity.java

  repo/
    AdminBroadcastRepository.java          (includes SKIP LOCKED native query)
    AdminBroadcastDeliveryRepository.java
    AdminNotificationTemplateRepository.java
    UserNotificationRepository.java

  web/
    UserNotificationController.java

  service/
    UserNotificationService.java

src/main/resources/db/migration/
  V28__admin_notification_template.sql
  V29__admin_broadcast.sql
  V30__admin_broadcast_delivery.sql
  V31__user_notification.sql
```

---

## 6. Files to Modify (Backend)

| File | Change |
|------|--------|
| `repo/UserDeviceRepository.java` | Add `List<UserDeviceEntity> findByUserIdInAndIsActiveTrue(Collection<UUID> userIds)` for batch token lookup during broadcast |
| `security/SecurityConfig.java` | Permit `/api/notifications/**` for authenticated users — already covered by `anyRequest().authenticated()`, verify no change needed |
| `application.yml` | Add config keys: `app.admin.notification.schedulerIntervalMs`, `app.admin.notification.batchSize`, `app.admin.notification.maxRecipients`, `app.admin.notification.staleProcessingTimeoutMinutes`, `app.admin.notification.sms.enabled`, `app.admin.notification.sms.msg91ApiKey`, `app.admin.notification.sms.msg91SenderId`, `app.admin.notification.sms.msg91BaseUrl`, `app.admin.notification.expoAccessToken` |

> `OolshikApplication.java` — **no change needed**. `@EnableScheduling` is already present; the scheduler pattern requires no new annotations.

---

## 7. Files to Modify (Frontend — `/Users/nitinkalokhe/Ni3/oolshik_web`)

These are identified but not yet in scope for the first implementation pass. Required before the feature is usable end-to-end:

| Area | What to add/change |
|------|--------------------|
| Admin compose screen | Form for title, body, targetType/targetValue, channel selection, template picker |
| Admin template management | List + create + delete templates |
| Admin broadcast list | Paginated list of past broadcasts with status and delivery counts |
| Admin broadcast detail | Per-user delivery breakdown, channel breakdown |
| Admin system outbox | Existing `GET /api/admin/notifications` route — **no route change**, stays as-is |
| Mobile app (Oolshik) | User-facing notification inbox screen consuming `/api/notifications`, unread badge count |

> The frontend and mobile changes are a **separate implementation ticket**. The backend APIs can be shipped first; the admin screen and mobile inbox follow.

---

## 8. Design Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Push transport | Expo Push API (HTTP) | All tokens are Expo format; Firebase SDK is conditional and not active in prod with `identity-provider: local` |
| Async mechanism | `@Scheduled` polling (not `@Async`) | Broadcast survives pod restart; `@EnableAsync` not present; same pattern as existing `NotificationOutboxPublisher` |
| SMS implementation | New `Msg91AdminSmsSender` | `Msg91OtpProvider` is template/OTP-bound and cannot send free-text; needs a separate Msg91 API call and config |
| Expo batch size | 100 per HTTP call | Expo's documented batch limit |
| Stale PROCESSING reset | Scheduler resets records older than configurable timeout back to QUEUED | Handles pod restart mid-broadcast without data loss |
| Invalid Expo token cleanup | Deactivate on `DeviceNotRegistered` error | Prevents repeated failed sends |
| In-app as DB rows | `user_notification` table | Read/unread tracking and history; no external dependency |
| Existing `/notifications` route | No rename | Renaming `GET /api/admin/notifications` to `/outbox` would break the existing admin screen; change together with frontend update |
| Template variables | Deferred | Free-form compose is sufficient for MVP; `{{name}}` substitution added when templates are actively used |
| Idempotency | `UNIQUE (broadcast_id, user_id, channel)` on delivery table | Prevents duplicate delivery rows on retry |

---

## 9. Safeguards

- **Title** max 100 chars, **body** max 1000 chars — enforced at API validation level
- **`estimatedRecipients`** returned before queuing so admin sees reach before sending
- **Max recipients cap**: `app.admin.notification.maxRecipients` (default 50,000) — endpoint returns 400 if estimated recipients exceed this
- **Admin audit**: `created_by` (UUID of the admin who sent) stored on `admin_broadcast`
- **SMS gate**: SMS channel requires `app.admin.notification.sms.enabled=true`; returns 400 if not configured
- **Stale broadcast recovery**: Broadcasts stuck in `PROCESSING` longer than `staleProcessingTimeoutMinutes` are reset to `QUEUED`
- **Delivery idempotency**: Unique constraint on `(broadcast_id, user_id, channel)` prevents double delivery rows on re-processing

---

## 10. Open Questions (resolved vs pending)

| Question | Status |
|----------|--------|
| Expo vs FCM push | ✅ Resolved: Expo Push API |
| `@Async` vs `@Scheduled` polling | ✅ Resolved: `@Scheduled` polling |
| Firebase availability in prod | ✅ Resolved: not available; Expo is the transport |
| SMS free-text implementation | ✅ Resolved: new `Msg91AdminSmsSender` with separate config |
| Existing `/notifications` route rename | ✅ Resolved: no rename; add new routes alongside |
| Frontend (admin compose + inbox) | ⏳ Pending: separate ticket, can follow backend |
| Expo access token for rate limits | ⏳ Optional: add `EXPO_ACCESS_TOKEN` env var when rate limits are hit in prod |
| SMS per-DLT template requirement | ⏳ Pending: Msg91 in India requires DLT-registered templates even for transactional SMS; confirm whether admin SMS messages can use a pre-registered template or need dynamic approval |
