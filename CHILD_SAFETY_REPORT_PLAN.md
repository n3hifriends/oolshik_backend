# Child Safety Reporting Execution Plan

## Objective

Make Oolshik compliant with Google Play's child safety reporting expectations for a Social app by extending the existing report and moderation system with a dedicated child-safety path.

The goal is not just to add a label. The implemented flow must prove that:

- Users can report child safety concerns inside the app.
- Child safety reports are stored and visible to admins.
- Child safety reports receive priority handling.
- Admins can record review, action, and authority-reporting notes.
- Oolshik has a public child safety standards page matching the implemented behavior.

## Current State Summary

### Existing Strengths

- Mobile already has `OolshikReport`.
- Mobile report flow already submits to backend endpoint `/api/reports`.
- Backend already has `ReportController`, `ReportService`, `ReportEventEntity`, and `ReportEventRepository`.
- Admin web already has Reports list and Report detail pages.
- Admin report moderation already supports:
  - `status`
  - `priority`
  - `assignedAdmin`
  - `resolutionNote`
  - `report_action` audit trail

### Current Gaps

- No dedicated child safety reason exists.
- DB constraint allows only `SPAM`, `INAPPROPRIATE`, `UNSAFE`, `OTHER`.
- Mobile report reason union allows only current reasons.
- Admin web reason filter does not include child safety.
- App-level `SafetyFeedbackScreen` writes to generic feedback, not report moderation.
- Profile "Report issue" opens email, which should not be the primary Play child-safety mechanism.
- No public child safety standards page exists in the reviewed web app.
- No report-specific backend test currently exists under `src/test`.

## Decision Record

| Topic                               | Decision                                               |
| ----------------------------------- | ------------------------------------------------------ |
| Use existing report system?         | Yes. Extend `/api/reports` and admin Reports.          |
| New reason value                    | `CHILD_SAFETY`                                         |
| User-facing label                   | `Child safety concern`                                 |
| Backend priority                    | `CRITICAL` automatically                               |
| Details required?                   | Yes for `CHILD_SAFETY` and `OTHER`                     |
| Admin handling                      | Existing report detail plus audit notes/actions        |
| Separate child safety table?        | No for v1                                              |
| Anonymous/public reporting?         | Out of scope for v1                                    |
| Automated reporting to authorities? | Out of scope for v1; admins record manual action notes |

## Important Implementation Rule

Do not treat email, WhatsApp, or generic feedback as the primary child-safety mechanism for Play Console.

For Play compliance, the main path should be:

```text
In app -> Report -> Child safety concern -> POST /api/reports -> Admin Reports
```

Email/support can remain as a secondary support path.

## Phase 0: Pre-Implementation Checks

Before coding, confirm these two items:

1. Final child safety contact email:
   - Preferred: `n3.hifriends@gmail.com`
   - Fallback: `n3.hifriends@gmail.com`

2. Final public URL:
   - Recommended: `https://www.oolshik.in/child-safety-standards`

No code should hardcode authority-specific promises beyond what Oolshik can operationally perform.

## Phase 1: Backend Data Contract

### Files

- `src/main/java/com/oolshik/backend/domain/ReportReason.java`
- New migration: `src/main/resources/db/migration/V35__child_safety_report_reason.sql`

Current latest migration is `V34__user_blocking.sql`, so the next migration should be `V35`.

### Required Changes

1. Add enum value:

```text
CHILD_SAFETY
```

2. Add migration to update the `report_event_reason_check` constraint.

Required migration behavior:

```sql
ALTER TABLE public.report_event
  DROP CONSTRAINT IF EXISTS report_event_reason_check;

ALTER TABLE public.report_event
  ADD CONSTRAINT report_event_reason_check
  CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'UNSAFE', 'OTHER', 'CHILD_SAFETY'));
```

### Acceptance Criteria

- Existing reports remain valid.
- New `CHILD_SAFETY` reports can be inserted.
- Invalid reason values still fail.

## Phase 2: Backend Report Creation Behavior

### Files

- `src/main/java/com/oolshik/backend/service/ReportService.java`
- `src/main/resources/messages_en_IN.properties`
- `src/main/resources/messages_mr_IN.properties`

