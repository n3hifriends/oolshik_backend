# Implementation Plan: "My Requests" — Dedicated Fetch

## Problem Statement

"My requests" currently reads from the same nearby cache as "For You". The backend's `ST_DWithin` filter silently excludes the user's own tasks the moment they move outside the request's search radius. A user's own tasks must always be visible regardless of their current location.

---

## Root Cause

1. `GET /api/requests/nearby` uses `ST_DWithin` to hard-filter results to within a radius from the user's current location.
2. Both "For You" and "My Requests" tabs share this single data source.
3. "My requests" is a **client-side filter** on that shared data — `requesterId === currentUserId`.
4. If the user moves away from where they posted, the backend never returns their task, so it disappears from "My requests".

---

## Solution

Add a dedicated `GET /api/requests/mine` endpoint that returns the authenticated user's own requests with no location filter, and wire the frontend to call it independently when in "mine" view mode.

---

## Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Ownership definition | `requester_id` only | "My requests" means tasks I posted, not tasks I helped with |
| Default statuses from `/mine` | No server-side filter — return all | Let the existing client-side status chip filter handle it |
| Pagination | Omit from v1 — return most recent 50 | `fetchNearby` discards page metadata; adding a new pagination model here is scope creep for a correctness fix |
| Cache | No cache for `myTasks` — always fetch fresh | Data set is small; avoids stale data after mutations from another device |

---

## Backend

### Step 1 — `HelpRequestRepository`: new query method

**File:** `src/main/java/com/oolshik/backend/repo/HelpRequestRepository.java`

Add a new `@Query` native method `findByRequesterIdPaged` using the same `HelpRequestRow` projection as `findNearbyPaged`, with the following changes:

- **Filter clause:** `WHERE h.requester_id = :requesterId` — no `ST_DWithin`
- **`distanceMtr` column:** `null` in the `SELECT` — compatible because the projection field is nullable `Double`
- **Sort:** `ORDER BY h.created_at DESC`
- **Params:** `requesterId` (UUID), `statusesCsv` (String, nullable — empty string means no filter, following existing convention)
- **Return type:** `List<HelpRequestRow>` with a fixed `LIMIT 50` — no `Page<>` until the UI needs infinite scroll

---

### Step 2 — `HelpRequestService`: new service method

**File:** `src/main/java/com/oolshik/backend/service/HelpRequestService.java`

Add `getMyRequests(UUID requesterId, List<String> statuses)`:

- Accepts `List<String> statuses` (not CSV — consistent with how `nearby` is handled at the controller layer)
- Converts list to CSV internally before passing to the repository, keeping the CSV concern out of the controller
- Delegates to the new repository method
- No other business logic

---

### Step 3 — `HelpRequestController`: new endpoint

**File:** `src/main/java/com/oolshik/backend/web/HelpRequestController.java`

Add endpoint:

```
GET /api/requests/mine
```

- **Auth:** reads `requesterId` from `AuthenticatedUserPrincipal` — no `lat`/`lng` params
- **Query params:** `statuses` as `List<String>` (optional), consistent with `/nearby`
- **Response:** `List<HelpRequestRowView>` using the existing `view(HelpRequestRow, UUID)` mapper

---

### Step 4 — Tests

**Files:** repository and controller test classes

Add a test case that explicitly verifies the core correctness fix:

> A request created at location A by user X must appear in `/mine` when user X queries from location B, even if B is outside the request's `radius_meters`.

This guards against regressions where a future query change accidentally re-introduces a spatial filter.

---

## Frontend

### Step 5 — API client: new method

**File:** `app/api/client.ts`

Add `myTasks(statuses?: string[])`:

- Calls `GET /requests/mine` with optional status query params
- Returns `ServerTask[]` — same item shape as `nearbyTasks`, no page envelope needed in v1

---

### Step 6 — `taskStore`: populate `myTasks`

**File:** `app/store/taskStore.ts`

`myTasks: Task[]` already exists in state but is never populated. Extend the store:

**New state fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `myTasksLoading` | `boolean` | Loading indicator for "mine" tab |
| `myTasksError` | `AppApiError \| null` | Error state for "mine" tab |

