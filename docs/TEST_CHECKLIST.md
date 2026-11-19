# On-device test checklist

The build environment had no phones, so these must be run on real hardware (or an
Android emulator for most of the Android side; location/accessibility behave best on a
real device). Check each box; the "expected" column is what a correct build does.

## A. Install & setup

| # | Step | Expected |
|---|------|----------|
| A1 | Install APK, open app | Consent screen appears first |
| A2 | Tap "I understand" | Moves to PIN setup |
| A3 | Enter PIN `123`, confirm | Error: "PIN must be at least 4 digits" |
| A4 | Enter PIN `1234`, confirm `1235` | Error: "PINs do not match" |
| A5 | Enter matching PIN `1234` | Saves, goes to Home, monitoring tries to start |
| A6 | Reopen the app after setup | Opens directly to Home (consent/PIN not shown again) |

## B. Permissions

| # | Step | Expected |
|---|------|----------|
| B1 | Home shows permission cards | Each ungranted card has a "Grant" button + empty circle |
| B2 | Grant **Usage access** in Settings, return | Card flips to a filled check (auto-refresh on resume) |
| B3 | Grant **Notifications** | Permanent "Family Guard is active" notification appears |
| B4 | Grant **Location** → "Allow all the time" | Both location cards become checked |
| B5 | Enable **Accessibility** service, return | Web-logging card becomes checked |
| B6 | Deny a permission, reopen app | Card correctly shows ungranted again |

## C. Data collection

| # | Step | Expected |
|---|------|----------|
| C1 | Use a few apps (YouTube, a game) for a few minutes | After ≤15 min, usage shows in summary/usage |
| C2 | Check "most used today" ordering | Highest foreground time first; bar widths proportional |
| C3 | Open several websites in Chrome | Web history lists host + full URL + time (after a few sec) |
| C4 | Visit the same URL twice quickly | Not duplicated within the ~4 s de-dupe window |
| C5 | Walk/drive with the phone | New location fixes appear (every ~5 min / 25 m) |
| C6 | Reboot the phone | Monitoring restarts automatically; notification returns |
| C7 | Leave running overnight, check next day | Usage correctly bucketed per day (midnight split works) |

## D. Parent viewing (web dashboard)

| # | Step | Expected |
|---|------|----------|
| D1 | Same Wi-Fi, open `http://<child-ip>:8766` in a browser | Login page loads |
| D2 | Enter wrong PIN | "Incorrect PIN." error, no data shown |
| D3 | Enter correct PIN | Summary tab renders device name + screen time |
| D4 | Switch tabs: Usage / Web / Location | Each loads its data; Refresh re-pulls |
| D5 | Different Wi-Fi / mobile data | Page can't connect (expected — local only) |
| D6 | Leave tab open, come back after 12 h | Token expired → bounced to login (re-enter PIN) |

## E. Parent viewing (iOS app)

| # | Step | Expected |
|---|------|----------|
| E1 | First connect | iOS "allow local network" prompt → Allow |
| E2 | Enter `host:port` + PIN | Signs in, Summary tab shows data |
| E3 | Wrong host | Friendly "could not reach the phone… same Wi-Fi" message |
| E4 | Location tab | Map renders with markers; list below |
| E5 | Pull to refresh on each tab | Data reloads |
| E6 | Sign out (Summary) | Returns to login |

## F. Security / privacy

| # | Step | Expected |
|---|------|----------|
| F1 | Hit `/api/summary` with no token (curl) | `401 unauthorized` |
| F2 | Hit `/api/usage?token=garbage` | `401 unauthorized` |
| F3 | Inspect APK / device — is PIN stored plaintext? | No — only a salted SHA-256 hash in EncryptedSharedPreferences |
| F4 | Confirm no outbound internet calls | Only inbound LAN requests; no cloud host contacted (check with a network monitor) |
| F5 | `/api/status` (no auth) | Returns only app name/time/flags — no personal data |

## G. Lifecycle / robustness

| # | Step | Expected |
|---|------|----------|
| G1 | Swipe app from recents | Foreground service keeps running; notification stays |
| G2 | Turn Wi-Fi off then on | Server rebinds / reachable again when on Wi-Fi |
| G3 | Revoke Usage access while running | Summary shows the "Usage access is OFF" warning |
| G4 | Fill 30+ days of data (or change retention) | Old rows pruned by the aggregation worker |

## Quick API smoke test (from a computer on the same Wi-Fi)

```bash
IP=192.168.1.20; PORT=8766
curl -s http://$IP:$PORT/api/status
TOKEN=$(curl -s -XPOST http://$IP:$PORT/api/auth -H 'Content-Type: application/json' \
        -d '{"pin":"1234"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -s http://$IP:$PORT/api/summary -H "Authorization: Bearer $TOKEN"
curl -s "http://$IP:$PORT/api/usage?days=7" -H "Authorization: Bearer $TOKEN"
```
