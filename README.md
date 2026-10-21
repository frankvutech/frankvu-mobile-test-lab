# Mobile Test Playground

A deterministic practice environment for mobile UI automation, API automation and CI. Three
first-class components share one data set:

- **Backend API** (`backend/`): Kotlin + Ktor REST API with PostgreSQL/H2, JWT auth, role-based
  authorization, OpenAPI docs and a reset endpoint. Tested with JUnit and an independent **pytest +
  httpx** suite (`api-tests/`).
- **Android:** Kotlin, Jetpack Compose, Material 3. Automated with Compose UI Testing and UIAutomator.
- **iOS:** Swift, SwiftUI. Automated with XCTest and XCUITest.

It is not a commercial product. Everything exists to be tested: stable IDs, fixed seed data, fixed
delays, a resettable backend, and a Test Mode that restores a known state.

```text
Android app ─┐
             ├── Backend REST API ── Database
iOS app ─────┘
                         ↑
                 API automation tests
```

The apps call the backend for authentication, catalog, cart and orders. A local deterministic data
set is available only as an explicit offline/test mode (Test Mode → "Use backend API" off), for
offline UI work and the UI test suites.

## Contents

- [Scenarios](#scenarios)
- [Backend API](#backend-api)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Repository structure](#repository-structure)
- [Running Android](#running-android)
- [Running iOS](#running-ios)
- [Running tests](#running-tests)
- [Docker Android build](#docker-android-build)
- [CI workflows](#ci-workflows)
- [Test credentials](#test-credentials)
- [Test IDs](#test-ids)
- [Verification status](#verification-status)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)

## Scenarios

The dashboard (`home_screen`) lists 20 scenarios (`scenario_<id>`). The app also has a bottom
navigation bar (Home, Catalog, Cart, Profile), a product catalogue with cart and checkout, admin
screens, and Settings.

| # | Scenario | ID | Highlights |
|---|---|---|---|
| 1 | Login and authentication | `auth` | Login, register, forgot/reset password, remember me, show/hide password, logout, session and role display |
| 2 | Forms | `forms` | Text, email, password, number, dropdown, radio, checkbox, switch, slider, date, time, multiline; required/format/length/range validation |
| 3 | Buttons and controls | `controls` | Primary, secondary, disabled, toggle, checkbox, radio, slider, stepper, switch, progress |
| 4 | Lists | `lists` | Static (30 items), dynamic (add, edit, delete, search, sort), infinite (100 rows, end state) |
| 5 | Scrolling | `scrolling` | Long vertical list, horizontal carousel, scroll-to-top/bottom |
| 6 | Gestures | `gestures` | Tap, double tap, long press, swipes, drag and drop, pinch zoom, pan |
| 7 | Navigation | `navigation` | Stack, modal, nested navigation, state retention across rotation/process death |
| 8 | Dialogs and alerts | `dialogs` | Confirmation, information, warning, error, action sheet, bottom sheet, snackbar |
| 9 | Permissions | `permissions` | Camera, photos, notifications, location: not requested / granted / denied / permanently denied |
| 10 | WebView | `webview` | Local HTML page with input, button, checkbox, link, form and a JavaScript bridge |
| 11 | Network | `network` | Success, slow, timeout, HTTP 400/401/403/404/500, empty, malformed |
| 12 | Accessibility | `accessibility` | Headings, labelled icon buttons with state, grouped content, custom actions, live region, touch targets |
| 13 | Error handling | `errors` | Network, unexpected, empty and recoverable errors with retry; inline validation |
| 14 | Dynamic content | `dynamic_content` | Delayed load, show/hide, delayed enable, countdown, changing text |
| 15 | Test data | `test_data` | Seed users and products, generate and reset data |
| 16 | Deep links | `deep_links` | `mtp://playground/...` examples and a custom link field |
| 17 | Date and time | `date_time` | Pickers compared with a fixed reference date |
| 18 | File and image picker | `file_picker` | System photo picker and document picker (no permission needed) |
| 19 | Notifications | `notifications` | Local notifications now / in 5 s, permission flow |
| 20 | Test Mode | `test_mode` | Reset app state, clear storage, reset/generate data, simulate network errors, disable authentication |

## Backend API

```bash
make backend-run                 # http://localhost:8080 with an H2 database, or:
docker compose up --build -d backend    # PostgreSQL + backend, with health checks
```

| What | Where |
|---|---|
| Swagger UI | http://localhost:8080/docs |
| OpenAPI spec | http://localhost:8080/openapi/openapi.yaml |
| Health / readiness | `/health`, `/ready` |
| Reset test data | `make api-reset` (`POST /api/v1/test/reset`) |
| Backend tests | `make backend-test`, `make backend-lint` |
| API automation | `make api-test-setup` once, then `make api-test` |

Connecting the apps:

| Client | URL |
|---|---|
| Android emulator | `http://10.0.2.2:8080/` (default) |
| iOS simulator | `http://localhost:8080` (default) |
| Physical device | your computer's LAN address, e.g. `http://192.168.1.20:8080` — Settings → Test Mode → Backend URL override (Android also: `-PapiBaseUrl=`) |

Details: [docs/backend.md](docs/backend.md), [docs/api.md](docs/api.md), [docs/api-testing.md](docs/api-testing.md).

## Architecture

Clean Architecture with MVVM: Presentation → Domain → Data. The domain is pure (a Kotlin/JVM module
on Android; Foundation-only Swift on iOS) and holds every rule both platforms share — validation,
authorization (`AccessPolicy`), deep-link parsing, paging, seed data. Repositories are interfaces in
the domain, implemented in the data layer and injected (Hilt on Android, an `AppEnvironment`
container on iOS). In backend mode, repositories call the API through remote data sources
(Retrofit/OkHttp on Android, URLSession on iOS); in the offline/test mode they use local
deterministic data. The Network *scenario* screen is separate: it simulates HTTP outcomes locally on
purpose, so every error can be produced on demand.

Details, including persistence rules and documented exceptions: [docs/architecture.md](docs/architecture.md).

## Technology stack

| | Android | iOS |
|---|---|---|
| Language | Kotlin 2.2 | Swift (5 language mode) |
| UI | Jetpack Compose, Material 3, Navigation Compose | SwiftUI, Observation, NavigationStack |
| DI | Hilt | `AppEnvironment` container |
| Async | Coroutines, Flow / StateFlow | async/await |
| Networking | Retrofit + OkHttp + kotlinx.serialization (backend API; local interceptor for the Network scenario) | URLSession (backend API; local `URLProtocol` for the Network scenario) |
| Persistence | DataStore (session, settings), Room (dynamic list) | UserDefaults suite |
| Unit tests | JUnit 4, kotlin-test, Turbine, coroutines-test | XCTest |
| UI tests | Compose UI Test, UIAutomator, AndroidX Test Orchestrator | XCUITest |
| Static analysis | Android Lint (warnings as errors), ktlint, detekt | SwiftLint |
| Build | Gradle 9.5 (Kotlin DSL, version catalogue), AGP 9.3, compileSdk 35, minSdk 26 | Xcode project generated by XcodeGen, iOS 17+ |

## Repository structure

| | Backend |
|---|---|
| Language / framework | Kotlin 2.2, Ktor 3.2 (Netty) |
| Persistence | Exposed + HikariCP; PostgreSQL 16 (Docker), H2 (local default, tests) |
| Security | HS256 JWT bearer tokens with revocation, PBKDF2-SHA256 password hashes |
| Docs | OpenAPI 3 spec + Swagger UI |
| Tests | JUnit + Ktor test host; API automation with pytest + httpx |

```text
.
├── backend/                     Kotlin/Ktor REST API (routes → services → repositories → database)
├── api-tests/                   pytest + httpx API automation suite
├── android/                     Gradle project
│   ├── app/                     Activity, navigation, screens, ViewModels, instrumented tests
│   ├── core/domain/             Pure Kotlin rules, use cases, interfaces (JVM tests)
│   ├── core/data/               Repositories, DataStore, Room, Retrofit + local interceptor
│   ├── core/ui/                 Theme, shared composables, TestTags (ID catalogue)
│   └── config/detekt/
├── ios/
│   ├── project.yml              XcodeGen spec (generated .xcodeproj is committed)
│   ├── MobileTestPlayground/    App: App/, Domain/, Data/, UI/, Resources/
│   ├── MobileTestPlaygroundTests/
│   └── MobileTestPlaygroundUITests/
├── shared/
│   ├── strings/strings.json     Single source of en/de strings for both apps
│   └── web/playground.html      The WebView page, bundled by both apps
├── tools/                       String and test-ID generators, iOS typecheck / host-test scripts
├── docker/android/              Android build image
├── docker/backend/              Backend image
├── docs/                        architecture, automation, test IDs, credentials, build environments
├── .github/workflows/           android.yml, ios.yml
├── docker-compose.yml
└── Makefile
```

## Running Android

Requirements: JDK 17 or newer, Android SDK (platform 35, build-tools 36), an emulator or device
(API 26+).

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > android/local.properties   # or export ANDROID_HOME
make android-build        # android/app/build/outputs/apk/debug/app-debug.apk
make android-install      # install on a running emulator/device
```

Test Mode is enabled in debug builds. A release build disables it unless built with
`./gradlew assembleRelease -PenableTestMode=true`.

## Running iOS

Requirements: macOS, Xcode 16+ with an iOS 17+ simulator, the Xcode licence accepted.

```bash
open ios/MobileTestPlayground.xcodeproj    # run the MobileTestPlayground scheme
make ios-build
```

After adding or removing Swift files, regenerate the project with `make ios-project` (XcodeGen).

## Running tests

| Command | What |
|---|---|
| `make android-test` | JVM unit tests (domain, data, ViewModels) |
| `make android-lint` | ktlint, detekt, Android Lint |
| `make android-quality` | All of the above (what CI and Docker run) |
| `make android-ui-test` | Compose UI + UIAutomator tests on a running emulator/device |
| `make ios-test` | XCTest unit tests in the simulator |
| `make ios-ui-test` | XCUITest in the simulator |
| `make ios-lint` | SwiftLint (strict) |
| `make ios-typecheck` | swiftc type-check of all iOS sources (no xcodebuild needed) |
| `make ios-host-test` | iOS unit tests on the macOS host via SwiftPM (no simulator) |
| `make check-generated` | Strings and test IDs are in sync with their sources |
| `make backend-test` / `backend-lint` | Backend unit + integration tests / ktlint + detekt |
| `make api-test` | API automation against a running backend |

How to write tests against the app: [docs/automation.md](docs/automation.md).

## Docker Android build

Docker is used for reproducible Android builds, unit tests and static analysis — not for running an
emulator, and not for iOS.

```bash
make android-docker-build   # docker compose build android
make android-docker-test    # generated-file checks, qualityCheck and assembleDebug in the container
```

See [docs/build-environments.md](docs/build-environments.md).

## CI workflows

- **Android** (`.github/workflows/android.yml`): quality gate and APKs; Compose UI and backend
  integration tests on an API 34 emulator (KVM) with the backend running; the Docker image building an
  APK. UIAutomator tests run in a separate, **non-blocking** emulator job (see
  [Known limitations](#known-limitations)). Uploads APKs and reports.
- **iOS** (`.github/workflows/ios.yml`): macOS runner, SwiftLint, starts the backend JAR, build,
  unit tests (including the live-backend test), XCUITest. Uploads `.xcresult` bundles and the `.app`.
- **Backend and API** (`.github/workflows/backend.yml`): starts PostgreSQL + backend with Docker
  Compose, waits for health, runs backend lint and tests, runs the pytest suite, uploads reports.

Current GitHub results are in [Verification status](#verification-status).

## Test credentials

| Account | Email | Password | Role |
|---|---|---|---|
| Customer | `test@example.com` | `Password123!` | CUSTOMER |
| QA user | `qa@example.com` | `QaPassword123!` | CUSTOMER |
| Admin | `admin@example.com` | `Admin123!` | ADMIN |
| Locked | `locked@example.com` | `Locked123!` | CUSTOMER (always locked) |

Password reset code: `123456`. These are **local test credentials only**; the backend stores them as
PBKDF2 hashes. Full rules: [docs/test-credentials.md](docs/test-credentials.md).

## Test IDs

Every interactive and state-bearing element has a stable lowercase `snake_case` ID, identical on
both platforms. The catalogue lives in `TestTags.kt` (Android) and is generated into
`TestIDs.swift` (iOS) and the table in [docs/test-ids.md](docs/test-ids.md).

| Screen | Element | Automation ID |
|---|---|---|
| Login | Email | `login_email` |
| Login | Password | `login_password` |
| Login | Login button | `login_button` |
| Login | Error | `login_error` |
| Home | Search | `home_search` |
| Home | Scenario row | `scenario_<id>` |
| Catalog | Add to cart | `product_003_add_to_cart` |
| Product | Add to cart | `product_add_to_cart` |
| Cart | Checkout | `cart_checkout_button` |
| Admin | Dashboard | `admin_dashboard` |
| Authorization | Access denied | `access_denied_message` |

## Verification status

What was actually run on the development Mac (Android API 34 emulator; iPhone 17 simulator, iOS 26.5,
Xcode 27.1). "Not run" means not verified, not "failed".

| Item | Status |
|---|---|
| Backend builds; ktlint + detekt clean | ✅ |
| Backend unit + integration tests (13) | ✅ Passed (Ktor test engine, H2) |
| Backend runs (Gradle and fat JAR); `/health`, `/ready`, `/docs` | ✅ Verified on the host |
| API automation (81 tests) | ✅ Passed against the running backend (H2), also in reverse order |
| Backend on PostgreSQL via Docker Compose | ✅ `make backend-up` (image built, both health checks green, PostgreSQL 16); API automation 81/81 passed against it |
| Android debug APK, ktlint, detekt, Lint, JVM unit tests (domain 31, data 15, app 16) | ✅ Passed |
| Android instrumented: 35 Compose UI + 9 UIAutomator (offline mode) + 3 backend integration | ✅ 46/47 in the last full run; the one failure (a Test Mode reset bug it exposed) was fixed and its suite re-run green |
| Android ↔ backend end to end | ✅ `BackendIntegrationTest` passed on the emulator; server log confirms the calls |
| Android Docker image builds; APK built in the container | ✅ On GitHub Actions (ubuntu runner): image built, quality gate and APK inside the container |
| iOS app and test bundles build in Xcode | ✅ `xcodebuild build-for-testing`, iPhone 17 simulator |
| iOS unit tests (25, incl. live backend) | ✅ Passed in the simulator, and on the macOS host via SwiftPM |
| XCUITest (18, incl. `BackendUITests` against the live backend) | ✅ 18/18 passed in the simulator |
| SwiftLint | ✅ No violations |
| GitHub Actions: Backend and API tests | ✅ Passed on GitHub (PostgreSQL + backend in Compose, backend tests, 81 API tests) |
| GitHub Actions: iOS | ✅ Passed on GitHub (macOS runner: unit tests incl. live backend, 18 XCUITests) |
| GitHub Actions: Android build and Docker jobs | ✅ Passed on GitHub (lint, unit tests, APKs; APK built inside the Docker image). The image now pre-installs build-tools 36 (an earlier run failed on a corrupted download of it) |
| GitHub Actions: Android Compose UI + backend integration job (38 tests) | ✅ Passed in every GitHub run since the module fix |
| GitHub Actions: Android UIAutomator job (9 tests) | ⚠️ Non-blocking. Last GitHub run (4 cores / 4 GB): 0/9, because a system "Pixel Launcher isn't responding" dialog covered the app. The tests now tap "Wait" on such dialogs and wait for the accessibility tree after each scroll (both pass locally on cold-booted emulators); not yet run on GitHub. Earlier runs: 1–5 of 9 failed per run |

## Troubleshooting

| Problem | Fix |
|---|---|
| App shows "Cannot reach the server" | Start the backend (`make backend-run`), check the URL in Settings → Data source, or switch Test Mode → "Use backend API" off for offline mode. |
| Physical device cannot reach the backend | Use the computer's LAN IP (not localhost) in Test Mode, same Wi-Fi, and allow port 8080 in the firewall. |
| `SDK location not found` | Create `android/local.properties` with `sdk.dir=…` or set `ANDROID_HOME`. |
| `You have not agreed to the Xcode license agreements` | Run `sudo xcodebuild -license accept`. |
| Strings or IDs changed but builds use old ones | Run `make generate`; CI fails on stale generated files. |
| Instrumented tests leak state between classes | They should not: the orchestrator clears app data per test. Run through Gradle (`:app:connectedDebugAndroidTest`), not by launching classes manually from the device. |
| UIAutomator cannot find a Compose element | Check it has a test tag and is on screen (UIAutomator sees only rendered nodes; scroll first). |
| XCUITest finds stale data | Launch with `-uiTestReset`; the base class does this. |
| Docker build slow on Apple Silicon | Expected: the image is `linux/amd64` (AAPT2 has no arm64 Linux build) and runs under emulation. |
| Emulator rotation tests flaky | Turn off auto-rotate after a manual session: `adb shell settings put system user_rotation 0`. |

## Known limitations

- Android emulators do not run inside Docker here (no KVM on Docker Desktop); instrumented tests need a
  host emulator/device or a KVM CI runner.
- UIAutomator tests have been unreliable on GitHub's software-rendered emulator: failure dumps showed
  the app intermittently not updating (login stuck on "Signing in", clicks and scroll actions without
  effect) and a launcher "isn't responding" dialog over the app (the tests now dismiss it). Compose
  tests, whose rule drives the frame clock, are unaffected. Locally, the WebView test used to fail on a
  cold-booted emulator: the home list did scroll, but the accessibility tree reported the new position
  later than `waitForIdle` returned, so the scroll helper went past the card and back without seeing it.
  The helper now waits for the scroll event and for the element after each step, and all 9 pass on a
  cold-booted local emulator (two runs in a row). This has not yet run on GitHub, so CI still runs them
  in a separate job that reports without blocking.
- The backend's test-support endpoints (`/api/v1/test/*`) must be disabled (`TEST_SUPPORT_ENABLED=false`)
  outside local development and CI.
- Login reports unknown users and wrong passwords with different codes, deliberately, for testability.
- The offline/test mode keeps catalog/cart/auth local; the Admin screens in the apps show seed data
  and do not call the admin API (which is exercised by the API tests).
- iOS requires macOS and Xcode; there is no Linux build.
- Registered accounts and changed passwords are per-process by design (each launch starts from the seed).
- Android scheduled notifications fire only while the process is alive (no WorkManager).
- iOS reports any permission refusal as "Denied permanently" because iOS never asks twice.
- iOS AutoFill shows "Save Password?" after login in the simulator; XCUITest dismisses it.
- Remember Me across a real process restart is covered by unit tests on both platforms, not by an
  instrumented test (Android instrumentation shares the app process).
