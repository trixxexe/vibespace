# VibeSpace Development Setup Guide

## Prerequisites

- **JDK**: OpenJDK 17 or higher (JDK 17 LTS recommended)
- **Android Studio**: Android Studio Koala / Ladybug or newer
- **Android SDK**:
  - `compileSdk`: 35
  - `minSdk`: 26 (Android 8.0 Oreo)
  - `targetSdk`: 35
- **Gradle**: 8.10.2 (handled automatically via `./gradlew`)

---

## 1. Quick Start

Clone the repository:
```bash
git clone https://github.com/vibespace/vibespace.git
cd vibespace
```

Validate Gradle wrapper:
```bash
./gradlew --version
```

---

## 2. Running Verification & CI Checks

Run all module unit tests:
```bash
./gradlew testDebugUnitTest
```

Run Android Lint checks:
```bash
./gradlew lintDebug
```

Build the debug APK:
```bash
./gradlew assembleDebug
```
The output APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 3. GitHub Cloud CI Build

All pull requests and commits pushed to `main` or `feature/**` trigger the `.github/workflows/android-ci.yml` workflow on GitHub Cloud Actions.

The CI runner:
1. Validates the Gradle wrapper checksum.
2. Compiles every core and feature module with JDK 17.
3. Executes all unit test suites.
4. Performs Android lint verification.
5. Builds and archives the debug APK for download.
