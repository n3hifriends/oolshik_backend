package com.oolshik.backend.service;

import com.oolshik.backend.domain.HelpRequestActorRole;
import com.oolshik.backend.domain.HelpRequestEventType;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.PhoneRevealEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.PhoneRevealEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.util.MaskingUtils;
import com.oolshik.backend.web.dto.PhoneRevealDtos.RevealPhoneResponse;
import com.oolshik.backend.web.error.ConflictOperationException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PhoneRevealService {

    private static final String REVEAL_SOURCE_MOBILE_TASK_DETAIL = "MOBILE_TASK_DETAIL";

    private final HelpRequestRepository helpRequestRepository;
    private final UserRepository userRepository;
    private final PhoneRevealEventRepository phoneRevealRepo;
    private final HelpRequestEventService helpRequestEventService;

    public PhoneRevealService(
            HelpRequestRepository helpRequestRepository,
            UserRepository userRepository,
            PhoneRevealEventRepository phoneRevealRepo,
            HelpRequestEventService helpRequestEventService) {
        this.helpRequestRepository = helpRequestRepository;
        this.userRepository = userRepository;
        this.phoneRevealRepo = phoneRevealRepo;
        this.helpRequestEventService = helpRequestEventService;
    }

    @Transactional
    public RevealPhoneResponse revealPhone(UUID helpRequestId, UUID viewerUserId) {
        HelpRequestEntity hr = helpRequestRepository.findById(helpRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found"));

        userRepository.findById(viewerUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        UUID targetUserId;
        HelpRequestActorRole viewerRole;
        HelpRequestActorRole targetRole;
        if (viewerUserId.equals(hr.getRequesterId())) {
            UUID helperTarget = hr.getHelperId() != null ? hr.getHelperId() : hr.getPendingHelperId();
            if (helperTarget == null) {
                throw new ConflictOperationException("errors.phoneReveal.helperNotAssigned");
            }
            targetUserId = helperTarget;
            viewerRole = HelpRequestActorRole.REQUESTER;
            targetRole = HelpRequestActorRole.HELPER;
        } else if (
                (hr.getHelperId() != null && viewerUserId.equals(hr.getHelperId())) ||
                (hr.getPendingHelperId() != null && viewerUserId.equals(hr.getPendingHelperId()))
        ) {
            targetUserId = hr.getRequesterId();
            viewerRole = HelpRequestActorRole.HELPER;
            targetRole = HelpRequestActorRole.REQUESTER;
        } else {
            throw new ConflictOperationException("errors.phoneReveal.participantRequired");
        }

        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Target user not found"));

        String fullNumber = target.getPhoneNumber();


        PhoneRevealEventEntity ev = new PhoneRevealEventEntity();
        ev.setPhoneNumber(fullNumber);
        ev.setRequesterUserId(viewerUserId);
        ev.setTargetUserId(targetUserId);
        ev.setHelpRequestId(helpRequestId);
        ev.setViewerRole(viewerRole);
        ev.setTargetRole(targetRole);
        ev.setRevealSource(REVEAL_SOURCE_MOBILE_TASK_DETAIL);
        ev.setMaskedPhone(MaskingUtils.maskPhone(fullNumber));
        phoneRevealRepo.save(ev);

        helpRequestEventService.record(
                helpRequestId,
                HelpRequestEventType.PHONE_REVEALED,
                viewerRole,
                viewerUserId,
                null,
                null,
                null
        );

        long count = phoneRevealRepo.countByRequesterUserId(viewerUserId);
        return new RevealPhoneResponse(fullNumber, count);
    }
}
