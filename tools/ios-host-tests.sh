#!/usr/bin/env bash
# Runs the iOS unit tests (MobileTestPlaygroundTests) on the macOS host with SwiftPM.
#
# The tested code (Domain/, Data/ and App/AppEnvironment.swift) uses only Foundation and
# Observation, so it compiles for macOS. This script assembles a throwaway Swift package from those
# files and the unit tests, and runs `swift test` using the Xcode toolchain directly.
#
# It is a fast check that does not need a simulator (and works before the Xcode licence is
# accepted). It does NOT replace the real XCTest run in the iOS Simulator: `make ios-test`.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV="$(xcode-select -p 2>/dev/null || echo /Applications/Xcode.app/Contents/Developer)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$WORK/Sources/MobileTestPlayground" "$WORK/Tests/MobileTestPlaygroundTests"
cp "$ROOT"/ios/MobileTestPlayground/Domain/*.swift "$ROOT"/ios/MobileTestPlayground/Data/*.swift \
   "$ROOT"/ios/MobileTestPlayground/App/AppEnvironment.swift "$WORK/Sources/MobileTestPlayground/"
cp "$ROOT"/ios/MobileTestPlaygroundTests/*.swift "$WORK/Tests/MobileTestPlaygroundTests/"
cat > "$WORK/Package.swift" <<'SWIFT'
// swift-tools-version:5.9
import PackageDescription
let package = Package(
    name: "MobileTestPlayground",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "MobileTestPlayground", swiftSettings: [.define("DEBUG"), .define("TEST_MODE")]),
        .testTarget(name: "MobileTestPlaygroundTests", dependencies: ["MobileTestPlayground"]),
    ]
)
SWIFT
cd "$WORK"
DEVELOPER_DIR="$DEV" "$DEV/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift" test
