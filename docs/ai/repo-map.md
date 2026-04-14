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
- `security/JwtService.java`
- `security/FirebaseIdentityCondition.java`
- `security/FirebaseTokenFilter.java`
- `security/AuthenticatedUserPrincipal.java`
- `service/AuthService.java`
- `service/GoogleAuthService.java`
- `service/GoogleIdTokenVerifierService.java`
- `service/OtpService.java`
- `service/UserService.java`
- `service/CurrentUserService.java`
- `service/OtpProvider.java`
- `service/OtpAuditService.java`
- `config/AuthProperties.java`
- `config/OtpProperties.java`
- `repo/UserRepository.java`
- `repo/FederatedIdentityRepository.java`
- `repo/OtpCodeRepository.java`
- `web/dto/AuthDtos.java`
- `util/PhoneUtil.java`
- `entity/UserEntity.java`
- `entity/FederatedIdentityEntity.java`
- `entity/OtpCodeEntity.java`
- `src/main/resources/application*.yml`

Expand only if a direct dependency requires it.

## Direct Auth Dependencies

These are not top-level entry points, but they are part of the active auth implementation and should be included for implementation work:

- `service/UserService.java`
  - OTP user creation and profile hint application
- `service/CurrentUserService.java`
  - `/me` principal-to-user resolution
- `web/dto/AuthDtos.java`
  - request and response contract for auth endpoints
- `repo/FederatedIdentityRepository.java`
  - Google identity linking and lookup
- `repo/OtpCodeRepository.java`
  - OTP lookup, cooldown, and active-code reads
- `config/OtpProperties.java`
  - OTP TTL, cooldown, provider, and dev behavior
- `service/OtpProvider.java`
  - OTP delivery abstraction
- `service/OtpAuditService.java`
  - OTP audit trail
- `security/FirebaseIdentityCondition.java`
  - runtime activation of Firebase auth mode
- `security/FirebaseTokenFilter.java`
  - Firebase bearer-token authentication path
- `security/AuthenticatedUserPrincipal.java`
  - authenticated principal shape used by `/me` and filters
- `util/PhoneUtil.java`
  - phone normalization used by OTP and Google auth flows
- `entity/UserEntity.java`
  - JWT subject backing record and `/me` payload source
- `entity/FederatedIdentityEntity.java`
  - Google identity persistence model
- `entity/OtpCodeEntity.java`
  - OTP persistence model
