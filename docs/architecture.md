# Architecture

Mobile Test Playground is a backend REST API (Kotlin, Ktor) and two native apps — Android (Kotlin,
Jetpack Compose) and iOS (Swift, SwiftUI) — built to be automated.

```text
Android app ─┐
             ├── Backend REST API ── Database
iOS app ─────┘
                         ↑
                 API automation tests
```

The backend's own layering is described in [backend.md](backend.md). This document covers the apps. Every decision below favours **determinism** (the same input
produces the same screen on every run) and **testability** (business rules run without a device).

## Layers

```text
Presentation   Compose screens + ViewModels       SwiftUI views + @Observable models
     ↓
Domain         pure rules, use cases, interfaces  (same rules, mirrored in Swift)
     ↓
Data           repositories → data sources:        repositories → data sources:
               backend API (Retrofit) or offline    backend API (URLSession) or offline
               (DataStore, Room, seed data)         (UserDefaults, seed data)
```

Dependencies point downwards only. The domain never imports a platform UI or storage framework.

### Android modules

| Module | Kind | Contents |
|---|---|---|
| `:core:domain` | Kotlin/JVM library | Models, seed data, validators, use cases, `AccessPolicy`, `DeepLinkParser`, network result types, repository interfaces. It cannot see the Android SDK, so the dependency rule is enforced by the build. |
| `:core:data` | Android library | Repository implementations, DataStore (`AppStorage`), Room (dynamic list), Retrofit `PlaygroundApi` over the local `ScenarioInterceptor`, Hilt `DataModule`, `AppInitializer`. |
| `:core:ui` | Android library | Material 3 theme, shared composables, and `TestTags` — the automation-ID catalogue. |
| `:app` | Application | Single activity, navigation, feature screens and ViewModels, Hilt `AppModule`, instrumented tests. |

### iOS targets

| Folder | Contents |
|---|---|
| `Domain/` | Models, seed data, validators, use cases, rules (Foundation only). |
| `Data/` | `KeyValueStorage` (UserDefaults / in-memory), repositories, `URLSessionNetworkClient` + `ScenarioURLProtocol`. |
| `App/` | `AppEnvironment` (the dependency container), `Router`, app entry, root and tab views. |
| `UI/` | `TestIDs` (generated), components, screens. |

`Domain/` and `Data/` use only Foundation and Observation, which is why `tools/ios-host-tests.sh`
can run the iOS unit tests on the macOS host without a simulator.

## Patterns

- **MVVM.** Screens are stateless functions of immutable UI state; ViewModels expose `StateFlow`
  (Android) or `@Observable` properties (iOS). No business logic in composables or views.
- **Use cases** encapsulate rules that combine validation and data (`LoginUseCase`,
  `RegisterUseCase`, `ResetPasswordUseCase`, `CheckoutUseCase`, `GenerateTestDataUseCase`,
  `ResetApplicationStateUseCase`).
- **Repository pattern with dependency inversion.** Interfaces live in the domain; implementations
  in data; Hilt (Android) and `AppEnvironment` (iOS) wire them.
- **Sealed results.** `LoginResult`, `NetworkResult`, `AccessDecision`, `NetworkUiState`, etc. make
  every outcome explicit and exhaustively handled.
- **One authorization rule.** `AccessPolicy.decide(level, session, authenticationRequired)` is the
  only place access is decided. Navigation and deep links both pass through it.

## Backend mode and the offline/test mode

Authentication, catalog, cart and checkout go through repositories with two data sources:

| Domain interface | Backend data source | Offline/test mode |
|---|---|---|
| `AuthRepository` | `RemoteAuthDataSource` (`/api/v1/auth/*`) | `LocalAccountDirectory` (seed accounts) |
| `ProductRepository` | `RemoteProductRepository` (`/api/v1/products`) | `SeedProductRepository` |
| `CartRepository` (incl. checkout) | `RemoteCartRepository` (`/api/v1/cart`, `/api/v1/orders`) | `InMemoryCartRepository` |

A switching repository picks the source per call from Test Mode's `useBackend` setting (default
from the build: `-PuseBackend`, iOS `USE_BACKEND_BY_DEFAULT`). The backend token is held by the auth
repository and persisted only with a remembered session. Failures map to a shared `FailureReason`
(`UNAVAILABLE`, `UNAUTHORIZED`, `NOT_FOUND`, `CONFLICT`, `UNKNOWN`) that ViewModels turn into
loading/error states. HTTP never happens in UI code.

The instrumented and XCUITest UI suites run in the offline mode (a Hilt test module on Android,
`-offlineMode` on iOS) so they need no backend; `BackendIntegrationTest` (Android) and
`BackendUITests` / `LiveBackendTests` (iOS) run against the real backend.

## Navigation and authorization

Android uses Navigation Compose with string routes. Each route has an `AccessLevel`
(`PUBLIC`, `AUTHENTICATED`, `ADMIN`). Every protected destination renders through a guard:

