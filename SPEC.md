---
id: Spec
aliases: []
tags: []
---

# Pawse — Specification

Started from the note in the vault (`Projects/Rust/Scrolly(App focus)/idea.md`):

> Scrolly like App, basically lets you enter a focus mode where apps are locked
> until you scan an nfc tag, which unlocks an app, it should block selected
> apps, basically drawing a layer on top until the nfc tag gets scanned.

The decisions that were open in the draft are settled and recorded in
section 9. Milestone 1 is built (section 11). What can only be found out on
the phone is listed in section 10, with what has been answered so far.

## 1. Overview

An Android app that makes chosen apps unusable during a **focus session**. A
blocked app is covered by a full-screen layer the moment it comes to the front.

The session is switched on and off by one thing only: scanning an **NFC tag**.
The tag is meant to live inside a plushie. Tap the phone on it and the paw
goes down on your apps; tap again and it lifts.

The point of the tag is friction. A confirmation dialog is one tap; a plushie
on the other side of the flat is a decision.

### Goals

* Block a chosen set of apps for the length of a session, reliably, including
  after the screen turns off, the app is swiped away, or the phone restarts.
* Make ending a session cost a walk to the tag. Nothing in the app ends one.
* Stay out of the way otherwise: no account, no server, no network permission,
  nothing leaving the phone.
* Reuse what HaBytes already worked out: NFC tags, native Kotlin that runs
  without the UI, background work that survives MIUI, signed releases.

### Non-goals

* iOS. Third-party apps cannot block other apps there outside Screen Time.
* Blocking websites, or content inside an app.
* Parental control: this is a tool you use on yourself, not on someone else.
* Being impossible to get around. Whoever goes into Settings to switch it off
  has decided to; the app does not fight that (section 4).
* Timers, schedules, and unlocking a single app. A session is on or off.
* Usage statistics, streaks, charts.
* A server, accounts, sync between devices, or any link to HaBytes.

## 2. How it works

```
   tag scanned ──► registered key? ──► toggle the session (on ⇄ off)

   an app comes to the front
              │
              ▼
   Watcher (accessibility service) ── is the session on, is this app on
              │                        the block list, and is it not in
              │ yes                    a call?
              ▼
   Cover: a full-screen layer over the app
   ("Pawsed. Boop your tag to end the session.")

   a blocked app posts a notification, or starts playing
              │
              ▼
   Hush (notification listener) ── session on? hide it, pause it
```

Four native parts, none of which needs the app's own screens to be open:

* **Watcher** — an accessibility service that knows which apps are on screen.
* **Cover** — what is shown instead of a blocked app.
* **Hush** — a notification listener that hides blocked apps' notifications,
  pauses their playback, and knows when one of them is in a call.
* **Session** — the stored state: on or off, since when, the block list, the
  registered keys.

### Detecting the app in front

An accessibility service. Android reports every window change
(`TYPE_WINDOW_STATE_CHANGED`) with the package name, so the cover is up almost
at once: measured on the phone, about a tenth of a second after a blocked app
first shows when it is started cold. It is granted by hand in Settings; on Android 13 and
later a sideloaded app must first be allowed "restricted settings".

What counts is every window on screen, not only the one with focus: the
service asks for the window list, and a blocked app in one half of split
screen or in a picture-in-picture window counts as being in front.

The service runs whether or not a session is on, and does nothing when it is
off. The system starts an enabled accessibility service again after a restart
of the phone, so a running session resumes without the app being opened.
Switching the service off is different: that ends the session (section 4).

Switched on is not the same as running. If the service crashes, Android leaves
it switched on in Settings but does not start it again until the phone
restarts, the app is updated, or it is switched off and on by hand. Opening
Pawse does not bring it back. So the service keeps a flag while it is
connected, and Home says so when it is switched on but not running
(section 5).

If MIUI makes this impractical even with the app exempt from battery saving,
the fallback is polling `UsageStatsManager` every half second from a
foreground service (usage access plus "display over other apps"). It is a beat
late, so the app flashes first, and it costs battery. Not built unless needed.

### Covering it

