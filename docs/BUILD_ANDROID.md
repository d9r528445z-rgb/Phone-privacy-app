# Build the Android (child) app

## Option A — Android Studio (easiest)

1. Install **Android Studio** (Giraffe 2023.3+ / latest is fine).
2. `File → Open…` and select the `android-child-app/` folder.
3. Let Gradle sync (it downloads the Android Gradle Plugin, Compose, Room, etc.).
4. Plug in the child's phone with **USB debugging** on, or use an emulator.
5. Press **Run ▶** (or `Build → Build Bundle(s)/APK(s) → Build APK(s)`).
6. The debug APK lands in `android-child-app/app/build/outputs/apk/debug/app-debug.apk`.

## Option B — Command line

Requires the Android SDK installed and `ANDROID_HOME`/`local.properties` pointing at it.

```bash
cd android-child-app
# create local.properties if Android Studio hasn't:
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # adjust path

./gradlew assembleDebug        # debug APK
# or a signed release:
./gradlew assembleRelease
```

Install on the phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Signing a release APK (for sideloading long-term)

Debug APKs expire-ish and are fine for testing. For a stable install, sign a release:

```bash
keytool -genkey -v -keystore familyguard.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias familyguard
```

Add to `android-child-app/app/build.gradle.kts` inside `android { }`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../familyguard.jks")
        storePassword = System.getenv("FG_STORE_PW")
        keyAlias = "familyguard"
        keyPassword = System.getenv("FG_KEY_PW")
    }
}
buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
```

Then `./gradlew assembleRelease` and install the APK from
`app/build/outputs/apk/release/`.

> Keep `familyguard.jks` and its passwords private. They are already git-ignored.

## Requirements / versions

- `compileSdk`/`targetSdk` 35, `minSdk` 26 (Android 8.0+)
- JDK 17 (Android Studio bundles one)
- Kotlin 2.0.21, AGP 8.7.3 — compatible with Gradle 8.9+ (wrapper pins 8.14.3)

## Note on the delivery environment

This project was authored in a Linux cloud container where the egress proxy **blocked
`dl.google.com` and Google's Maven**, so the Android SDK could not be installed and the
APK could not be compiled there. The code is complete and self-consistent; the first
build happens on your machine in Android Studio (which fetches those dependencies
normally). If you hit a dependency/version error on first sync, let Android Studio
apply its suggested AGP/Kotlin upgrade — the code uses stable, current APIs.
