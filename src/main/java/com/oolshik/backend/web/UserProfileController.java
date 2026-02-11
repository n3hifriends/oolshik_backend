package com.oolshik.backend.web;

import com.oolshik.backend.repo.HelpRequestRatingRepository;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
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

    private final UserRepository userRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final HelpRequestRatingRepository ratingRepository;

    public UserProfileController(
            UserRepository userRepository,
            HelpRequestRepository helpRequestRepository,
            HelpRequestRatingRepository ratingRepository
    ) {
        this.userRepository = userRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.ratingRepository = ratingRepository;
    }

    @GetMapping("/me/stats")
    public ResponseEntity<?> getMyStats(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal
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
            FirebaseTokenFilter.FirebaseUserPrincipal principal
    ) {
        if (principal.phone() != null && !principal.phone().isBlank()) {
            var byPhone = userRepository.findByPhoneNumber(principal.phone());
            if (byPhone.isPresent()) return byPhone.get();
        }
        if (principal.uid() != null && !principal.uid().isBlank()) {
            var byUid = userRepository.findByFirebaseUid(principal.uid());
            if (byUid.isPresent()) return byUid.get();
        }
        if (principal.email() != null && !principal.email().isBlank()) {
            var byEmail = userRepository.findByEmail(principal.email());
            if (byEmail.isPresent()) return byEmail.get();
        }
        return null;
    }
}
