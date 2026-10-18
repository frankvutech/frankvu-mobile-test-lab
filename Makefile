# Mobile Test Playground — common commands. Run `make help` for the list.
ANDROID := cd android && ./gradlew
IOS_PROJECT := ios/MobileTestPlayground.xcodeproj
IOS_SCHEME := MobileTestPlayground
# Override with e.g. `make ios-test IOS_DESTINATION='platform=iOS Simulator,name=iPhone 17'`.
IOS_DESTINATION ?= platform=iOS Simulator,name=iPhone 17

.PHONY: help generate check-generated \
	backend-run backend-test backend-lint backend-jar backend-up backend-down api-test-setup api-test api-reset \
	android-build android-test android-lint android-quality android-ui-test android-install \
	android-docker-build android-docker-test android-docker-apk \
	ios-project ios-lint ios-typecheck ios-host-test ios-build ios-test ios-ui-test

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-22s %s\n", $$1, $$2}'

generate: ## Regenerate string resources and iOS test IDs from their sources
	python3 tools/generate_strings.py
	python3 tools/generate_test_ids.py

check-generated: ## Fail if generated files are out of date
	python3 tools/generate_strings.py --check
	python3 tools/generate_test_ids.py --check

# --- Backend API ---
backend-run: ## Run the backend on http://localhost:8080 (H2 file database in backend/data)
	cd backend && ./gradlew run

backend-test: ## Backend unit and integration tests
	cd backend && ./gradlew test

backend-lint: ## Backend ktlint and detekt
	cd backend && ./gradlew ktlintCheck detekt

backend-jar: ## Build backend/build/libs/backend.jar
	cd backend && ./gradlew buildFatJar

backend-up: ## Start PostgreSQL + backend in Docker and wait until healthy
	docker compose up --build --wait backend

backend-down: ## Stop the Docker services (data volume kept; add -v to drop it)
	docker compose down

api-test-setup: ## Create api-tests/.venv with httpx and pytest
	python3 -m venv api-tests/.venv && api-tests/.venv/bin/pip install -r api-tests/requirements.txt

api-test: ## API automation against API_BASE_URL (default http://localhost:8080)
	cd api-tests && .venv/bin/pytest

api-reset: ## Restore the backend's seed data
	curl -fsS -X POST $${API_BASE_URL:-http://localhost:8080}/api/v1/test/reset && echo "reset"

# --- Android (host) ---
android-build: ## Build the debug APK
	$(ANDROID) assembleDebug

android-test: ## JVM unit tests (all modules)
	$(ANDROID) :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest

android-lint: ## ktlint, detekt and Android Lint
	$(ANDROID) ktlintCheck detekt :app:lintDebug

android-quality: ## Everything CI runs without a device
	$(ANDROID) qualityCheck

android-ui-test: ## Compose UI + UIAutomator tests on a running emulator/device
	$(ANDROID) :app:connectedDebugAndroidTest

android-install: ## Install the debug APK on a running emulator/device
	$(ANDROID) installDebug

# --- Android (Docker) ---
android-docker-build: ## Build the Android build image
	docker compose build android

android-docker-test: ## Quality gate + unit tests + APK inside Docker
	docker compose run --rm android

android-docker-apk: ## Only the debug APK, inside Docker
	docker compose run --rm android ./gradlew --no-daemon assembleDebug

# --- iOS (macOS + Xcode) ---
ios-project: ## Regenerate the Xcode project from ios/project.yml
	cd ios && xcodegen generate

ios-lint: ## SwiftLint
	cd ios && swiftlint lint --strict

ios-typecheck: ## Type-check app and test sources with swiftc (no xcodebuild needed)
	tools/ios-typecheck.sh

ios-host-test: ## Run the iOS unit tests on the macOS host via SwiftPM (no simulator)
	tools/ios-host-tests.sh

ios-build: ## Build the app for the simulator
	xcodebuild build -project $(IOS_PROJECT) -scheme $(IOS_SCHEME) -destination '$(IOS_DESTINATION)'

ios-test: ## Unit tests in the simulator
	xcodebuild test -project $(IOS_PROJECT) -scheme $(IOS_SCHEME) -destination '$(IOS_DESTINATION)' -only-testing:MobileTestPlaygroundTests

ios-ui-test: ## XCUITest in the simulator
	xcodebuild test -project $(IOS_PROJECT) -scheme $(IOS_SCHEME) -destination '$(IOS_DESTINATION)' -only-testing:MobileTestPlaygroundUITests
