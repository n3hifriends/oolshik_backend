Use the repo’s root agent instructions and shared AI docs as primary context:

- `AGENTS.md`
- `CLAUDE.md`
- `docs/ai/repo-map.md`
- `docs/ai/current-auth-state.md`

Use whichever root file is supported by the current tool.

Task:

- [one concrete implementation goal]

Scope files:

- `src/main/java/com/oolshik/backend/web/AuthController.java`
- `src/main/java/com/oolshik/backend/security/SecurityConfig.java`
- `src/main/java/com/oolshik/backend/security/JwtAuthFilter.java`
- `src/main/java/com/oolshik/backend/security/JwtService.java`
- `src/main/java/com/oolshik/backend/security/FirebaseIdentityCondition.java`
- `src/main/java/com/oolshik/backend/security/FirebaseTokenFilter.java`
- `src/main/java/com/oolshik/backend/security/AuthenticatedUserPrincipal.java`
- `src/main/java/com/oolshik/backend/service/AuthService.java`
- `src/main/java/com/oolshik/backend/service/GoogleAuthService.java`
- `src/main/java/com/oolshik/backend/service/GoogleIdTokenVerifierService.java`
- `src/main/java/com/oolshik/backend/service/OtpService.java`
- `src/main/java/com/oolshik/backend/service/UserService.java`
- `src/main/java/com/oolshik/backend/service/CurrentUserService.java`
- `src/main/java/com/oolshik/backend/service/OtpProvider.java`
- `src/main/java/com/oolshik/backend/service/OtpAuditService.java`
- `src/main/java/com/oolshik/backend/config/AuthProperties.java`
- `src/main/java/com/oolshik/backend/config/OtpProperties.java`
- `src/main/java/com/oolshik/backend/repo/UserRepository.java`
- `src/main/java/com/oolshik/backend/repo/FederatedIdentityRepository.java`
- `src/main/java/com/oolshik/backend/repo/OtpCodeRepository.java`
- `src/main/java/com/oolshik/backend/web/dto/AuthDtos.java`
- `src/main/java/com/oolshik/backend/util/PhoneUtil.java`
- `src/main/java/com/oolshik/backend/entity/UserEntity.java`
- `src/main/java/com/oolshik/backend/entity/FederatedIdentityEntity.java`
- `src/main/java/com/oolshik/backend/entity/OtpCodeEntity.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- [exact frontend files under `../../Oolshik/` only if API contract changes require it]

Constraints:

- preserve JWT access and refresh contract unless explicitly changing it
- preserve public auth route behavior unless explicitly changing it
- do not assume Firebase filter behavior is active in every environment
- do not widen auth scope beyond direct dependencies

Acceptance criteria:

- [observable backend outcomes]
- [API contract outcomes]
- [security expectations]

Do not break:

- `/api/auth/otp/request`
- `/api/auth/otp/verify`
- `/api/auth/google`
- `/api/auth/refresh`
- `/api/auth/me`
- bearer access-token authentication in `JwtAuthFilter`

Validation commands:

- `./mvnw -q -DskipTests compile`
- targeted tests for touched auth files, for example:
  - `./mvnw -q -Dtest=AuthControllerOtpWebMvcTest test`
  - `./mvnw -q -Dtest=GoogleAuthServiceTest,GoogleIdTokenVerifierServiceTest test`

Output format:

1. Design and assumptions
2. Planned file changes
3. Code
4. Validation results