### Required Changes

1. Require details for `CHILD_SAFETY`.

Existing behavior requires details for `OTHER`. Extend this validation:

```text
Reason OTHER or CHILD_SAFETY requires non-blank text.
```

2. Add localized error key:

```text
errors.report.detailsRequiredForChildSafety
```

3. Set priority:

```text
if reason == CHILD_SAFETY -> priority = CRITICAL
else -> existing default MEDIUM
```

4. Keep current targeting rules:

- Exactly one of `taskId` or `targetUserId`.
- Prevent self-reporting.
- For task reports, reporter must be a task participant.

### Non-Goals

- Do not loosen report targeting.
- Do not allow arbitrary report without target in this backend endpoint.
- Do not add media upload in this phase.

### Acceptance Criteria

- `CHILD_SAFETY` with target and details creates report.
- Created report has `priority = CRITICAL`.
- `CHILD_SAFETY` with blank details returns `400`.
- `OTHER` still requires details.
- Existing reasons still work.

## Phase 3: Backend Tests

### Files

- New test recommended: `src/test/java/com/oolshik/backend/service/ReportServiceTest.java`

There is no existing report-specific test in `src/test`, so create a focused service test.

### Test Cases

1. Creates normal report with existing reason.
2. Creates child-safety user report and saves `CRITICAL` priority.
3. Creates child-safety task report and resolves target participant correctly.
4. Rejects child-safety report with blank details.
5. Rejects request with both `taskId` and `targetUserId`.
6. Rejects request with neither `taskId` nor `targetUserId`.
7. Rejects self-report.

### Verification Command

```bash
./mvnw -q test
```

If time is constrained, run:

```bash
./mvnw -Dtest=ReportServiceTest test
```

## Phase 4: Mobile API Contract

### Files

- `Oolshik/app/api/client.ts`

### Required Changes

Add `CHILD_SAFETY` to `ReportPayload.reason`.

Expected union:

```text
"SPAM" | "INAPPROPRIATE" | "UNSAFE" | "OTHER" | "CHILD_SAFETY"
```

### Acceptance Criteria

- TypeScript accepts `reason: "CHILD_SAFETY"` for `OolshikApi.report`.

## Phase 5: Mobile Report Screen

### Files

- `Oolshik/app/screens/ReportScreen.tsx`
- `Oolshik/app/i18n/en.ts`
- `Oolshik/app/i18n/mr.ts`

### Required Changes

1. Add a new reason option:

```text
Child safety concern
```

2. Recommended ordering:

```text
Unsafe / harmful
Child safety concern
Inappropriate content
Spam / advertising
Other
```

3. Details label should change based on reason:

- For `CHILD_SAFETY`: `Details (required)`
- For `OTHER`: `Details (required)`
- For other reasons: `Details (optional)`

4. Validation:

```text
CHILD_SAFETY requires non-blank details.
OTHER requires non-blank details.
```

5. Placeholder for child safety:

```text
Describe what happened. Do not include unnecessary sensitive details.
```

6. Success copy:

```text
Thanks for reporting. Our team will review this shortly.
```

### UX Requirements

- Do not include lengthy policy text inside the report screen.
- Do not make users leave the app to submit a child-safety report.
- Do not route child-safety report submission to email.

### Acceptance Criteria

- User can select `Child safety concern`.
- Submit button calls `/api/reports`.
- Blank details are blocked client-side.
- Backend still enforces blank details server-side.

## Phase 6: Mobile Entry Points

### Files

- `Oolshik/app/screens/task-detail/hooks/useTaskDetailController.tsx`
- `Oolshik/app/screens/task-detail/TaskDetailScreen.tsx`
- `Oolshik/app/screens/FeedbackHubScreen.tsx`
- `Oolshik/app/screens/ProfileScreen.tsx`
- `Oolshik/app/navigators/OolshikNavigator.tsx`

### Required Changes

1. Task detail report button:
   - Keep existing route to `OolshikReport`.
   - Verify route passes `taskId`.

2. User/profile report entry:
   - Ensure any reportable user/profile path opens `OolshikReport` with `targetUserId`.
   - Do not rely only on email from `ProfileScreen`.