No pagination fields — v1 returns a flat list.

**New actions:**

- `fetchMyTasks()` — fetches `/requests/mine`, sets `myTasksLoading`, populates `myTasks`, sets `myTasksError` on failure. No cache write. Always fetches fresh.
- `clearMyTasks()` — resets `myTasks`, `myTasksLoading`, `myTasksError` to initial values. Called on sign-out alongside `clearNearby()`.

**Mutation sync (best-effort only):**

When a local mutation (accept, cancel, complete, markDone, etc.) updates a task in `tasks`, apply the same update to the matching entry in `myTasks` if present. This is optimistic UI only — it does not replace a server refresh. The "mine" tab must re-fetch on focus, pull-to-refresh, and push notification to catch server-side changes from other devices or background jobs.

---

### Step 7 — `useHomeFeedController`: switch data source by viewMode

**File:** `app/screens/home-feed/hooks/useHomeFeedController.tsx`

**Read new fields from store:**
- Pull `myTasks`, `myTasksLoading`, `myTasksError`, `fetchMyTasks` alongside existing store fields.

**`taskItems` selection:**
- Change from always using `tasks` to: `viewMode === "mine" ? myTasks : tasks`

**`hasVisibleTasks`:**
- Derive from the active list based on `viewMode`

**Loading and error state:**
- When `viewMode === "mine"`, use `myTasksLoading` and `myTasksError` for the feed's loading and error UI

**Trigger fetch:**
- When `viewMode` changes to `"mine"`, call `fetchMyTasks()`
- On manual pull-to-refresh while in `"mine"` mode, call `fetchMyTasks()` instead of `fetchNearby`
- On app foreground resume while in `"mine"` mode, call `fetchMyTasks()`

**`availableStatuses` memo (lines 356–378):**
- Remove the `isMine`/`!isMine` split — the store already provides ownership-filtered data per viewMode

**Distance sort guard:**
- Line 415 already blocks distance sort when `viewMode === "mine"` — keep as-is

---

### Step 8 — `useTaskFiltering`: remove ownership gating

**File:** `app/hooks/useTaskFiltering.ts`

The `isMine` index field and the guard at line 160 become dead code **only after** both the store and controller (steps 6 and 7) are complete and both views always receive pre-filtered lists. Do not remove this until steps 6 and 7 are fully shipped — removing it prematurely will break the "For You" tab by showing the current user's own tasks there.

---

## Sequencing

| Step | Layer | Blocking |
|------|-------|---------|
| 1. Repo query | Backend | Blocks step 2 |
| 2. Service method | Backend | Blocks step 3 |
| 3. Controller endpoint | Backend | Blocks step 4 |
| 4. Backend tests | Backend | Independent — but ship with step 3 |
| 5. API client method | Frontend | Blocks step 6 |
| 6. Store state + fetch actions | Frontend | Blocks step 7 |
| 7. Controller hook wiring | Frontend | Blocks step 8 |
| 8. `useTaskFiltering` cleanup | Frontend | Only after step 7 is fully shipped |

Backend steps 1–4 can be developed and deployed before frontend work begins. Frontend steps 5–7 can be done together in a single PR once the endpoint is live.

---

## Edge Cases to Handle

| Scenario | Handling |
|----------|---------|
| First open of "mine" tab | `myTasks` is empty — show loader, not empty state |
| Offline / network error | Show same error card as "For You" feed using `myTasksError` |
| Task created while on "mine" tab | Prepend the newly created task to `myTasks` the same way it is prepended to `tasks` today |
| Sign-out | `clearMyTasks()` must be called alongside `clearNearby()` to prevent stale data leaking to the next session |
| Status mutations | accept / cancel / complete must sync into both `tasks` and `myTasks` as best-effort; server refresh on focus/pull is the authoritative update |
| Push notification received | Re-call `fetchMyTasks()` if currently in "mine" viewMode |
| User is both requester and helper on different tasks | `/mine` returns only requester-owned tasks; helper tasks appear only in "For You" |
