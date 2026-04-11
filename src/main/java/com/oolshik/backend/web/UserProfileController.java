package com.oolshik.backend.web;

import com.oolshik.backend.repo.HelpRequestRatingRepository;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final HelpRequestRepository helpRequestRepository;
    private final HelpRequestRatingRepository ratingRepository;
    private final CurrentUserService currentUserService;

    public UserProfileController(
            HelpRequestRepository helpRequestRepository,
            HelpRequestRatingRepository ratingRepository,
            CurrentUserService currentUserService
    ) {
        this.helpRequestRepository = helpRequestRepository;
        this.ratingRepository = ratingRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me/stats")
    public ResponseEntity<?> getMyStats(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        var user = resolveUser(principal);
        if (user == null) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("error", "user_not_found");
            return ResponseEntity.status(404).body(out);
        }
        BigDecimal avgRating = ratingRepository.findAvgRatingForUser(user.getId());
        long completedHelps = helpRequestRepository.countCompletedHelps(user.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("avgRating", avgRating != null ? avgRating.doubleValue() : null);
        out.put("completedHelps", completedHelps);
        return ResponseEntity.ok(out);
    }

    private com.oolshik.backend.entity.UserEntity resolveUser(
            AuthenticatedUserPrincipal principal
    ) {
        return currentUserService.resolve(principal);
    }
}
