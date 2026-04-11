package com.oolshik.backend.web.dto;

import com.oolshik.backend.logging.Sensitive;
import jakarta.validation.constraints.*;

public class AuthDtos {

    public record OtpRequest(
            @Sensitive
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "{validation.phone.invalid}") String phone
    ) {}

    public record OtpVerify(
            @Sensitive @NotBlank String phone,
            @Sensitive @NotBlank @Size(min = 4, max = 8) String code,
            String displayName,
            @Email String email
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @Sensitive @NotBlank String password
    ) {}

    public record TokenResponse(String accessToken, String refreshToken) {}
    public record RefreshRequest(@Sensitive @NotBlank String refreshToken) {}
    public record GoogleExchangeRequest(
            @Sensitive @NotBlank String idToken,
            @Sensitive String phone
    ) {}
}