An overlay window owned by the accessibility service
(`TYPE_ACCESSIBILITY_OVERLAY`), which needs no extra permission and cannot be
dismissed with back or recents. If the overlay misbehaves on MIUI, the
alternative is the app's own full-screen activity brought to the front.

The cover takes the whole screen, also when the blocked app is only half of a
split screen. The app beside it is hidden too until the blocked one is closed.

A picture-in-picture window is the exception. Nothing the cover offers can
close a floating window, so covering the whole screen for one would leave the
phone unusable, with Settings and so the emergency exit out of reach. That is
what happened when it was first tried. A floating blocked app gets a small
cover of its own size, which follows it when it is moved. Touches pass through
it, so the window can still be dragged away; opening it up makes it an
ordinary window, which is covered whole.

The cover shows the blocked app's name, how long the session has been on, how
to end it, and a "Go home" button. It never offers a button that unlocks. It
comes down when no blocked app is on screen any more, when the blocked app on
screen is in a call (see below), or when the session ends.

### Notifications, audio and calls

A notification listener, granted by hand in Settings like the accessibility
service. It does nothing while the session is off.

* **Notifications** from blocked apps are snoozed as they arrive, and those
  already showing are snoozed when the session starts. Android has no way to
  bring a snoozed notification back early, so they are snoozed for fifteen
  minutes at a time and snoozed again each time they return while the session
  is on. After the session ends they reappear by themselves within fifteen
  minutes. Nothing is dismissed.
* **Audio**: playback by a blocked app is paused when the session starts and
  whenever one starts playing during it, through its media session, which the
  notification listener gives access to.
* **Calls**: a blocked app is *in a call* while it has a notification of the
  call category showing, which is how WhatsApp, Telegram and the like announce
  an incoming or ongoing call. While it is, that notification is not hidden,
  the app is not paused, and the whole app is not covered. Pawse cannot tell
  the call screen from the rest of the app, so all of it is usable until the
  call ends; then the cover returns.

Without notification access a session does not start (section 5).

### The tag

NFC tags as in HaBytes: an NDEF URI record, `pawse://key/<id>`, plus an
Android Application Record so Android always hands the tag to this app. The
tag holds a random identifier, not a secret; what makes a tag a key is that it
was registered in the app.

* Any number of keys can be registered. They all do the same thing.
* Only a tag carrying a `pawse://key/<id>` record can be a key, whether this
  install wrote it or another one did. Any other tag has to be written first.
* Tags are left rewritable. Pawse never makes one read-only.
* Registering, writing, renaming or removing a key is only possible
  **outside** a session. Otherwise the way out of any session is to make a
  new key.
* Android reads tags only while the phone is unlocked and the screen is on,
  and delivers the scan to this app even with another app in front.

The scan arrives in a small activity with no screen of its own. It toggles the
session, confirms it and finishes. The confirmation is a toast ("Pawsed" /
"Unpawsed") and a vibration, with a different pattern for on and off, since
the phone is against a plushie and may not be looked at.

| Scanned | Session off | Session on |
| --- | --- | --- |
| A registered key | Starts the session, if setup is complete and the block list is not empty; otherwise opens the app at what is missing. | Ends the session. |
| A `pawse://` tag that is not registered | Opens the Keys screen, offering to register it. | Ignored, with a message saying so. |

Held against the phone, a tag can be read twice in a row. A second scan within
three seconds of a toggle is ignored, measured with the monotonic clock.

## 3. Sessions

There is one **block list** and one session, which is on or off.

* **Start**: scanning a registered key. There is no start button.
* **End**: scanning a registered key. There is no end button, no timer and no
  schedule.

While it is on:

* Opening a blocked app shows the cover. Switching to it from recents, a
  link, split screen or picture-in-picture does too.
* If a blocked app is in front when the session starts, it is covered at once.
* Blocked apps' notifications are hidden and their playback is paused, except
  for an app that is in a call, which is usable until the call ends
  (section 2).
* Everything else works normally, including phone calls and the launcher.
* The block list cannot be edited and keys cannot be changed, renamed
  included.
