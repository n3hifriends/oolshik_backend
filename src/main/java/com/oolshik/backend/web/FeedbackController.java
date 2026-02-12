package com.oolshik.backend.web;

import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.service.FeedbackService;
import com.oolshik.backend.web.dto.FeedbackDtos.CreateRequest;
import com.oolshik.backend.web.dto.FeedbackDtos.CreateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CreateRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.create(principal, idempotencyKey, req));
    }
}
