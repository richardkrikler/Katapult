# Testing a local build on a Mudita Kompakt

## Why the debug build

`./gradlew installDebug` installs `com.gezimos.katapult.debug`, which sits **next to** the release.
The installed release keeps its settings and Obtainium keeps tracking it.
A locally built release can't replace the installed one: it is signed with a different key, so Android would make you uninstall first and you'd lose your settings.
The debug app has its own settings, so it starts with onboarding.

## 1. Connect

1. On the Kompakt, go to Settings → About and tap the build number to unlock Developer options.
   Turn on USB debugging and accept the RSA prompt.
   Close Mudita Center, because it holds adb.
2. `installDebug` installs on **every** connected device, including a running emulator, so pin it to the phone:

```sh
adb devices
export ANDROID_SERIAL=<serial>
./gradlew installDebug               # re-run after each change; keeps the debug app's settings
```

## 2. Swap the release out, the debug build in

Only one Katapult may run the Action Service. With two, the widgets and overlays stack.

```sh
P=com.gezimos.katapult.debug
# Other apps (e.g. a password manager) may be in this list too: replace only Katapult's entry.
adb shell settings get secure enabled_accessibility_services
adb shell settings put secure enabled_accessibility_services "$P/com.gezimos.katapult.lockscreen.LockscreenWidgetService:<others>"
adb shell cmd notification allow_listener $P/com.gezimos.katapult.service.NotificationListener
adb shell pm grant $P android.permission.READ_SMS          # Mudita Messages badge
adb shell pm grant $P android.permission.READ_CALL_LOG     # Mudita Phone badge
adb shell cmd package set-home-activity $P/com.gezimos.katapult.MainActivity
```

To test the lockscreen widget, **set a PIN**. Without one, MuditaOS has no lock screen and waking the phone goes straight to the launcher.

## 3. Back to the release

```sh
adb uninstall com.gezimos.katapult.debug
adb shell settings put secure enabled_accessibility_services "com.gezimos.katapult/com.gezimos.katapult.lockscreen.LockscreenWidgetService:<others>"
adb shell cmd package set-home-activity com.gezimos.katapult/com.gezimos.katapult.MainActivity
adb shell settings get secure enabled_notification_listeners   # should list com.gezimos.katapult
```

## Useful while testing

```sh
adb logcat | grep -E 'START u0|dismiss Keyguard'                  # what opened on tap
# --noredact prints message text: don't save the output.
adb shell dumpsys notification --noredact | grep -E 'pkg=com.mudita.(messages|dial)'
```

## Kompakt quirks

- Mudita Messages posts a notification for each SMS (visibility PRIVATE).
- Mudita Dial posts **no** missed-call notification. It does handle the standard call-log intent (`VIEW`, type `vnd.android.cursor.dir/calls`).
- `NotificationOpenActivity` must stay opaque. A translucent one doesn't cover the keyguard, which cancels `requestDismissKeyguard`.