* An app installed during the session is not on the list and stays usable
  until the session ends. Block the store if that matters.
* The session survives the screen turning off, this app being swiped away,
  and a restart of the phone. It does not survive the accessibility service
  being switched off (section 4).

The block list may contain any installed app that has a launcher icon, except:

* Pawse itself, the launcher in use, and the dialer;
* Settings, and any other system app that hosts the accessibility or app-info
  pages, so that the emergency exit in section 4 always works. They are found
  by asking Android which app handles those pages, not from a fixed list;
* the default SMS app and the keyboard in use.

These are worked out when the list is shown and again when a session starts,
so changing the default launcher or SMS app does not leave one blocked.

Apps are matched by package name. A MIUI Dual Apps clone, or the same app in
Second Space, is blocked along with the original wherever the service sees it.

## 4. Getting around it, and getting out without the tag

The app is **honest**: it closes none of the routes around itself.

| Route | What happens |
| --- | --- |
| Switch off the accessibility service in Settings | The session ends. |
| Force-stop the app | Android switches the accessibility service off with it. The session ends. |
| Switch off notification access | Notifications and audio come back, and calls in blocked apps are covered. The session stays on. |
| Uninstall it | Gone. |
| Restart the phone | Nothing: the session is stored and the watcher starts again. |
| Use the blocked service in a browser | Out of scope. Add the browser to the block list if it matters. |

These are several screens deep, which is enough to stop the impulse. That is
the problem being solved; a determined owner of the phone is not. Settings can
never be on the block list (section 3), so the routes are always there.

They are also the **emergency exit**. If the tag is lost, broken, or at home
while you are away, switch the accessibility service off. That ends the
session, and keys can be registered again. Switching the service back on does
not resume it: the next session starts with a scan.

The session is marked as ended at whichever of these comes first:

* the service is told it is being switched off, and records it;
* the service starts and finds a session stored as on that it was switched
  off during (not a restart of the phone, which leaves the session on);
* the app is opened, or a tag is scanned, with a session stored as on and the
  service off. This catches a force-stop, where the service is told nothing.

Nothing else is built for this. Registering a second key and keeping it
somewhere awkward is the comfortable version.

## 5. The app's screens

Small, and only needed between sessions:

| Screen | Contents |
| --- | --- |
| Home | Whether a session is on, since when, and what it blocks. No button to start or end one: it says to scan the tag. The setup checklist if anything is missing: accessibility (also when it is switched on but not running, in which case it says to switch it off and on again), notification access, exemption from battery saving, NFC switched on, at least one key, at least one blocked app. Each item says what it is for and opens the right Settings page or screen. |
| Block list | Installed apps with a switch each, with a search field. Apps that cannot be blocked (section 3) are not listed. Read-only during a session. |
| Keys | Registered tags, with a name each; write a new one; register an existing `pawse://` tag; rename one; remove one. Disabled during a session. |

The interface is in English only. The text lives in string resources, so
another language can be added later.

## 6. Technology

| Part | Choice | Why |
| --- | --- | --- |
| Everything | Kotlin, native, in one Gradle module | The watcher, cover and tag handling must run with the app closed and are most of the code. |
| The app's screens | Jetpack Compose | Three small screens. No Flutter, so no bridge between two languages and a much smaller APK. HaBytes' theme code is not shared. |
| Session logic | Plain Kotlin with no Android classes | So it can be unit-tested on the JVM. |
| Storage | App-private preferences, excluded from backups | A handful of values, read by the service and the screens alike. |
| Min SDK | 26 | As in HaBytes. |
| Package | `uk.pixxula.pawse` | |

There is no server, so no Rust. The vault files this under Rust, but it is an
Android project.

Starting point, from Dev-kit. Its `flutter-android` template is used without
Flutter:

* `flake.nix`: the same shell minus `pkgs.flutter`, the `flutter config` line
  and the `web` shell; `app = "pawse"`. The NDK and CMake are not needed
  without Flutter plugins.
* `android/nix-gradlew`, at the root of the project, which here is the Gradle
  project itself.
* From `android-snippets.md`: `android.builder.sdkDownload=false`, the signing
  block, and the unit-test dependency. The NDK workaround does not apply.
