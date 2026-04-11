package com.oolshik.backend.web;

import com.oolshik.backend.config.AuthProperties;
import com.oolshik.backend.config.LocaleSupport;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.service.AuthService;
import com.oolshik.backend.service.CurrentUserService;
import com.oolshik.backend.service.GoogleAuthService;
import com.oolshik.backend.service.OtpService;
import com.oolshik.backend.service.UserService;
import com.oolshik.backend.web.dto.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.oolshik.backend.web.error.ConflictOperationException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OtpService otp;
    private final UserService userService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtService jwt;
    private final GoogleAuthService googleAuthService;
    private final CurrentUserService currentUserService;
    private final MessageSource messageSource;
    private final AuthProperties authProperties;

    public AuthController(
            OtpService otp,
            UserService userService,
            AuthService authService,
            UserRepository userRepository,
            JwtService jwt,
            GoogleAuthService googleAuthService,
            CurrentUserService currentUserService,
            MessageSource messageSource,
            AuthProperties authProperties
    ) {
        this.otp = otp;
        this.userService = userService;
        this.authService = authService;
        this.userRepository = userRepository;
        this.jwt = jwt;
        this.googleAuthService = googleAuthService;
        this.currentUserService = currentUserService;
        this.messageSource = messageSource;
        this.authProperties = authProperties;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<?> otpRequest(@RequestBody @Valid OtpRequest req) {
        if (!authProperties.getPhone().isOtpEnabled()) {
            throw new IllegalArgumentException("errors.auth.phoneOtpDisabled");
        }
        var res = otp.requestLoginOtp(req.phone());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> otpVerify(@RequestBody @Valid OtpVerify req) {
        if (!authProperties.getPhone().isOtpEnabled()) {
            throw new IllegalArgumentException("errors.auth.phoneOtpDisabled");
        }
        boolean ok = otp.verifyLoginOtp(req.phone(), req.code());
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error",
                    messageSource.getMessage(
                            "errors.auth.invalidOrExpiredOtp",
                            null,
                            "Invalid or expired OTP",
                            LocaleSupport.normalizeLocale(LocaleContextHolder.getLocale())
                    )
            ));
        }
        var user = userService.getOrCreateByPhone(req.phone(), req.displayName(), req.email());
        String access = jwt.generateAccessToken(user.getId(), user.getPhoneNumber());
        String refresh = jwt.generateRefreshToken(user.getId());
        return ResponseEntity.ok(new TokenResponse(access, refresh));
    }

    @PostMapping("/complete")
    public ResponseEntity<?> complete(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody CompleteProfileReq req
    ) {
        if (principal == null) return ResponseEntity.status(401).build();
        UserEntity me = userService.getOrCreate(principal, req.displayName(), req.email());
        // optionally update email/phone if allowed
        return ResponseEntity.ok(me);
    }

    public record CompleteProfileReq(String displayName, String email) {}

    @PostMapping("/google")
    public ResponseEntity<TokenResponse> google(@RequestBody @Valid GoogleExchangeRequest req) {
        if (!authProperties.getGoogle().isEnabled()) {
            throw new IllegalArgumentException("errors.auth.googleDisabled");
        }
        return ResponseEntity.ok(googleAuthService.authenticate(req.idToken(), req.phone()));
    }

    // Admin password login (ops only)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest req) {
        var res = authService.loginWithPassword(req.email(), req.password());
        return ResponseEntity.ok(new TokenResponse((String)res.get("accessToken"), (String)res.get("refreshToken")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody @Valid RefreshRequest req) {
        String access = authService.refreshAccessToken(req.refreshToken());
        return ResponseEntity.ok(Map.of("accessToken", access));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        UserEntity u = requireCurrentUser(principal);
        String preferredLanguage = LocaleSupport.normalizeTag(u.getPreferredLanguage());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.getId());
        out.put("phone", u.getPhoneNumber());
        out.put("email", u.getEmail());
        out.put("emailVerified", u.isEmailVerified());
        out.put("displayName", u.getDisplayName());
        out.put("roles", u.getRoles());
        out.put("languages", u.getLanguages());
        out.put("preferredLanguage", preferredLanguage);
        out.put("locale", preferredLanguage);
        return ResponseEntity.ok(out);
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody Map<String, Object> patch
    ) {
        UserEntity u = requireCurrentUser(principal);
        if (patch.containsKey("displayName")) u.setDisplayName(String.valueOf(patch.get("displayName")));
        if (patch.containsKey("languages")) u.setLanguages(String.valueOf(patch.get("languages")));
        if (patch.containsKey("preferredLanguage")) {
            u.setPreferredLanguage(LocaleSupport.normalizeTag(String.valueOf(patch.get("preferredLanguage"))));
        }
        if (patch.containsKey("language")) {
            u.setPreferredLanguage(LocaleSupport.normalizeTag(String.valueOf(patch.get("language"))));
        }
        if (patch.containsKey("email")) {
            String nextEmail = normalizeEmail(patch.get("email"));
            String currentEmail = normalizeEmail(u.getEmail());
            if (!Objects.equals(currentEmail, nextEmail)) {
                userRepository.findByEmailIgnoreCase(nextEmail)
                        .filter(existing -> !existing.getId().equals(u.getId()))
                        .ifPresent(existing -> {
                            throw new ConflictOperationException("errors.auth.emailInUse");
                        });
                u.setEmail(nextEmail);
                u.setEmailVerified(false);
            }
        }
        if (u.getPreferredLanguage() == null || u.getPreferredLanguage().isBlank()) {
            u.setPreferredLanguage(LocaleSupport.EN_IN_TAG);
        }
        userRepository.save(u);
        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(
                        "response.updated",
                        null,
                        "updated",
                        LocaleSupport.normalizeLocale(LocaleContextHolder.getLocale())
                ),
                "preferredLanguage", LocaleSupport.normalizeTag(u.getPreferredLanguage())
        ));
    }

    @GetMapping("/me/language")
    public ResponseEntity<?> getPreferredLanguage(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        UserEntity u = requireCurrentUser(principal);
        return ResponseEntity.ok(Map.of(
                "preferredLanguage", LocaleSupport.normalizeTag(u.getPreferredLanguage())
        ));
    }

    @PutMapping("/me/language")
    public ResponseEntity<?> updatePreferredLanguage(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        UserEntity u = requireCurrentUser(principal);
        String preferredLanguage = LocaleSupport.normalizeTag(
                body == null ? null : String.valueOf(body.get("preferredLanguage"))
        );
        u.setPreferredLanguage(preferredLanguage);
        userRepository.save(u);
        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(
                        "response.updated",
                        null,
                        "updated",
                        LocaleSupport.normalizeLocale(LocaleContextHolder.getLocale())
                ),
                "preferredLanguage", preferredLanguage
        ));
    }

    private UserEntity requireCurrentUser(AuthenticatedUserPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("errors.auth.required");
        }
        return currentUserService.require(principal);
    }

    private String normalizeEmail(Object rawEmail) {
        if (rawEmail == null) return null;
        String normalized = String.valueOf(rawEmail).trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
