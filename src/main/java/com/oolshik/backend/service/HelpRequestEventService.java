package com.oolshik.backend.service;

import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.entity.HelpRequestEventEntity;
import com.oolshik.backend.repo.HelpRequestEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HelpRequestEventService {

    private final HelpRequestEventRepository repo;

    public HelpRequestEventService(HelpRequestEventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public HelpRequestEventEntity record(
            UUID requestId,
            HelpRequestEventType eventType,
            HelpRequestActorRole actorRole,
            UUID actorUserId,
            String reasonCode,
            String reasonText,
            String metadataJson
    ) {
        HelpRequestEventEntity event = new HelpRequestEventEntity();
        event.setRequestId(requestId);
        event.setEventType(eventType);
        event.setActorRole(actorRole);
        event.setActorUserId(actorUserId);
        event.setReasonCode(reasonCode);
        event.setReasonText(reasonText);
        event.setMetadata(metadataJson);
        return repo.save(event);
    }
}
