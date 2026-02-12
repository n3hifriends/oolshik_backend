package com.oolshik.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oolshik.backend.domain.FeedbackContextType;
import com.oolshik.backend.domain.FeedbackType;
import com.oolshik.backend.entity.FeedbackEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.FeedbackEventRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.web.dto.FeedbackDtos.CreateRequest;
import com.oolshik.backend.web.dto.FeedbackDtos.CreateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackEventRepository feedbackRepo;
    @Mock
    private UserService userService;

    private FeedbackService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackService(
                feedbackRepo,
                userService,
                10,
                24,
                365
        );
    }

    @Test
    void idempotencyReturnsExisting() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);

        FeedbackEventEntity existing = new FeedbackEventEntity();
        existing.setId(eventId);
        existing.setCreatedAt(OffsetDateTime.now());

        when(userService.getOrCreate(any(), any(), any())).thenReturn(user);
        when(feedbackRepo.findByUserIdAndIdempotencyKey(eq(userId), eq("key-123")))
                .thenReturn(Optional.of(existing));

        CreateRequest req = new CreateRequest(
                FeedbackType.BUG,
                FeedbackContextType.APP,
                null,
                null,
                List.of("category:CRASH"),
                "test",
                "en-IN",
                "1.0",
                "android",
                "Pixel"
        );

        CreateResponse res = service.create(principal(), "key-123", req);
        assertEquals(eventId, res.id());
        verify(feedbackRepo, never()).save(any());
    }

    @Test
    void rateLimitRejected() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);

        when(userService.getOrCreate(any(), any(), any())).thenReturn(user);
        when(feedbackRepo.findByUserIdAndIdempotencyKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(feedbackRepo.countByUserIdAndCreatedAtAfter(eq(userId), any()))
                .thenReturn(10L);

        CreateRequest req = new CreateRequest(
                FeedbackType.FEATURE,
                FeedbackContextType.APP,
                null,
                null,
                List.of("category:SEARCH"),
                "test",
                "en-IN",
                "1.0",
                "android",
                "Pixel"
        );

        assertThrows(ResponseStatusException.class, () -> service.create(principal(), "key-1", req));
    }

    @Test
    void csatRequiresRating() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        when(userService.getOrCreate(any(), any(), any())).thenReturn(user);

        CreateRequest req = new CreateRequest(
                FeedbackType.CSAT,
                FeedbackContextType.APP,
                null,
                null,
                List.of("tag:helpful"),
                null,
                "en-IN",
                "1.0",
                "android",
                "Pixel"
        );

        assertThrows(ResponseStatusException.class, () -> service.create(principal(), "key-2", req));
    }

    private FirebaseTokenFilter.FirebaseUserPrincipal principal() {
        return new FirebaseTokenFilter.FirebaseUserPrincipal("uid", "+911234567890", "a@b.com");
    }
}
