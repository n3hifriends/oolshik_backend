package com.oolshik.backend.service;

import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.PhoneRevealEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.PhoneRevealEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.dto.PhoneRevealDtos.RevealPhoneResponse;
import com.oolshik.backend.web.error.ConflictOperationException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PhoneRevealService {

    private final HelpRequestRepository helpRequestRepository;
    private final UserRepository userRepository;
    private final PhoneRevealEventRepository phoneRevealRepo;

    public PhoneRevealService(
            HelpRequestRepository helpRequestRepository,
            UserRepository userRepository,
            PhoneRevealEventRepository phoneRevealRepo) {
        this.helpRequestRepository = helpRequestRepository;
        this.userRepository = userRepository;
        this.phoneRevealRepo = phoneRevealRepo;
    }

    @Transactional
    public RevealPhoneResponse revealPhone(UUID helpRequestId, UUID viewerUserId) {
        HelpRequestEntity hr = helpRequestRepository.findById(helpRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found"));

        UserEntity viewer = userRepository.findById(viewerUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        UUID targetUserId;
        if (viewerUserId.equals(hr.getRequesterId())) {
            UUID helperTarget = hr.getHelperId() != null ? hr.getHelperId() : hr.getPendingHelperId();
            if (helperTarget == null) {
                throw new ConflictOperationException("Helper not assigned");
            }
            targetUserId = helperTarget;
        } else if (
                (hr.getHelperId() != null && viewerUserId.equals(hr.getHelperId())) ||
                (hr.getPendingHelperId() != null && viewerUserId.equals(hr.getPendingHelperId()))
        ) {
            targetUserId = hr.getRequesterId();
        } else {
            throw new ConflictOperationException("Not allowed");
        }

        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Target user not found"));

        String fullNumber = target.getPhoneNumber();


        PhoneRevealEventEntity ev = new PhoneRevealEventEntity();
        ev.setPhoneNumber(fullNumber);
        ev.setRequesterUserId(helpRequestId);
        ev.setTargetUserId(targetUserId);
        phoneRevealRepo.save(ev);

        long count = phoneRevealRepo.countByRequesterUserId(helpRequestId);
        return new RevealPhoneResponse(fullNumber, count);
    }
}
