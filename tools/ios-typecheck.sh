#!/usr/bin/env bash
# Type-checks the iOS app, unit-test and UI-test sources with the Simulator SDK by calling swiftc
# directly. Useful where xcodebuild is unavailable (for example before the Xcode licence is
# accepted). It does not link, sign or run anything; full verification is `make ios-test`.
set -euo pipefail
cd "$(dirname "$0")/../ios"
DEV="$(xcode-select -p 2>/dev/null || echo /Applications/Xcode.app/Contents/Developer)"
SWIFTC="$DEV/Toolchains/XcodeDefault.xctoolchain/usr/bin/swiftc"
PLATFORM="$DEV/Platforms/iPhoneSimulator.platform"
SDK="$PLATFORM/Developer/SDKs/iPhoneSimulator.sdk"
TARGET="$(uname -m)-apple-ios17.0-simulator"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

APP_SOURCES=()
while IFS= read -r file; do APP_SOURCES+=("$file"); done < <(find MobileTestPlayground -name '*.swift' | sort)

# 1. App module (also emitted so the tests can `@testable import` it).
"$SWIFTC" -emit-module -parse-as-library -enable-testing -module-name MobileTestPlayground \
  -sdk "$SDK" -target "$TARGET" -swift-version 5 -D DEBUG -D TEST_MODE \
  -emit-module-path "$OUT/MobileTestPlayground.swiftmodule" "${APP_SOURCES[@]}"
echo "app: type-check OK"

# 2. Test targets against XCTest.
TEST_FLAGS=(-typecheck -sdk "$SDK" -target "$TARGET" -swift-version 5 -I "$OUT"
  -F "$PLATFORM/Developer/Library/Frameworks" -I "$PLATFORM/Developer/usr/lib")
"$SWIFTC" "${TEST_FLAGS[@]}" MobileTestPlaygroundTests/*.swift
echo "unit tests: type-check OK"
"$SWIFTC" "${TEST_FLAGS[@]}" MobileTestPlaygroundUITests/*.swift
echo "UI tests: type-check OK"
