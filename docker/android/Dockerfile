# Android build environment for Mobile Test Playground.
#
# Purpose: compile the app, run JVM unit tests, ktlint, detekt and Android Lint, and produce the debug
# APK on a machine that has only Docker. It is a BUILD environment and deliberately does not run an
# emulator (see the note at the end and docs/build-environments.md).
#
# --platform=linux/amd64 is required: the Android Gradle Plugin ships AAPT2 only as an x86-64 Linux
# binary, so on Apple Silicon the container runs under emulation (Rosetta in Docker Desktop).
FROM --platform=linux/amd64 eclipse-temurin:17-jdk-jammy

# Command-line tools 11076708 is the last revision distributed as a plain zip with sdkmanager.
# Platform matches compileSdk in android/gradle/libs.versions.toml; build-tools is the default of the
# Android Gradle Plugin in use (AGP 9.3: 36.0.0), so Gradle never downloads it during a build.
ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    CMDLINE_TOOLS_VERSION=11076708 \
    ANDROID_COMPILE_SDK=35 \
    ANDROID_BUILD_TOOLS=36.0.0 \
    GRADLE_USER_HOME=/home/builder/.gradle \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install --no-install-recommends -y \
        ca-certificates \
        curl \
        git \
        python3 \
        unzip \
    && rm -rf /var/lib/apt/lists/*

# Non-root, so files written into the bind-mounted repository are not owned by root on Linux hosts.
RUN useradd --create-home --shell /bin/bash builder

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

ENV PATH="${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools"

# Licences must be accepted non-interactively for an unattended build.
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-${ANDROID_COMPILE_SDK}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
    && chown -R builder:builder "${ANDROID_SDK_ROOT}"

USER builder
WORKDIR /workspace/android

# Warm the Gradle distribution into an image layer by copying only the wrapper, so source changes do
# not re-download Gradle.
COPY --chown=builder:builder android/gradle/wrapper/ /workspace/android/gradle/wrapper/
COPY --chown=builder:builder android/gradlew /workspace/android/
RUN ./gradlew --version > /dev/null

COPY --chown=builder:builder docker/scripts/ /usr/local/bin/

# Default: the full quality gate plus the debug APK. Override with any Gradle command, e.g.
#   docker compose run --rm android ./gradlew :core:domain:test
CMD ["android-ci.sh"]

# --- Emulator support ------------------------------------------------------------------------------
# Not configured and not tested, so not claimed. An emulator needs hardware acceleration (/dev/kvm on
# a Linux host with nested virtualisation), which Docker Desktop on macOS and Windows does not expose.
# Instrumented tests run on a host emulator/device or in CI on a KVM-enabled Linux runner.
