# Build environments

## Android

### Mode 1 — host emulator, Docker for builds (recommended)

| Task | Where |
|---|---|
| Unit tests, ktlint, detekt, Android Lint, debug APK | Host Gradle **or** Docker |
| Compose UI and UIAutomator tests | Host emulator or a USB device (`./gradlew :app:connectedDebugAndroidTest`) |

Host requirements: JDK 17+ (the project compiles to Java 17 bytecode; JDK 21 also works), Android SDK
with platform 35 and build-tools 35, an emulator image (API 26+; tests are verified on API 34).
Point Gradle at the SDK with `android/local.properties` (`sdk.dir=...`) or `ANDROID_HOME`.

Docker:

```bash
docker compose build android      # make android-docker-build
docker compose run --rm android   # make android-docker-test: generated-file checks, qualityCheck, assembleDebug
docker compose run --rm android ./gradlew --no-daemon :core:domain:test
```

The image (`docker/android/Dockerfile`) contains Temurin JDK 17, Android command-line tools, platform
35, build-tools 35 and platform-tools. The repository is bind-mounted, so the APK appears at
`android/app/build/outputs/apk/debug/app-debug.apk` on the host. Gradle caches persist in the
`gradle-cache` volume. A container-specific `local.properties` is mounted over the host's so a host
SDK path never leaks into the container.

The image is `linux/amd64` because AAPT2 ships only as an x86-64 Linux binary; on Apple Silicon it
runs under Rosetta and is slower than a native host build.

On a Linux host the bind mount keeps host file ownership, and the container's `builder` user usually
has a different UID, so Gradle cannot create `android/.gradle` ("Cannot create directory"). Make the
checkout writable first (`chmod -R a+rwX .`, as CI does). Docker Desktop on macOS does not have this
problem.

### Mode 2 — emulator inside Docker

**Not provided.** An emulator needs hardware virtualisation (`/dev/kvm`), which Docker Desktop on
macOS and Windows does not expose. A KVM-enabled Linux host could run one, but that setup has not
been built or tested here, so it is not claimed. CI runs instrumented tests on a KVM-enabled GitHub
Ubuntu runner with `reactivecircus/android-emulator-runner` instead.

## Backend

Runs anywhere with JDK 17+ (`make backend-run`), or in Docker: `docker compose up --build -d backend`
starts PostgreSQL 16 and the backend image (`docker/backend/Dockerfile`, multi-stage JDK build → JRE
runtime) with health checks on both, published on port 8080. The API tests need Python 3.10+
(`make api-test-setup`). See [backend.md](backend.md).

## iOS

iOS apps build only on macOS with Xcode. There is no Linux Docker image for iOS and none is claimed.

Requirements: macOS with Xcode 16 or newer (verified: Xcode 27.1 with an iPhone 17 / iOS 26.5
simulator), an iOS 17+ simulator, and the Xcode licence accepted (`sudo xcodebuild -license accept`).
Optional: XcodeGen (to regenerate the project from `ios/project.yml`) and SwiftLint.

```bash
make ios-project     # xcodegen generate (the generated project is committed)
make ios-lint        # swiftlint lint --strict
make ios-build
make ios-test        # XCTest in the simulator
make ios-ui-test     # XCUITest in the simulator
```

Checks that work without `xcodebuild` (for example before the licence is accepted, or for fast
feedback):

```bash
make ios-typecheck   # swiftc -typecheck of app, unit-test and UI-test sources against the Simulator SDK
make ios-host-test   # runs the XCTest unit tests on the macOS host through SwiftPM
```

`ios-host-test` works because `Domain/`, `Data/` and `AppEnvironment.swift` depend only on
Foundation and Observation. It complements, and does not replace, the simulator run.

## CI

| Workflow | Runner | Steps |
|---|---|---|
| `.github/workflows/android.yml` | ubuntu-latest | Generated-file check, ktlint, detekt, Android Lint, unit tests, debug + test APKs, artifacts; emulator job (API 34, KVM) with the backend started via Compose, running Compose UI, UIAutomator and backend integration tests; Docker job building the image and an APK inside it |
| `.github/workflows/ios.yml` | macos-15 | Select newest Xcode, generated-file check, XcodeGen, SwiftLint, start the backend JAR, build-for-testing, unit tests (incl. live backend), XCUITest, upload `.xcresult` and `.app` |
| `.github/workflows/backend.yml` | ubuntu-latest | Compose up PostgreSQL + backend and wait for health, backend lint + tests, pytest API suite, reports, compose down |

The workflow files exist in the repository; whether they pass on GitHub's hosted runners is only
known once they run there.