* `docs/android-on-nixos.md`, `docs/android-signing.md` and
  `ci/workflows/android.yml`, with Gradle commands in place of Flutter's.

From HaBytes: `NfcScanActivity.kt` for reading tags, the tag-writing code
behind `lib/platform/nfc.dart` rewritten in Kotlin, and `Store.kt` as the
pattern for the native store.

If the Flutter-less shell works out, it goes back into Dev-kit as an
`android-native` template.

## 7. Permissions

| Permission | For |
| --- | --- |
| Accessibility service | Knowing which app is in front; the cover |
| Notification access | Hiding blocked apps' notifications, pausing their playback, knowing when one is in a call |
| NFC | Reading and writing tags |
| Vibrate | Confirming a scan |
| Ignore battery optimisations | Not being stopped by MIUI |

Listing installed apps uses a `<queries>` entry for launcher activities in the
manifest, not "query all packages". No internet permission, no device admin,
no boot receiver. Usage access and "display over other apps" only if the
fallback detection is ever built.

An accessibility service would not pass Play Store review without a
justification form, which does not matter: the app is installed from its own
releases, like HaBytes.

## 8. Testing

* Kotlin unit tests for the session logic: a registered key toggles; an
  unknown one does not; a start is refused with an empty list or incomplete
  setup; a second scan inside three seconds is ignored; whether a given app is
  covered, including when it is in a call; which apps can never be on the
  list; keys and the list are locked while a session is on; a stored session
  with the service off is ended, and one after a restart is not; whether a
  given notification is hidden.
* On the phone, by hand: every route into a blocked app (launcher, recents,
  link, split screen, picture-in-picture), a scan while a blocked app is in
  front, the screen turning off, swiping the app away, a restart mid-session,
  a scan from inside another app, and the emergency exit in section 4,
  including switching the service back on afterwards.
* Also by hand: notifications hidden during a session and back after it,
  music paused at the start and when started during it, and an incoming and
  an outgoing WhatsApp call with WhatsApp blocked.

## 9. Decisions

What was open in the draft, and how it was settled (4 October 2026).

| | Question | Decision |
| --- | --- | --- |
| Q1 | What does scanning the tag do? | It toggles the session: on if off, off if on. No per-app or timed unlocks. |
| Q2 | How does a session start and end? | Both only by scanning a tag. No button, timer or schedule. |
| Q3 | How strict? | Honest. No route around the app is closed. |
| Q4 | An emergency exit? | Nothing built in. Switching the accessibility service off ends the session. |
| Q5 | One block list, or several? | One. |
| Q6 | Flutter or native for the screens? | Native Kotlin with Compose. |
| Q7 | One tag, or one per app? | Any number of keys, all equal: each toggles the session. |
| Q8 | Should it know about HaBytes? | No. Dropped, not deferred. |
| Q9 | The name | Pawse. |

Questions the settled draft left unanswered, decided the same day.

| | Question | Decision |
| --- | --- | --- |
| Q10 | Can Settings be blocked? | Never, nor whatever else hosts the pages of the emergency exit. |
| Q11 | Calls in a blocked app? | Let through. The whole app is usable while it is ringing or in a call. |
| Q12 | Notifications from blocked apps? | Hidden during a session and returned after it, by snoozing. Call notifications are exempt. |
| Q13 | Split screen and picture-in-picture? | The whole screen is covered if any ordinary window on it is a blocked app's. First settled the same way for picture-in-picture, then changed on 5 October 2026 after it locked the phone: a floating window is covered only where it is. |
| Q14 | The accessibility service is switched off mid-session? | The session ends. Switching it back on does not resume it. |
| Q15 | A scan to start, from inside a blocked app? | Covered at once. |
| Q16 | Audio from blocked apps? | Paused, for the whole session. |
| Q17 | What can "register an existing tag" accept? | Only `pawse://` tags. |
| Q18 | Make written tags read-only? | No. |
| Q19 | Rename a key during a session? | No. The whole Keys screen is disabled. |
| Q20 | What else can never be blocked? | The default SMS app and the keyboard in use. |
| Q21 | Apps installed during a session? | Not blocked until they are added after it. |
| Q22 | MIUI Dual Apps and Second Space? | Blocked along with the original. |
| Q23 | Language | English only. |
| Q24 | How is a scan confirmed? | A toast and a vibration. |

