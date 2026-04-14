Use the repo’s root agent instructions and shared AI docs as primary context:

- `AGENTS.md`
- `CLAUDE.md`
- `docs/ai/repo-map.md`
- `docs/ai/current-auth-state.md`

Use whichever root file is supported by the current tool.

Task: Review whether the attached implementation prompt is compatible with the current code.

Scope limit:

- Inspect only:
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
- If frontend contract verification is required, inspect only:
  - `../../Oolshik/app/context/AuthContext.tsx`
  - `../../Oolshik/app/screens/login/useLoginScreenController.ts`
  - `../../Oolshik/app/api/index.ts`
  - `../../Oolshik/app/api/client.ts`
  - `../../Oolshik/app/auth/tokens.ts`
  - `../../Oolshik/app/config/config.base.ts`
  - `../../Oolshik/app/config/config.dev.ts`
  - `../../Oolshik/app/config/config.prod.ts`
  - `../../Oolshik/app.config.ts`
- Expand scope only if a direct dependency requires it.
- Do not scan unrelated transcription, payments, notifications, infra, or media code unless a direct dependency chain requires it.

Output only:

1. Compatible assumptions
2. Conflicting assumptions
3. Missing inputs
4. Files that must change
5. Whether implementation can proceed

Do not generate code.