3. FeedbackHub:
   - If `taskId` or `targetUserId` exists, route to `OolshikReport`.
   - If no target context exists, either:
     - keep app-level safety feedback for non-user-specific safety issues, or
     - add a clear support path.

### Important Constraint

The backend `/api/reports` currently requires `taskId` or `targetUserId`. Therefore, a fully targetless child-safety report cannot use the existing report endpoint without backend changes.

Recommended v1 approach:

- Use `/api/reports` for user/task child-safety reporting.
- Keep app-level safety feedback for app-wide safety concerns.
- Play Console wording should say users can report child safety concerns from reportable user/task content in-app.

If product wants targetless child-safety reports, add a separate backend-supported context type in a later phase.

### Acceptance Criteria

- From task detail, user can report child safety.
- From reportable user/profile context, user can report child safety.
- Profile support email is not presented as the only safety-reporting option.

## Phase 7: Admin Web Report List

### Files

- `oolshik_web/app/(admin)/reports.tsx`
- `oolshik_web/src/api/mock.ts`

### Required Changes

1. Add `CHILD_SAFETY` to `REASONS`.
2. Give `CHILD_SAFETY` a red tone.
3. Keep `CRITICAL` priority red.
4. Add mock `CHILD_SAFETY` reports with `CRITICAL` priority.
5. Optional but useful: default sort remains newest first.

### Acceptance Criteria

- Admin can filter by `CHILD_SAFETY`.
- Child-safety reports are visually obvious in the table.
- Critical reports remain highlighted.

## Phase 8: Admin Web Report Detail

### Files

- `oolshik_web/app/(admin)/reports/[id].tsx`

### Required Changes

1. Give `CHILD_SAFETY` a red reason tone.
2. Keep existing status transitions:
   - `OPEN`
   - `REVIEWING`
   - `RESOLVED`
   - `DISMISSED`
3. Add suggested quick action buttons only if lightweight:
   - `Evidence reviewed`
   - `Content removed`
   - `User restricted`
   - `Reported to authority`
   - `No violation found`

The backend `addReportAction` accepts arbitrary action strings, so this does not require a backend enum.

### Authority Reporting Notes

When an admin reports externally, the internal note should record:

- Authority or channel used.
- Date/time.
- Reference number if available.
- Admin who recorded the action.

Do not store unnecessary sensitive child details in notes.

### Acceptance Criteria

- Admin can open child-safety report detail.
- Admin can record notes/actions.
- Admin can mark status `REVIEWING` and later `RESOLVED` or `DISMISSED`.

## Phase 9: Optional Admin User Blocking Integration

There is a separate `BLOCK_USER_PLAN.md`. If user blocking is implemented before or alongside this work, connect moderation outcomes to blocking.

Recommended behavior:

- From child-safety report detail, admins can open target user.
- Admin can block target user from the user detail page.
- Report audit note should mention account action.

Do not block users automatically from report creation.

## Phase 10: Public Child Safety Standards Page

### Location

Recommended URL:

```text
https://www.oolshik.in/child-safety-standards
```

### Required Page Content

The page must be public and not require login.

Include:

1. App name:
   - `Oolshik`

2. Prohibited content/conduct:
   - Child sexual abuse material.
   - Child sexual exploitation.
   - Grooming or attempts to exploit minors.
   - Sexualization of minors.
   - Trafficking, coercion, or endangerment involving minors.

3. In-app reporting mechanism:
   - Users can use the in-app Report option and choose `Child safety concern`.

4. Review and action:
   - Oolshik reviews reports.
   - Oolshik may remove content, restrict accounts, block accounts, and preserve evidence where appropriate.

5. Authority reporting:
   - Oolshik complies with applicable child safety laws.
   - Oolshik reports qualifying incidents to relevant regional or national authorities where required by law.

6. Contact:
   - Child safety contact email.

### Wording Guardrails

- Do not promise a specific response time unless operations can meet it.
- Do not claim automated detection unless implemented.
- Do not claim legal reporting to a named authority unless the operational process exists.
- Keep statements accurate and defensible.

### Acceptance Criteria

- Public URL opens without authentication.
- Page mentions Oolshik by name.
- Page explains in-app child-safety reporting.
- Page has a contact email.
- Page wording matches actual implementation.

