# Family Guard — Transparent Parental Monitoring

A two-app parental-monitoring system for a parent to see what their **minor child**
does on an Android phone, with the reports viewed from the parent's iPhone (or any
browser). It is built to be **transparent and consent-based** — it stays visible on
the child's phone and shows a permanent notification. It deliberately does **not**
hide itself, and it does **not** read message content or use the camera/microphone.

> ⚠️ **Use only on a device you are legally responsible for** (e.g. your own minor
> child's phone). Secretly monitoring another adult's phone — a partner, employee, or
> any adult who has not consented — is illegal in most countries and is not what this
> project is for. See [`docs/CONSENT.md`](docs/CONSENT.md).

---

## What it does

| Feature | Child (Android) | Shown to parent |
| --- | --- | --- |
| **App usage & screen time** | `UsageStatsManager`, per-app foreground time per day | Today's total, most-used apps, 7-day breakdown |
| **Web browsing history** | Address-bar capture via Accessibility service | URLs + host + time, last 7 days |
| **Location** | Fused location, sampled every ~5 min | Last location + 3-day history on a map |

## What it does **not** do (by design)

- ❌ No message/SMS/chat **content**, and no reading of notification text
- ❌ No camera, screen, or microphone capture / live streaming
- ❌ No hiding the app icon or the notification
- ❌ No cloud server — data never leaves your devices

These are the lines that separate lawful parental monitoring from **stalkerware**.
They are intentional and are not a "missing feature."

---

## Architecture (no cloud — phone-to-phone over Wi-Fi)

```
┌─────────────────────────────┐          same Wi-Fi           ┌──────────────────────────┐
│  Child phone (Android APK)  │  ◄───────────────────────►    │  Parent phone (iPhone)   │
│                             │   http://<child-ip>:8766      │                          │
│  • Collectors (usage/web/   │   PIN → bearer token          │  • SwiftUI app OR        │
│    location) → Room DB      │   JSON over local network     │    Safari dashboard      │
│  • Foreground service +     │                               │  • Pulls reports, renders│
│    permanent notification   │                               │                          │
│  • NanoHTTPD local server   │                               │                          │
└─────────────────────────────┘                               └──────────────────────────┘
```

Because there is no cloud, the parent sees data **only when both phones are on the
same Wi-Fi**. Data is buffered on the child's phone (30-day retention) and is
available next time they're on the same network.

## Repository layout

```
android-child-app/     Android (Kotlin, Jetpack Compose) — install on the CHILD's phone
ios-parent-app/        iOS (SwiftUI) — install on the PARENT's iPhone
docs/                  Build, install, test, and consent documentation
```

## Quick start

1. **Build & install the Android app** on the child's phone → [`docs/BUILD_ANDROID.md`](docs/BUILD_ANDROID.md)
2. **Set it up** (consent, PIN, grant permissions) → [`docs/INSTALL_AND_SETUP.md`](docs/INSTALL_AND_SETUP.md)
3. **View reports** from your iPhone — either open the address shown on the child's
   phone in **Safari**, or build the native app → [`docs/BUILD_IOS.md`](docs/BUILD_IOS.md)
4. **Verify everything** with the [`docs/TEST_CHECKLIST.md`](docs/TEST_CHECKLIST.md)

## Build/verification status

- **Android:** ✅ **compiles and packages cleanly** — a debug APK
  (`com.familyguard.child.debug`, 18 MB) was built with the Android SDK (compileSdk 35,
  build-tools 35.0.0) via `./gradlew assembleDebug`. Kotlin compile, KSP/Room processing,
  resources, dexing, and signing all pass. Opens the same in Android Studio.
- **iOS:** complete SwiftUI source + `project.yml`. iOS can only be built on macOS
  with Xcode, so it is delivered as source for you to build.
- On-device behavior (permissions, background service, LAN sync) still **must be
  verified on real hardware** using `docs/TEST_CHECKLIST.md` — a clean compile proves
  the code is valid, not that every runtime permission flow behaves on your phone.
