# Oolshik Backend Claude Guide

Use `docs/ai/repo-map.md` and `docs/ai/current-auth-state.md` as primary context for auth-related work before widening scope.

## Working Rules

- Prefer the smallest relevant file set. Do not do full-repo scans unless required.
- For auth work, start with controller, security filter chain, service layer, token handling, and config.
- Do not infer active architecture from legacy or optional integrations alone.
- Prefer evidence in this order:
  1. controller endpoints
  2. security filter chain and auth filters
  3. token issuance and refresh services
  4. config properties and application yml
- Start with medium effort. Increase only if the task is cross-cutting or inconsistent.
- For review-only tasks, do not generate code unless explicitly asked.

## Auth Focus

Treat these as the primary auth entry points:
- `src/main/java/com/oolshik/backend/web/AuthController.java`
- `src/main/java/com/oolshik/backend/security/SecurityConfig.java`
- `src/main/java/com/oolshik/backend/security/JwtAuthFilter.java`
- `src/main/java/com/oolshik/backend/security/JwtService.java`
- `src/main/java/com/oolshik/backend/service/AuthService.java`
- `src/main/java/com/oolshik/backend/service/GoogleAuthService.java`
- `src/main/java/com/oolshik/backend/service/GoogleIdTokenVerifierService.java`
- `src/main/java/com/oolshik/backend/service/OtpService.java`
- `src/main/java/com/oolshik/backend/config/AuthProperties.java`
- `src/main/java/com/oolshik/backend/repo/UserRepository.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

## Cross-Repo Note

If auth work requires frontend contract verification, inspect only the relevant files in the sibling frontend repo at `../../Oolshik`.

## Review Output Format

When asked for review only, respond with:
1. Compatible assumptions
2. Conflicting assumptions
3. Missing inputs
4. Files that must change
5. Whether implementation can proceed