## Phase 11: Play Console Preparation

Prepare these values after implementation:

| Console Field              | Recommended Value                                                                                                                             |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Published standards URL    | `https://www.oolshik.in/child-safety-standards`                                                                                               |
| In-app reporting mechanism | Yes. Users can report via Report -> Child safety concern.                                                                                     |
| Child safety contact       | `n3.hifriends@gmail.com` or approved fallback                                                                                                 |
| Legal compliance statement | Oolshik complies with applicable child safety laws and reports qualifying incidents to relevant regional/national authorities where required. |

Before submitting:

- Take screenshots of the mobile report flow.
- Take screenshot of admin child-safety report detail.
- Verify public page is live.
- Verify email inbox exists and is monitored.

## Phase 12: Verification Checklist

### Backend

- `POST /api/reports` accepts `CHILD_SAFETY`.
- `CHILD_SAFETY` report with blank details returns `400`.
- `CHILD_SAFETY` report with details saves successfully.
- Saved child-safety report has priority `CRITICAL`.
- Existing reasons still work.
- Admin report list can filter reason `CHILD_SAFETY`.

### Mobile

- Report screen shows `Child safety concern`.
- Selecting child safety makes details required.
- Submission calls `/api/reports`.
- Success message appears.
- Task detail report flow works.
- User/profile report flow works where target user context exists.

### Admin Web

- Reports list displays child-safety reports.
- Filter by child safety works.
- Child-safety report appears red/critical.
- Report detail opens.
- Admin can add note/action.
- Admin can set `REVIEWING`, `RESOLVED`, and `DISMISSED`.

### Public Web

- Child safety standards page is public.
- Page content matches implemented flow.
- Contact email is correct.

## Recommended Execution Order

1. Backend enum and DB migration.
2. Backend service priority/validation.
3. Backend tests.
4. Mobile API type.
5. Mobile report screen and translations.
6. Mobile entry point verification.
7. Admin web filters/tones/mock data.
8. Public child safety standards page.
9. Full verification.
10. Play Console submission.

## Files To Change

### Backend

- `src/main/java/com/oolshik/backend/domain/ReportReason.java`
- `src/main/java/com/oolshik/backend/service/ReportService.java`
- `src/main/resources/messages_en_IN.properties`
- `src/main/resources/messages_mr_IN.properties`
- `src/main/resources/db/migration/V35__child_safety_report_reason.sql`
- `src/test/java/com/oolshik/backend/service/ReportServiceTest.java`

### Mobile

- `Oolshik/app/api/client.ts`
- `Oolshik/app/screens/ReportScreen.tsx`
- `Oolshik/app/i18n/en.ts`
- `Oolshik/app/i18n/mr.ts`
- Optional/verify: `Oolshik/app/screens/FeedbackHubScreen.tsx`
- Optional/verify: `Oolshik/app/screens/ProfileScreen.tsx`

### Admin Web

- `oolshik_web/app/(admin)/reports.tsx`
- `oolshik_web/app/(admin)/reports/[id].tsx`
- `oolshik_web/src/api/mock.ts`

### Public Web

- Add public route/page for child safety standards.

## Risks And Mitigations

| Risk                                                         | Mitigation                                                                                         |
| ------------------------------------------------------------ | -------------------------------------------------------------------------------------------------- |
| DB rejects new reason                                        | Add `V35` constraint migration before mobile rollout.                                              |
| Mobile sends new reason before backend deploy                | Deploy backend first.                                                                              |
| Play reviewer cannot find child-safety report option         | Make label explicit: `Child safety concern`.                                                       |
| App-level safety feedback is confused with report moderation | Keep Play wording focused on reportable user/task content, or add targetless report support later. |
| Admin misses urgent reports                                  | Auto-set `CRITICAL`; highlight in admin UI; default list can stay `OPEN`.                          |
| Public policy over-promises                                  | Use conservative wording tied to actual process.                                                   |

## Implementation Can Proceed?

Yes, after confirming the child-safety contact email and public URL. The existing report and admin moderation system is suitable for v1. The robust path is to extend it with `CHILD_SAFETY`, critical priority, required details, admin visibility, and a matching public standards page.
