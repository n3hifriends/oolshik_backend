package com.oolshik.backend.web;

import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.security.JwtService;
import com.oolshik.backend.service.AuthService;
import com.oolshik.backend.service.OtpService;
import com.oolshik.backend.service.UserService;
import com.oolshik.backend.web.dto.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OtpService otp;
    private final UserService userService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtService jwt;

    public AuthController(OtpService otp, UserService userService, AuthService authService, UserRepository userRepository, JwtService jwt) {
        this.otp = otp;
        this.userService = userService;
        this.authService = authService;
        this.userRepository = userRepository;
        this.jwt = jwt;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<?> otpRequest(@RequestBody @Valid OtpRequest req) {
        var res = otp.requestLoginOtp(req.phone());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> otpVerify(@RequestBody @Valid OtpVerify req) {
        boolean ok = otp.verifyLoginOtp(req.phone(), req.code());
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
        var user = userService.getOrCreateByPhone(req.phone(), req.displayName(), req.email());
        String access = jwt.generateAccessToken(user.getId(), user.getPhoneNumber());
        String refresh = jwt.generateRefreshToken(user.getId());
        return ResponseEntity.ok(new TokenResponse(access, refresh));
    }

    @PostMapping("/complete")
    public ResponseEntity<?> complete(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @RequestBody CompleteProfileReq req
    ) {
        if (principal == null) return ResponseEntity.status(401).build();
        UserEntity me = userService.getOrCreate(principal, req.displayName(), req.email());
        // optionally update email/phone if allowed
        return ResponseEntity.ok(me);
    }

    public record CompleteProfileReq(String displayName, String email) {}

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
    public ResponseEntity<?> me(@AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal) {
        var u = userRepository.findByPhoneNumber(principal.phone()).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.getId());
        out.put("phone", u.getPhoneNumber());
        out.put("email", u.getEmail());
        out.put("displayName", u.getDisplayName());
        out.put("roles", u.getRoles());
        out.put("languages", u.getLanguages());
        return ResponseEntity.ok(out);
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal, @RequestBody Map<String, Object> patch) {
        var u = userRepository.findByPhoneNumber(principal.phone()).orElseThrow();
        if (patch.containsKey("displayName")) u.setDisplayName(String.valueOf(patch.get("displayName")));
        if (patch.containsKey("languages")) u.setLanguages(String.valueOf(patch.get("languages")));
        if (patch.containsKey("email")) u.setEmail(String.valueOf(patch.get("email")));
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message", "updated"));
    }
}
