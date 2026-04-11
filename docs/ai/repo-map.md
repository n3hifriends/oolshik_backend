# Backend Repo Map

This file is compact by design. Use it for first-pass context, then inspect code only where needed.

## App Shape

- Spring Boot backend
- Main code under `src/main/java/com/oolshik/backend`
- Runtime config under `src/main/resources/application*.yml`

## Auth Entry Points

- `web/AuthController.java`
  - `/api/auth/otp/request`
  - `/api/auth/otp/verify`
  - `/api/auth/google`
  - `/api/auth/login`
  - `/api/auth/refresh`
  - `/api/auth/me`

- `security/SecurityConfig.java`
  - public auth routes
  - stateless security
  - JWT filter registration
  - optional Firebase token filter registration

- `security/JwtAuthFilter.java`
  - bearer-token parsing
  - access-token authentication into Spring Security context

- `service/AuthService.java`
  - password login
  - refresh-token to access-token exchange

- `service/GoogleAuthService.java`
  - Google ID token verification, linking, user creation, JWT issuance

- `service/OtpService.java`
  - OTP request and verification path

- `config/AuthProperties.java`
  - auth flags and Google-specific config

- `repo/UserRepository.java`
  - user lookup for auth, refresh, linking, and me

## Baseline Auth Review Scope

Start with:
- `web/AuthController.java`
- `security/SecurityConfig.java`
- `security/JwtAuthFilter.java`
- `service/AuthService.java`
- `service/GoogleAuthService.java`
- `service/OtpService.java`
- `config/AuthProperties.java`
- `repo/UserRepository.java`
- `src/main/resources/application*.yml`

Expand only if a direct dependency requires it.
