# Current Backend Auth State

Update this file whenever any of these change:
- login method
- token issuance format
- refresh behavior
- security filter behavior
- auth config flags
- Google or OTP onboarding requirements

## Locally Confirmed Current State

- Auth endpoints are rooted at `/api/auth` in `web/AuthController.java`.
- `/otp/verify` issues both access and refresh JWTs.
- `/google` issues both access and refresh JWTs through `GoogleAuthService`.
- `/refresh` returns a new access token from a refresh token.
- `/me` reads the authenticated principal and returns profile data.
- `JwtAuthFilter` authenticates bearer access tokens into the Spring Security context.
- `SecurityConfig` permits:
  - `/api/auth/otp/**`
  - `/api/auth/google`
  - `/api/auth/login`
  - `/api/auth/refresh`
- `SecurityConfig` may also register a `FirebaseTokenFilter` conditionally.
- Runtime auth flags live in `application.yml` under:
  - `app.auth.phone.otpEnabled`
  - `app.auth.google.enabled`
  - `app.auth.google.requirePhone`
  - `app.auth.google.autoLinkByEmail`
  - `app.auth.google.allowedClientIds`

## Warning

- Do not assume Firebase-based security and JWT-based auth issuance are the same flow.
- Before changing auth behavior, confirm:
  - which endpoints mint JWTs
  - which filter authenticates bearer access tokens
  - whether Firebase token validation is active in the target environment
  - whether refresh returns access only or access plus refresh

## Architecture Rule

Do not infer active auth architecture from package presence alone.
Prefer:
1. controller endpoints
2. security config and filters
3. token issuance and refresh services
4. runtime config in `application*.yml`
