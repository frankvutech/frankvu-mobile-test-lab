# Backend

`backend/` is a runnable Kotlin/Ktor REST API — a first-class component that the Android and iOS apps
call, and that the API automation suite (`api-tests/`) tests independently of either app.

## Architecture

```text
Android app ─┐
             ├── Backend REST API ── Database (PostgreSQL in Docker, H2 locally/tests)
iOS app ─────┘
                         ↑
                 API automation tests (pytest + httpx)
```

Inside the backend, each layer depends only on the one below:

| Layer | Package | Responsibility |
|---|---|---|
| Routes | `routes/` | HTTP ↔ service calls. Parameter and body binding, status codes. No business rules. |
| Services | `service/` | Validation, business rules (stock, cart limits, checkout), authorization checks, transactions. |
| Repositories | `persistence/Repositories.kt` | Table ↔ domain mapping with Exposed. No rules, no transactions of their own. |
| Persistence | `persistence/Database.kt`, `SeedData.kt` | Connection pool, schema creation, deterministic seed and reset. |
| Security | `security/` | PBKDF2 password hashing, HS256 bearer tokens. |

Services wrap each operation in one transaction (`PlaygroundDatabase.query { }`), so checkout —
re-check stock, decrement it, create the order, empty the cart — is atomic.

## Technology

Kotlin 2.2 · Ktor 3.2 (Netty, content negotiation, status pages, JWT auth, Swagger UI, CORS) ·
kotlinx.serialization · Exposed 0.61 · HikariCP · PostgreSQL 16 (Docker) / H2 2.3 in PostgreSQL mode
(local default and tests) · Logback · JUnit + Ktor test host · ktlint · detekt.

## Running

| How | Command | Database |
|---|---|---|
| Gradle (fastest) | `make backend-run` (`cd backend && ./gradlew run`) | H2 file in `backend/data/` |
| Docker Compose | `make backend-up` (`docker compose up --build --wait backend`) | PostgreSQL container (`postgres-data` volume) |
| JAR | `make backend-jar` then `java -jar backend/build/libs/backend.jar` | H2 unless `DATABASE_URL` is set |

The API listens on **http://localhost:8080** (port `PORT`, or `BACKEND_PORT` for Compose).

- Swagger UI: http://localhost:8080/docs
- OpenAPI specification: http://localhost:8080/openapi/openapi.yaml
  (source: `backend/src/main/resources/openapi/openapi.yaml`)
- Liveness: `GET /health` · Readiness (database reachable): `GET /ready`

## Configuration

Environment variables (`backend/.env.example`; Compose reads a root `.env`, see `.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DATABASE_URL` | H2 file `./data/playground` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/playground` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `sa` / empty | Database credentials |
| `JWT_SECRET` | a development-only constant | HMAC key for tokens — set your own outside local development |
| `JWT_ISSUER` | `mobile-test-playground` | Token issuer |
| `TOKEN_TTL_SECONDS` | `3600` | Token lifetime |
| `TEST_SUPPORT_ENABLED` | `true` | Enables `POST /api/v1/test/reset` and `GET /api/v1/test/server-error` |

No secret is committed: the defaults are documented local-development values, and `.env` is
git-ignored.

## Data and determinism

On first start the schema is created (idempotent) and seeded; `POST /api/v1/test/reset` restores the
seed at any time, including restarting order numbering (the first order after a reset is id 1,
`ORDER-0001`).

| Seed | Values |
|---|---|
| Accounts | see [test-credentials.md](test-credentials.md); passwords are stored as PBKDF2-SHA256 hashes |
| Products | `product_001` … `product_020`, same names, prices and categories as the apps; stock 10, except `product_019` (1) and `product_020` (0, sold out) |
| Wishlist | `qa@example.com` starts with `product_002` |
| Carts, orders | empty |

**Disable `TEST_SUPPORT_ENABLED` in any shared environment** — reset wipes all data.

## Security model

- Passwords: PBKDF2-HMAC-SHA256, 120 000 iterations, 16-byte random salt per password.
- Tokens: HS256 JWT with issuer, audience, subject (user id), `role` claim, unique `jti` and expiry.
  `POST /api/v1/auth/logout` stores the `jti` in `revoked_tokens`; revoked tokens are rejected.
- Authorization is enforced on the server: every `/api/v1/admin/**` handler checks the token's role
  (`403 FORBIDDEN` for customers), and users can only see their own carts, wishlists and orders
  (another user's order is `404`, so ids cannot be probed).
- Login distinguishes `UNKNOWN_USER` from `INVALID_PASSWORD` on purpose, so tests can tell the
  scenarios apart. A production API would return one generic error.
- Password reset uses the fixed code `123456` and sends no email. Tokens issued before a reset stay
  valid until they expire.

## Connecting the apps

| Client | Backend URL | Notes |
|---|---|---|
| Android emulator | `http://10.0.2.2:8080/` (default) | `10.0.2.2` is the emulator's alias for the host |
| iOS simulator | `http://localhost:8080` (default) | the simulator shares the Mac's network |
| Physical device | `http://<your computer's LAN IP>:8080` | set in the app: Settings → Test Mode → Backend URL override |

Android build options: `./gradlew installDebug -PapiBaseUrl=http://192.168.1.20:8080/` changes the
default URL; `-PuseBackend=false` builds with the offline mode as default. Debug builds allow plain
HTTP to any host (for LAN development); release builds allow it only to `10.0.2.2` and loopback.

iOS: `API_BASE_URL` and `USE_BACKEND_BY_DEFAULT` in `ios/project.yml` (Info.plist).
App Transport Security allows local networking only (`NSAllowsLocalNetworking`). Launch arguments
`-offlineMode` and `-backendURL <url>` override at runtime.

### Backend mode and the offline/test mode

Both apps use the backend for authentication, catalog, cart and checkout through repository and
data-source abstractions (`RemoteAuthDataSource`, `RemoteProductRepository`/`RemoteProductDataSource`,
`RemoteCartRepository`/`CartRepository`). The local deterministic data is an **explicit
offline/test mode**, switched in Test Mode ("Use backend API") — for offline UI development and for
the UI test suites, which run without a backend. It is not a substitute for the API. Switching mode
signs the user out.

The other scenario screens (forms, gestures, the simulated Network playground, …) are client-side by
design and do not need the backend.

## Tests

```bash
make backend-test    # unit + integration tests: Ktor test engine, isolated in-memory H2 per test
make backend-lint    # ktlint + detekt
make api-test        # the HTTP automation suite against a running server (see api-testing.md)
```
