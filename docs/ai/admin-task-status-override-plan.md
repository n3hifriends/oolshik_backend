# Admin Task Status Override — Implementation Plan (Revised)

## Overview

Add a `PATCH /api/admin/requests/{id}/status` endpoint that allows a web admin to force a `HelpRequest` task to a target status, bypassing the normal actor-driven state machine. The endpoint is admin-only, requires a mandatory note for audit purposes, and records an immutable event row.

---

## Review Corrections Applied

| Original plan issue | Correction |
|---|---|
| Referenced `HelpRequestEventService` (does not exist) | Use `HelpRequestEventRepository` directly — follows `saveReportAction` pattern |
| `completedAt` field existence was uncertain | Confirmed at `HelpRequestEntity:149` — field exists |
| No field-effect matrix for target statuses | Status invariant table added below |
| Scheduler fields not cleared for terminal overrides | Added to per-status logic |
| `HelpRequestRepository` had no locking `findById` | Add `findByIdForUpdate` to repository |
| `HelpRequestCompletionMode` has no `ADMIN_OVERRIDE` | Must add the value to the enum |
| v1 allowed any-to-any without structural guards | v1 restricted to 4 safe target statuses |

---

## Locked Decisions

| # | Decision | Choice |
|---|---|---|
| A | **Transition policy** | v1 allows only `OPEN`, `REVIEW_REQUIRED`, `COMPLETED`, `CANCELLED` as targets. All others return 400. |
| B | **Timestamp integrity** | Apply per-status field-effect table (see below). Scheduler expiry fields are cleared for terminal targets. |
| C | **Notifications** | Fire `ADMIN_STATUS_OVERRIDE` event type — notification worker controls message content. |
| D | **Admin note storage** | New `admin_override_reason TEXT` column for latest display. Immutable history goes into `help_request_event` row (audit trail already there). |
| E | **Concurrency** | Add `findByIdForUpdate` (pessimistic write lock) to `HelpRequestRepository` — same pattern as `UserRepository`. |

---

## Available Statuses and v1 Support

| Status | v1 target | Reason if blocked |
|---|---|---|
| `DRAFT` | ❌ blocked | No structural need; drafts are user-owned |
| `OPEN` | ✅ allowed | Safe: clears all helper/pending/expiry fields |
| `PENDING_AUTH` | ❌ blocked | Requires valid `pendingHelperId` + auth expiry — structurally risky |
| `ASSIGNED` | ❌ blocked | Requires valid `helperId` without an accept flow |
| `WORK_DONE_PENDING_CONFIRMATION` | ❌ blocked | Requires `helperId` + `workDoneAt` context |
| `REVIEW_REQUIRED` | ✅ allowed | Safe: sets `issueReportedAt` if null |
| `COMPLETED` | ✅ allowed | Sets `completedAt`, `completionMode`, clears scheduler fields |
| `CANCELLED` | ✅ allowed | Sets `cancelledAt`, `cancelledBy`, clears scheduler/helper fields |

---

## Status Invariant Table (Decision B)

For each allowed target status, the service must apply exactly this field-effect:

### → `OPEN`
| Field | Action |
|---|---|
| `helperId` | `null` |
| `pendingHelperId` | `null` |
| `pendingAuthExpiresAt` | `null` |
| `assignmentExpiresAt` | `null` |
| `workDoneAt` | `null` |
| `completionConfirmationExpiresAt` | `null` |
| `nextEscalationAt` | `null` |
| `cancelledAt`, `cancelledBy`, `cancelReasonCode`, `cancelReasonText` | `null` |
| `completedAt`, `completionMode`, `completedBy` | `null` |

### → `REVIEW_REQUIRED`
| Field | Action |
|---|---|
| `issueReportedAt` | Set to `now()` if currently `null` |

