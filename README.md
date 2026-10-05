# Pawse

A focus mode for Android: chosen apps are covered until you scan an NFC tag,
which is meant to live inside a plushie. Scan it to start, scan it to stop.

`SPEC.md` is the specification; its section 9 records the decisions, section
10 what the phone has answered, and section 11 the order of work. Milestones 1
to 4 are built: a tag starts and ends a session, a session covers the apps on
the block list, and the home screen says what is missing before one can start.

## Building

Everything runs inside the Nix shell, which provides the JDK and the Android
SDK. Gradle is run through `nix-gradlew`, as explained in Dev-kit's
`docs/android-on-nixos.md`.

```sh
nix develop
./nix-gradlew :app:testDebugUnitTest      # the unit tests
./nix-gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then switch Pawse on under Settings, Accessibility, Downloaded apps. On
Android 13 and later a sideloaded app is refused there at first: open its
app-info page, and choose "Allow restricted settings" from the menu.

Do not check the app with `uiautomator dump`: it unbinds every accessibility
service on the phone while it runs, Pawse's included. Use screenshots, the
log (`adb logcat -s Pawse`) and the stored state
(`adb shell run-as uk.pixxula.pawse cat shared_prefs/session.xml`).
