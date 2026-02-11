package com.oolshik.backend.web;

import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.service.UserDeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserDeviceController {

    private final UserDeviceService deviceService;
    private final UserRepository userRepository;

    public UserDeviceController(UserDeviceService deviceService, UserRepository userRepository) {
        this.deviceService = deviceService;
        this.userRepository = userRepository;
    }

    @PostMapping("/device")
    public ResponseEntity<?> registerDevice(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @RequestBody @Valid RegisterDeviceRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        var user = userRepository.findByPhoneNumber(principal.phone()).orElseThrow();
        deviceService.registerDevice(user.getId(), request.token(), request.platform());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/device")
    public ResponseEntity<?> unregisterDevice(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @RequestBody @Valid UnregisterDeviceRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        var user = userRepository.findByPhoneNumber(principal.phone()).orElseThrow();
        deviceService.unregisterDevice(user.getId(), request.token());
        return ResponseEntity.noContent().build();
    }

    public record RegisterDeviceRequest(
            @NotBlank String token,
            String platform
    ) {}

    public record UnregisterDeviceRequest(
            @NotBlank String token
    ) {}
}