### → `COMPLETED`
| Field | Action |
|---|---|
| `completedAt` | Set to `now()` if currently `null` |
| `completionMode` | `ADMIN_OVERRIDE` |
| `completedBy` | `adminUserId` |
| `completionConfirmationExpiresAt` | `null` |
| `nextEscalationAt` | `null` |
| `assignmentExpiresAt` | `null` |

### → `CANCELLED`
| Field | Action |
|---|---|
| `cancelledAt` | Set to `now()` if currently `null` |
| `cancelledBy` | `adminUserId` |
| `cancelReasonText` | Admin note (truncated to 512 chars) |
| `cancelReasonCode` | `"ADMIN_OVERRIDE"` |
| `helperId` | `null` |
| `pendingHelperId` | `null` |
| `pendingAuthExpiresAt` | `null` |
| `assignmentExpiresAt` | `null` |
| `completionConfirmationExpiresAt` | `null` |
| `nextEscalationAt` | `null` |

**Payment warning (all terminal targets):** If the task had an active payment, the service does NOT touch payment state in v1. Log a warning: `ADMIN_AUDIT: payment state not updated for override id={} status={}` — manual payment review required.

---

## Execution Order

```
Step 1 — Schema migration (new column)
  → Step 2 — Enum additions
    → Step 3 — Repository locking method
      → Step 4 — DTO
        → Step 5 — AdminService method
          → Step 6 — AdminController endpoint
            → Step 7 — Tests
```

---

## Step 1 — Schema Migration

**Create:** `src/main/resources/db/migration/V41__admin_help_request_override.sql`

```sql
ALTER TABLE help_request
    ADD COLUMN IF NOT EXISTS admin_override_reason TEXT;
```

No index needed — only read via primary key lookups.

---

## Step 2 — Enum Additions

**Modify:** `src/main/java/com/oolshik/backend/domain/HelpRequestEventType.java`
- Add `ADMIN_STATUS_OVERRIDE`

**Modify:** `src/main/java/com/oolshik/backend/notification/NotificationEventType.java`
- Add `ADMIN_STATUS_OVERRIDE`

**Modify:** `src/main/java/com/oolshik/backend/domain/HelpRequestCompletionMode.java`
- Add `ADMIN_OVERRIDE` (needed for `COMPLETED` override path)

---

## Step 3 — Repository Locking Method

**Modify:** `src/main/java/com/oolshik/backend/repo/HelpRequestRepository.java`

Add a pessimistic write lock query, mirroring `UserRepository.findByIdForUpdate`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select r from HelpRequestEntity r where r.id = :id")
Optional<HelpRequestEntity> findByIdForUpdate(@Param("id") UUID id);
```

Required imports: `jakarta.persistence.LockModeType`, `org.springframework.data.jpa.repository.Lock`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`.

---

## Step 4 — DTO

**Modify:** `src/main/java/com/oolshik/backend/admin/AdminDtos.java`

Add one new record:
```java
record UpdateHelpRequestStatusRequest(String status, String note) {}
```

Response: reuse existing `AdminRequestDetail` — no new response DTO needed.

---

## Step 5 — AdminService Method

**Modify:** `src/main/java/com/oolshik/backend/admin/AdminService.java`

Add two new `final` fields (Lombok `@RequiredArgsConstructor` handles the constructor):
```java
private final HelpRequestEventRepository helpRequestEventRepository;
// NotificationService only if notification enqueue is added here — defer to notification worker via event row
```

Add private helper (follows `saveReportAction` pattern exactly):
```java
private void saveHelpRequestEvent(UUID requestId, UUID adminUserId, HelpRequestEventType type, String reasonText) {
    HelpRequestEventEntity event = new HelpRequestEventEntity();
    event.setRequestId(requestId);
    event.setEventType(type);
    event.setActorUserId(adminUserId);
    event.setActorRole(HelpRequestActorRole.SYSTEM);
    event.setReasonText(reasonText);
    helpRequestEventRepository.save(event);
}
```

Add `@Transactional` method:
```java
public AdminRequestDetail adminUpdateHelpRequestStatus(
        UUID requestId, String rawStatus, String note, UUID adminUserId)
```

