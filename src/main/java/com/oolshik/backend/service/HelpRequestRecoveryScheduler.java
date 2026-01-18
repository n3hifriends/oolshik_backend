package com.oolshik.backend.service;

import com.oolshik.backend.config.TaskRecoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
public class HelpRequestRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HelpRequestRecoveryScheduler.class);

    private final HelpRequestService helpRequestService;
    private final TaskRecoveryProperties recoveryProperties;

    public HelpRequestRecoveryScheduler(
            HelpRequestService helpRequestService,
            TaskRecoveryProperties recoveryProperties
    ) {
        this.helpRequestService = helpRequestService;
        this.recoveryProperties = recoveryProperties;
    }

    @Scheduled(fixedDelayString = "${app.task-recovery.schedulerDelayMs:60000}")
    public void autoReleaseExpiredAssignments() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> expired = helpRequestService.findExpiredAssignments(
                now,
                recoveryProperties.getSchedulerBatchSize()
        );
        if (expired.isEmpty()) {
            return;
        }
        int released = 0;
        for (UUID id : expired) {
            if (helpRequestService.autoReleaseExpired(id)) {
                released++;
            }
        }
        if (released > 0) {
            log.info("Auto-released {} expired assignments", released);
        }
    }
}