- **Granted** → the screen.
- **Denied** (signed in, wrong role) → the access-denied screen in place (`access_denied_message`).
- **RequiresLogin** → the host navigates to login with a `redirect` argument; after a successful
  login the original destination opens.

Logging out (or a Test Mode reset) always lands on a plain login screen with an empty back stack.
Access state is provided through a `CompositionLocal`, so a session change recomposes destinations
without rebuilding the navigation graph.

iOS mirrors the same rule with a `Router` (tab + `NavigationStack` path) and a `RouteView` that
evaluates `AccessPolicy` for each pushed route.

Deep links (`mtp://playground/...`) are parsed by `DeepLinkParser` (identical rules on both
platforms, unit tested on both) and routed through the same guard.

## Determinism

| Source of variation | How it is removed |
|---|---|
| Data | `SeedData` on both platforms: four accounts, 20 products, 30 static items, 5 dynamic items, 100 paged rows. |
| Backend | Seeded identically to the apps; `POST /api/v1/test/reset` restores it (tests call it before each test). |
| Network scenario | The Network playground screen simulates outcomes locally (an OkHttp interceptor / a `URLProtocol`), through the real Retrofit / URLSession decoding paths, so every error can be produced on demand. |
| Time | Loading delays are fixed constants (`UiTiming`); the Date & Time scenario compares against a fixed reference date (2025-01-15). |
| Order numbers | Sequential per process (`ORDER-0001`, …). |
| Identifiers | Fixed tags; repeated items use their data ids (`product_003`, `dynamic_list_item_4`). |
| State between tests | Android: the test orchestrator clears app data before every test. iOS: `-uiTestReset` wipes storage at launch. Both platforms offer Test Mode reset. |

## Persistence and Remember Me

| Data | Android | iOS | Survives process restart | Cleared by |
|---|---|---|---|---|
| Remembered session | DataStore | UserDefaults suite | Yes, if Remember Me was on | Logout, Test Mode reset/clear, clearing app data |
| Remembered email (prefill) | DataStore | UserDefaults | Yes | Test Mode reset/clear, clearing app data (not logout) |
| Non-remembered session | memory | memory | **No** | process death |
| Test Mode switches, theme | DataStore | UserDefaults | Yes | Test Mode reset/clear |
| Dynamic list items | Room | UserDefaults (JSON) | Yes | Reset test data / Test Mode |
| Cart, order counter | memory | memory | No | process death, reset |
| Registered accounts, changed passwords | memory | memory | No (by design: every launch starts from the seed) | process death, reset |

*Process restart* (the system kills the app, or `am force-stop`) keeps persisted data.
*Clearing app data* (`adb shell pm clear`, deleting the iOS app, or Test Mode "Clear local
storage") removes everything.

ViewModel state that must survive rotation and process death (form fields, control values, the
navigation counter) is held in `SavedStateHandle` on Android and `@SceneStorage` on iOS.

## Test Mode gating

Destructive tools (reset application state, clear local storage, auth bypass) are compiled in only
when enabled:

- Android: `BuildConfig.ENABLE_TEST_MODE` is `true` for debug, `false` for release unless built with
  `-PenableTestMode=true`. The ViewModel checks the flag too, so a hidden button can never act.
- iOS: the `TEST_MODE` compilation condition is set for the Debug configuration only.

## Localization

All user-facing strings come from one table, `shared/strings/strings.json` (English and German).
`tools/generate_strings.py` writes Android `strings.xml` (values, values-de) and iOS
`Localizable.strings` / `.stringsdict`. CI fails if the generated files are stale. Automation IDs are
never localized.

## Dependency budget (Android)

| Dependency | Why |
|---|---|
| Compose BOM, Material 3, Navigation Compose, Lifecycle | Required UI stack. |
| Hilt | Dependency injection, including in instrumented tests. |
| Coroutines / Flow | Asynchrony and observable state. |
| DataStore | Session, preferences, Test Mode. |
| Room | Persisted, queryable dynamic list (demonstrates a real database layer). |
| Retrofit, OkHttp, kotlinx.serialization | Real HTTP client stack over a local interceptor. |
| JUnit, kotlin-test, Turbine, coroutines-test | JVM tests. |
| Compose UI Test, UIAutomator, AndroidX Test, Orchestrator, Hilt testing | Instrumented tests. |
| ktlint, detekt | Formatting and static analysis. |

iOS uses only Apple frameworks (SwiftUI, Observation, WebKit, PhotosUI, UserNotifications,
AVFoundation, CoreLocation, XCTest). XcodeGen and SwiftLint are build-time tools.

## Documented exceptions

- **WebView on Android** is the one classic `View`, hosted with `AndroidView`, because Compose has no
  web view. It has a View id (`<package>:id/webview`) for UIAutomator. iOS wraps `WKWebView` with
  `UIViewRepresentable` for the same reason.
- **XML on Android** is limited to the manifest, the window theme, the launcher icon, backup rules
  and one View id — all platform-required.
- **Scheduled notifications on Android** use an app-scoped coroutine (they fire while the process is
  alive), which is deterministic for UI tests; a production app would use WorkManager or AlarmManager.
