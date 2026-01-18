package com.oolshik.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HelpRequestNotificationService {

    private static final Logger log = LoggerFactory.getLogger(HelpRequestNotificationService.class);

    public void notifyRequester(UUID requesterId, String event, UUID requestId) {
        log.info("notifyRequester event={} requesterId={} requestId={}", event, requesterId, requestId);
    }

    public void notifyHelper(UUID helperId, String event, UUID requestId) {
        log.info("notifyHelper event={} helperId={} requestId={}", event, helperId, requestId);
    }
}
