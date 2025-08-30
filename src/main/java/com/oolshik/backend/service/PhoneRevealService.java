package com.oolshik.backend.service;

import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.PhoneRevealEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.PhoneRevealEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.dto.PhoneRevealDtos.RevealPhoneResponse;
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

        UserEntity requester = userRepository.findById(hr.getRequesterId())
                .orElseThrow(() -> new EntityNotFoundException("Requester not found"));

        UserEntity viewer = userRepository.findById(viewerUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String fullNumber = requester.getPhoneNumber();


        PhoneRevealEventEntity ev = new PhoneRevealEventEntity();
        ev.setPhoneNumber(fullNumber);
        ev.setRequesterUserId(helpRequestId);
        ev.setTargetUserId(viewerUserId);
        phoneRevealRepo.save(ev);

        long count = phoneRevealRepo.countByRequesterUserId(helpRequestId);
        return new RevealPhoneResponse(fullNumber, count);
    }
}