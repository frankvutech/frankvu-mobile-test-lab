# Test credentials

These are **local test credentials only**. The same accounts are seeded in the backend
(`backend/.../SeedData.kt`, stored as PBKDF2 hashes) and in the apps' offline/test mode
(`SeedData.kt` / `Models.swift`). Never use them, or the backend's development JWT secret, anywhere
shared or public.

| Account | Email | Password | Role | Notes |
|---|---|---|---|---|
| Customer | `test@example.com` | `Password123!` | CUSTOMER | Display name "Test Customer" |
| QA user | `qa@example.com` | `QaPassword123!` | CUSTOMER | Display name "QA User" |
| Admin | `admin@example.com` | `Admin123!` | ADMIN | Can open admin screens |
| Locked | `locked@example.com` | `Locked123!` | CUSTOMER | Always rejected with "This account is locked." |

## Predictable login outcomes

Checks run in this order and stop at the first failure:

| Input | Result (English text) | ID |
|---|---|---|
| Empty email | Email is required. | `login_error` |
| Malformed email (`not-an-email`) | Enter a valid email address. | `login_error` |
| Empty password | Password is required. | `login_error` |
| Unknown email | No account exists for this email. | `login_error` |
| Locked account | This account is locked. | `login_error` |
| Wrong password | Incorrect password. | `login_error` |
| Valid credentials | Dashboard (`home_screen`) after a fixed 0.6 s loading state (`login_loading`) | — |

Email rule: `local@domain.tld` with a TLD of at least two letters, no whitespace.

## Registration and password reset

- New accounts are CUSTOMER and exist until the app process ends (every launch starts from the seed).
- Password policy: 8–64 characters, at least one uppercase letter, one digit and one special character.
- Forgot password always confirms for a well-formed email (it never reveals whether an account
  exists). The reset code is always **`123456`**.

## Remember Me

- On: the session is persisted and restored after a process restart; the email is prefilled.
- Off: the session lives in memory; a process restart returns to login.
- Logout clears the session but keeps the prefilled email; clearing app data removes both.