## 10. To find out on the phone

Not decisions, but things only the device (MIUI) can answer. Milestone 1
exists to answer the first three; the last four belong to milestones 4
and 5.

* Does the accessibility service stay alive through a day of normal use with
  the battery-saving exemption, and does it come back by itself after a
  restart? If not after a restart, add a boot receiver as in HaBytes.
* Does the accessibility overlay cover reliably, including over split screen
  and picture-in-picture, or is the activity cover needed? (Answered below,
  except for split screen.)
* Does force-stopping really switch the service off on MIUI, so that the
  emergency exit works as written?
* Does the scan activity, started over another app, return to that app cleanly
  when it finishes?
* Can the service tell being switched off from being killed and started again
  by MIUI? If not, a kill would end the session, and the rule in section 4
  needs another signal.
* Do snoozed notifications stay hidden through a long session, and do they
  sound or light the screen each time they are snoozed again?
* Do WhatsApp and Telegram mark their call notifications with the call
  category, for incoming and ongoing calls alike? If not, calls in blocked
  apps need another signal.
* What does MIUI report for a Dual Apps clone and for Second Space: the same
  package name, and does the service see their windows at all?

Answered so far, on the POCO X3 GT (Android 13, MIUI 14.0.6), over adb, on
4 October 2026:

* The accessibility, notification-access and app-info pages are all in
  `com.android.settings`, not in the Security app. The battery-saving
  exemption page is in `com.miui.powerkeeper`, which has no launcher icon.
* The launcher, dialer, SMS app and keyboard in use are all third-party or
  Google apps here (Nova, Google Phone, Google Messages, FUTO Keyboard), so
  each has to be looked up, never assumed to be the MIUI one.
* The accessibility overlay covers the whole screen, status and navigation
  bars included, and takes the touches. It goes up when the app is opened
  from the launcher or from recents, cold or already running, and comes down
  on "Go home", the back key and the home key.
* A picture-in-picture window is reported like any other, marked as such,
  with its position. The small cover over it works (YouTube). A window that
  has only just opened can be in the list before it can be asked which app it
  is, so the watcher also goes by the event that announced it and looks again
  a quarter of a second later.
* Split screen could not be tried: on this phone it freezes regardless of
  Pawse.
* After a restart of the phone the service comes back by itself, with the app
  never opened, at the moment the phone is first unlocked: not before, as the
  app's storage is locked until then. No boot receiver is needed.
* Force-stopping does switch the service off: Android removes it from the
  enabled services, and blocking stops. The service is told nothing first, as
  section 4 assumes.
* A crash is different from both a force-stop and being switched off: the
  service stays switched on but is not started again (section 2). Whether
  MIUI killing the app to save battery behaves like that, or like an ordinary
  kill that Android recovers from by itself, is still to be seen; it is what
  the day of normal use is for.

## 11. Milestones

1. **Block one app** — the project from Dev-kit, an accessibility service, a
   hard-coded package, the cover. Answers section 10 before anything else is
   built. *Built: the calculator and YouTube are covered whenever the service
   is on. Left to try: a day of normal use.*
2. **Session and block list** — the session logic with its tests, stored
   state, the Block list screen, a temporary debug toggle. Survives a restart.
3. **Keys** — write and register a tag, scan to toggle, the Keys screen. The
   debug toggle is removed.
4. **Home and setup** — session status, the checklist, the battery-saving
   exemption, ending the session when the service is switched off.
5. **Hush** — the notification listener: hiding notifications, pausing
   playback, letting calls through.
6. **Releases** — the signing key and the workflow, from Dev-kit.

## 12. Later

Only if using it shows the need; none of it is planned.

* A quick-settings tile or widget that shows whether a session is on.
* A key per block list ("Work" on the desk, "Evening" by the bed).
* Blocking websites in the browser.
