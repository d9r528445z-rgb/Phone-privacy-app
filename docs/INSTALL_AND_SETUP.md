# Install & set up on the child's phone

## 1. Install the APK

- Build it (see `BUILD_ANDROID.md`) and either `adb install` it, or copy the APK to the
  phone and open it.
- To install a sideloaded APK you'll need to allow **"Install unknown apps"** for the
  app you're installing from (Files/Chrome). Android walks you through this.

### Play Protect warning ("harmful app") — expected, here's how to proceed

Google Play Protect flags this app based on its **permissions** (accessibility + usage
+ background location), not its behavior. **Every** monitoring app trips this when
sideloaded, including commercial parental-control apps. It is not a sign the app is
broken or malicious — Play Protect simply can't see that *you* consent to it.

To install your own app anyway:

1. When the warning appears, tap **"More details"** → **"Install anyway"**.
2. If you only see **OK/Cancel** (blocked outright): go to **Settings → Security (or
   "Security & privacy") → Google Play Protect → ⚙ Settings → turn OFF "Scan apps with
   Play Protect"**, install the APK, then turn it back on. Re-enabling it will **not**
   remove the already-installed app (it may show a one-time warning you can dismiss).
3. If after install you get a "harmful app" notification, open it and choose
   **"Keep app" / "Keep anyway"**.

> ⚠️ Only do this for apps you built/trust (like this one). Leaving Play Protect off
> weakens security for the whole phone, so turn it back on afterward.

### Android 13+: "Restricted settings" for the accessibility step

On Android 13 and newer, you can't enable an accessibility service for a sideloaded app
until you lift the restriction:

1. **Settings → Apps → Family Guard → ⋮ (top-right menu) → "Allow restricted settings"**.
2. Then enable the web-address logging service in Accessibility (step 2.3 below).

## 2. First-run setup (do this together with your child)

The app opens to a setup flow. Being open about it is part of using it lawfully and keeps
trust with your child.

1. **Consent screen** — read what is and isn't collected, tap **I understand**.
2. **Create a parent PIN** (4–10 digits). You'll type this on your own phone to see
   reports. It's stored only as an encrypted hash.
3. **Grant permissions** — the status screen lists each one with a **Grant** button:
   - **Usage access** → opens Settings; find *Family Guard* and turn it on. *(app usage)*
   - **Notifications** → tap Allow. *(the permanent "monitoring active" notice)*
   - **Location** → choose **Allow** then, when prompted, **Allow all the time**.
   - **Web-address logging (Accessibility)** → opens Accessibility settings; enable
     *Family Guard web-address logging*. *(browsing history)*

When Usage access + Notifications are on, monitoring is **Active** and a permanent
notification appears. The app stays visible in the app drawer — this is intentional.

## 3. Note the dashboard address

On the app's status screen you'll see something like:

```
Parent dashboard address
http://192.168.1.20:8766
```

That IP is the child's phone on your Wi-Fi. You'll use it from your phone.

## 4. View reports from your phone

Make sure **your phone is on the same Wi-Fi**, then either:

- **Safari / any browser:** open `http://192.168.1.20:8766`, enter your PIN. Or
- **Native iPhone app:** open Family Guard, enter `192.168.1.20:8766` and your PIN.

## Keeping it running reliably

Android aggressively kills background apps. For dependable monitoring:

- **Battery:** Settings → Apps → Family Guard → Battery → **Unrestricted**.
- **Autostart** (Xiaomi/Oppo/Vivo/Samsung skins): enable "Autostart"/"Auto-launch" for
  Family Guard so it restarts after reboot.
- The app also re-starts itself on boot and runs a foreground service to stay alive.

## Changing the server port

Default is `8766`. If it clashes with something, you can change `serverPort` in
`Prefs.kt` default, or expose a setting — the dashboard address updates automatically.