Internal sequence:
1. Validate `rawStatus` is one of the 4 allowed targets — throw 400 if blank, invalid, or unsupported
2. Validate `note` non-blank — throw 400 "Admin note is required"
3. `findByIdForUpdate(requestId)` → throw 404 if absent
4. Capture `fromStatus` for logging
5. If `newStatus == fromStatus` → throw 400 "Status is already X"
6. Apply per-status field-effect table (see Step 5a)
7. `entity.setStatus(newStatus)`
8. `entity.setLastStateChangeAt(now)`
9. `entity.setLastStateChangeReason("ADMIN_OVERRIDE")`
10. `entity.setAdminOverrideReason(cleanNote(note))`
11. `helpRequestRepository.save(entity)`
12. `saveHelpRequestEvent(requestId, adminUserId, ADMIN_STATUS_OVERRIDE, note)`
13. Log: `log.info("ADMIN_AUDIT: help_request status overridden id={} from={} to={} by={}", ...)`
14. Return `getRequest(requestId).orElseThrow()`

### Step 5a — Field-effect logic block

```java
OffsetDateTime now = OffsetDateTime.now();
switch (newStatus) {
    case OPEN -> {
        entity.setHelperId(null);
        entity.setPendingHelperId(null);
        entity.setPendingAuthExpiresAt(null);
        entity.setAssignmentExpiresAt(null);
        entity.setWorkDoneAt(null);
        entity.setCompletionConfirmationExpiresAt(null);
        entity.setNextEscalationAt(null);
        entity.setCancelledAt(null);
        entity.setCancelledBy(null);
        entity.setCancelReasonCode(null);
        entity.setCancelReasonText(null);
        entity.setCompletedAt(null);
        entity.setCompletionMode(null);
        entity.setCompletedBy(null);
    }
    case REVIEW_REQUIRED -> {
        if (entity.getIssueReportedAt() == null) entity.setIssueReportedAt(now);
    }
    case COMPLETED -> {
        if (entity.getCompletedAt() == null) entity.setCompletedAt(now);
        entity.setCompletionMode(HelpRequestCompletionMode.ADMIN_OVERRIDE);
        entity.setCompletedBy(adminUserId);
        entity.setCompletionConfirmationExpiresAt(null);
        entity.setNextEscalationAt(null);
        entity.setAssignmentExpiresAt(null);
    }
    case CANCELLED -> {
        if (entity.getCancelledAt() == null) entity.setCancelledAt(now);
        entity.setCancelledBy(adminUserId);
        entity.setCancelReasonCode("ADMIN_OVERRIDE");
        entity.setCancelReasonText(cleanNote(note));
        entity.setHelperId(null);
        entity.setPendingHelperId(null);
        entity.setPendingAuthExpiresAt(null);
        entity.setAssignmentExpiresAt(null);
        entity.setCompletionConfirmationExpiresAt(null);
        entity.setNextEscalationAt(null);
    }
    default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Status override not supported in v1: " + newStatus);
}
```

### Step 5b — HelpRequestEntity field

**Modify:** `src/main/java/com/oolshik/backend/entity/HelpRequestEntity.java`
- Add field: `@Column(name = "admin_override_reason", columnDefinition = "TEXT") private String adminOverrideReason;`
- Add getter and setter

---

## Step 6 — AdminController Endpoint

**Modify:** `src/main/java/com/oolshik/backend/admin/AdminController.java`

Add near the existing `getRequest` method:

```java
@PatchMapping("/requests/{id}/status")
public AdminRequestDetail updateRequestStatus(
        @PathVariable UUID id,
        @RequestBody UpdateHelpRequestStatusRequest request,
        @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
    UUID adminId = requireAdmin(principal);
    return adminService.adminUpdateHelpRequestStatus(id, request.status(), request.note(), adminId);
}
```

No security config changes — `/api/admin/**` is already `hasRole("ADMIN")`.

---

## Step 7 — Tests

