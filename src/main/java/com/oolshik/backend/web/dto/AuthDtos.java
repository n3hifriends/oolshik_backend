package com.oolshik.backend.web.dto;

import jakarta.validation.constraints.*;

public class AuthDtos {

    public record OtpRequest(
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "{validation.phone.invalid}") String phone
    ) {}

    public record OtpVerify(
            @NotBlank String phone,
            @NotBlank @Size(min = 4, max = 8) String code,
            String displayName,
            @Email String email
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record TokenResponse(String accessToken, String refreshToken) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
}
