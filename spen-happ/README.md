# S Pen → Happ VPN (Galaxy S24 Ultra / One UI)

## What it does
- Listens to Samsung's `com.samsung.pen.INSERT` broadcast with boolean `penInsert`.
- When pen is removed, toggles Happ OFF **only if a system VPN is currently active** and remembers that it tried to turn it off.
- When pen is reinserted, toggles Happ ON **only if it previously toggled it off and no VPN is currently active**.
- Uses `com.happproxy.action.widget.click` broadcast with package `com.happproxy`, not opening Happ.
- The selected server/profile stays in Happ; we do not change its configuration.
- Foreground service notification; auto-restart on boot; events log in UI.

## Limitations (important)
- These are Samsung/Happ undocumented broadcasts. Device/One UI/Happ variants could block them.
- Android NetworkCapabilities says if ANY VPN is active, not specifically Happ; use Happ as the only VPN.
- Happ's action is a toggle rather than separate start/stop, so if its state changes concurrently the operation can be out of sync. It is not an official supported integration.
- On initial service start, a sticky state initializes but is NOT treated as a pen-removal transition.
- On some Samsung versions broadcasts may only reach system apps; if the event log stays empty, inspect `adb logcat` or use Samsung Modes and Routines for S Pen trigger.
- Force stop prevents automatic boot/start until app is manually reopened.

## Build APK without Android Studio
1. Create a private GitHub repository and upload the **contents** of this folder (including `.github/workflows/build-apk.yml`).
2. Go to **Actions → Build Android APK → Run workflow**.
3. When completed, download the `SPenHapp-debug-apk` artifact and extract `app-debug.apk`.
4. Transfer APK to phone and install it (allow unknown app sources).

Or open in Android Studio (JDK 17, Android SDK 35) and choose **Build → Build APK(s)**.

## First run
1. Make sure Happ (package `com.happproxy`) is installed, VPN permission granted, desired server selected and connected once.
2. Open S Pen → Happ and tap **Включить автоматизацию**.
3. Grant notification permission and set battery usage to **Unrestricted** in App info.
4. Turn OFF 'Always-on VPN' or 'Block connections without VPN' in Android VPN settings if they prevent intentional disconnection.
5. Put pen in, activate VPN in Happ, remove pen, then reinsert. Verify actual VPN icon and the log.

The app uses no INTERNET permission; it does not receive your subscriptions, keys or traffic.