### New test class

`src/test/java/com/oolshik/backend/admin/AdminServiceHelpRequestStatusOverrideTest.java`

Follow the structure of `AdminServiceBlockUserTest` exactly.

| Test case | Expected outcome |
|---|---|
| `OPEN → COMPLETED` | Status changes, `completedAt` set, `completionMode = ADMIN_OVERRIDE`, event saved, `AdminRequestDetail` returned |
| `ASSIGNED → CANCELLED` | `cancelledAt`, `cancelledBy`, `cancelReasonCode = "ADMIN_OVERRIDE"` set; helper fields cleared |
| `CANCELLED → OPEN` | Status changes, all helper/expiry/cancel fields cleared |
| `OPEN → REVIEW_REQUIRED` | `issueReportedAt` set; event saved |
| `OPEN → ASSIGNED` | 400 "Status override not supported in v1" |
| Entity not found | 404 `ResponseStatusException` |
| Same status as current | 400 "Status is already X" |
| Blank note | 400 before repo access |
| Blank/invalid status string | 400 before repo access |

### Existing test to update

`src/test/java/com/oolshik/backend/admin/AdminServiceBlockUserTest.java` (if it uses a manual constructor call for `AdminService`)

Add `@Mock HelpRequestEventRepository helpRequestEventRepository` and include it in the service constructor call in `@BeforeEach`.

### Optional WebMVC test

`src/test/java/com/oolshik/backend/web/AdminRequestStatusControllerWebMvcTest.java`

Verify HTTP layer: 200 on success, 400 on missing/invalid status, 401 unauthenticated, 403 non-admin. Follow `AuthSecurityWebMvcTest` pattern.

---

## Files Changed Summary

| File | Action | Notes |
|---|---|---|
| `db/migration/V41__admin_help_request_override.sql` | Create | New column |
| `domain/HelpRequestEventType.java` | Add `ADMIN_STATUS_OVERRIDE` | Enum value |
| `notification/NotificationEventType.java` | Add `ADMIN_STATUS_OVERRIDE` | Enum value |
| `domain/HelpRequestCompletionMode.java` | Add `ADMIN_OVERRIDE` | Required for COMPLETED path |
| `repo/HelpRequestRepository.java` | Add `findByIdForUpdate` | Pessimistic lock |
| `admin/AdminDtos.java` | Add `UpdateHelpRequestStatusRequest` | Request record |
| `entity/HelpRequestEntity.java` | Add `adminOverrideReason` field + getter/setter | Latest-display audit |
| `admin/AdminService.java` | Add method + `HelpRequestEventRepository` dependency + private helper | Core logic |
| `admin/AdminController.java` | Add endpoint | HTTP layer |
| `AdminServiceBlockUserTest.java` | Add `@Mock` for `HelpRequestEventRepository` | Constructor fix |
| `AdminServiceHelpRequestStatusOverrideTest.java` | Create | New test class |

---

## Risk Notes

| Risk | Severity | Mitigation |
|---|---|---|
| Bypasses all business workflow guards | Medium | Mandatory note; `ADMIN_AUDIT:` log; immutable event row |
| Payment state not synchronized for terminal overrides | Medium | Explicit warning log; manual payment review required; noted in v2 scope |
| v1 restriction may block legitimate admin use for `ASSIGNED`/`PENDING_AUTH` | Low | v2 can add those targets with helper-ID payload requirement |
| No automatic rollback on bad override | Low | Audit log + manual re-override always possible |
| Concurrency race with helper/requester actions | Low | `findByIdForUpdate` (pessimistic write lock) prevents this |

---

## v2 Scope (deferred)

- Allow `ASSIGNED` as target with required `helperId` in request body
- Allow `PENDING_AUTH` as target with required `pendingHelperId` and computed expiry
- Allow `WORK_DONE_PENDING_CONFIRMATION` as target with `helperId` guard
- Add payment-state coordination for forced `COMPLETED`/`CANCELLED` on tasks with open payments
