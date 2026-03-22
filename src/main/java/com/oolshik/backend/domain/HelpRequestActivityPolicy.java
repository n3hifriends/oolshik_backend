package com.oolshik.backend.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class HelpRequestActivityPolicy {

    private static final Set<HelpRequestStatus> ACTIVE_STATUS_SET = EnumSet.of(
            HelpRequestStatus.OPEN,
            HelpRequestStatus.PENDING_AUTH,
            HelpRequestStatus.ASSIGNED,
            HelpRequestStatus.WORK_DONE_PENDING_CONFIRMATION,
            HelpRequestStatus.REVIEW_REQUIRED
    );

    private static final List<HelpRequestStatus> ACTIVE_STATUSES = List.copyOf(ACTIVE_STATUS_SET);

    private HelpRequestActivityPolicy() {
    }

    public static List<HelpRequestStatus> activeStatuses() {
        return ACTIVE_STATUSES;
    }

    public static boolean isActive(HelpRequestStatus status) {
        return status != null && ACTIVE_STATUS_SET.contains(status);
    }
}
