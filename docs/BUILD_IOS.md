# Build the iOS (parent) app

You need a **Mac with Xcode 15+**. iOS apps cannot be built on Linux/Windows.

## Option A — Generate the project with XcodeGen (recommended)

```bash
brew install xcodegen
cd ios-parent-app
xcodegen generate          # creates FamilyGuardParent.xcodeproj from project.yml
open FamilyGuardParent.xcodeproj
```

Then in Xcode:
1. Select the **FamilyGuardParent** target → **Signing & Capabilities** → pick your
   Apple ID / team (a free personal team is fine for installing on your own iPhone).
2. Choose your iPhone as the run destination and press **Run ▶**.
3. On the iPhone, trust the developer profile:
   **Settings → General → VPN & Device Management → (your Apple ID) → Trust**.

## Option B — Create the project by hand in Xcode

1. Xcode → **New → App**, Interface **SwiftUI**, name it `FamilyGuardParent`.
2. Delete the auto-created `ContentView.swift`.
3. Drag every file from `ios-parent-app/FamilyGuardParent/` into the project
   (check "Copy items if needed"): `FamilyGuardParentApp.swift`, `RootView` is inside
   it, `Models.swift`, `ApiClient.swift`, `LoginView.swift`, `DashboardView.swift`,
   `UsageView.swift`, `WebHistoryView.swift`, `LocationView.swift`.
4. Open the provided `Info.plist` and copy these keys into the target's Info settings
   (or set `INFOPLIST_FILE` to this file):
   - `NSLocalNetworkUsageDescription` — **required**; iOS needs it to reach the LAN.
   - `NSAppTransportSecurity → NSAllowsLocalNetworking = YES` — allows plain HTTP to
     the child's phone on the local network.
5. Deployment target **iOS 17+** (uses the newer `Map` API and `ContentUnavailableView`).

## First run

- The first time the app contacts the child's phone, iOS shows a **"allow local network
  access"** prompt — tap **Allow**, or it can't connect.
- Enter the address shown on the child's phone (e.g. `192.168.1.20:8766`) and your PIN.

## Don't have a Mac right now?

You don't need this app to use the system. The Android app already serves the **same
dashboard** as a web page. On your iPhone, open **Safari** and go to the address shown
on the child's phone (e.g. `http://192.168.1.20:8766`), enter the PIN, and you get the
full reports. The native app is just a nicer, installable version of that.
