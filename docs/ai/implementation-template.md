Use `CLAUDE.md`, `docs/ai/repo-map.md`, and `docs/ai/current-auth-state.md` as primary context.

Task:
- [one concrete implementation goal]

Scope files:
- [exact backend files]
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
