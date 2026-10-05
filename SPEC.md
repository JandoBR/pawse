---
id: Spec
aliases: []
tags: []
---

# Scrolly — Specification (draft)

Working name, after the product the idea comes from. Started from the note in
the vault (`Projects/Rust/Scrolly(App focus)/idea.md`):

> Scrolly like App, basically lets you enter a focus mode where apps are locked
> until you scan an nfc tag, which unlocks an app, it should block selected
> apps, basically drawing a layer on top until the nfc tag gets scanned.

This is a draft. Several decisions that shape the rest are still open; they are
collected in section 9, and wherever the text depends on one it says so as
**(Q1)**, **(Q2)** and so on. Nothing here is built yet.

## 1. Overview

An Android app that makes chosen apps unusable during a **focus session**. A
blocked app is covered by a full-screen layer the moment it comes to the front.
The way out is physical: scanning an **NFC tag** that is somewhere you have to
go to.

The point of the tag is friction. A confirmation dialog is one tap; a tag on
the other side of the flat is a decision.

### Goals

* Block a chosen set of apps for the length of a session, reliably, including
  after the screen turns off, the app is swiped away, or the phone restarts.
* Make ending or bypassing a session cost real effort, and make the cheap
  routes around it closed or at least inconvenient **(Q3)**.
* Stay out of the way otherwise: no account, no server, nothing leaving the phone.
* Reuse what HaBytes already worked out: NFC tags, native Kotlin that runs
  without the UI, background work that survives MIUI, signed releases.

### Non-goals

* iOS. Third-party apps cannot block other apps there outside Screen Time.
* Blocking websites, or content inside an app.
* Parental control: this is a tool you use on yourself, not on someone else.
* Usage statistics, streaks, charts.
* A server, accounts, or sync between devices.

## 2. How it works

```
   an app comes to the front
              │
              ▼
   Watcher (accessibility service) ── is a session running, and is this
              │                        app on its block list?
              │ yes
              ▼
   Cover: a full-screen layer over the app          NFC tag scanned
   ("Locked. Scan your tag.")                              │
              ▲                                            ▼
              │                                   Unlock (Q1): end the session,
              └────────── re-covers when ◄─────── or open one app, or open it
                          the unlock runs out     for a while
```

Three native parts, none of which needs the app's own screens to be open:

* **Watcher** — knows which app is in front, at all times during a session.
* **Cover** — what is shown instead of a blocked app.
* **Session** — the stored state: running or not, the block list, any unlocks
  and when they expire.

### Detecting the app in front

Two ways. The first is the plan; the second is the fallback if it proves
impractical on MIUI.

| | Accessibility service | Usage-stats polling |
| --- | --- | --- |
| How | Android reports every window change (`TYPE_WINDOW_STATE_CHANGED`) with the package name. | Ask `UsageStatsManager` every half second or so which app was last in front. |
| Speed | Immediate: the cover is up before the app draws. | A beat late: the app flashes first. |
| Permission | Accessibility, granted by hand in Settings. On Android 13 and later a sideloaded app must first be allowed "restricted settings". | Usage access, plus "display over other apps". |
| Cost | A powerful permission; MIUI may stop the service unless the app is exempt from battery saving. | A service that polls, which costs battery and is also a target for MIUI. |

### Covering it

Either an overlay window owned by the accessibility service
(`TYPE_ACCESSIBILITY_OVERLAY`, which needs no extra permission), or the app's
own full-screen activity brought to the front. The overlay is harder to slip
past; the activity is easier to build and can receive the NFC scan directly.
To be settled by trying both on the phone.

The cover says which app is locked and how to get out, and offers "Go home".
It never offers a button that unlocks.

### The tag

NFC tags as in HaBytes: an NDEF URI record, `scrolly://key/<id>`, plus an
Android Application Record so Android always hands the tag to this app. The
tag holds an identifier, not a secret; what makes a tag a key is that it was
registered in the app.

* Registering or writing a key is only possible **outside** a session.
  Otherwise the way out of any session is to make a new key.
* Android reads tags only while the phone is unlocked and the screen is on,
  and delivers the scan to this app even with another app in front.
* A scan with no session running does nothing, or starts one **(Q2)**.

## 3. Sessions

A session has a **block list** (the apps it covers), a start, and an end
condition **(Q2)**. While one is running:

* Opening a blocked app shows the cover. Switching to it from recents, a
  notification or a link does too.
* Everything else works normally, including calls and the launcher.
* The session survives the screen turning off, this app being swiped away,
  and a restart of the phone.

What a scan does to a running session is **(Q1)**. The cover, the stored state
and the watcher are the same in every variant; what changes is whether there
are per-app unlocks with an expiry.

## 4. Getting around it

Blocking the apps is the easy half. A blocker is as strong as its cheapest
exit, and every one of these is open by default:

| Exit | Closing it |
| --- | --- |
| Turn off the accessibility permission | Cover Settings' accessibility pages (or all of Settings) during a session. |
| Force-stop the app from its app-info page | Cover that page during a session. |
| Uninstall it | Device-admin rights: an app that is a device admin cannot be uninstalled until that is switched off, which is itself a Settings page to cover. |
| Restart the phone | Start the watcher again at boot; the session is stored, not held in memory. |
| Safe mode (third-party apps do not run) | Cannot be closed. It takes a restart and a button combination, which is friction of its own. |
| Use the blocked service in a browser | Out of scope. Add the browser to the block list if it matters. |
| Change the clock to end a timed session | Measure time with the monotonic clock, not the wall clock. |

How many of these to close is **(Q3)**. Closing all of them needs an emergency
exit that is deliberately costly **(Q4)**, or a mistake locks you out of your
own Settings.

Blocking Settings must never cover the screens needed to answer a call, use
the dialer, or reach emergency services.

## 5. The app's screens

Small, and only needed between sessions:

| Screen | Contents |
| --- | --- |
| Home | Whether a session is running and what it blocks; start one; the setup checklist if a permission is missing. |
| Block list | Installed apps with a switch each. Possibly named lists ("Work", "Evening") **(Q5)**. |
| Keys | Registered tags, with a name each; write or register a new one; remove one. Disabled during a session. |
| Setup | The permissions, one at a time, each with what it is for and a button that opens the right Settings page: accessibility, exemption from battery saving, and device admin if used **(Q3)**. |

## 6. Technology

| Part | Choice | Why |
| --- | --- | --- |
| Watcher, cover, session state, tag handling | Kotlin, native | They must run with the app closed; none of it is UI. As in HaBytes. |
| The app's screens | **(Q6)**: Flutter, or native Kotlin (Compose) | Flutter matches HaBytes and its theme can be reused. Native keeps a small app in one language with no bridge. |
| Storage | App-private preferences, excluded from backups | A handful of values. |
| Min SDK | 26 | As in HaBytes. |

There is no server, so no Rust: nothing here needs one. The vault files this
under Rust, but it is an Android project.

Starting point: the `flutter-android` template in Dev-kit, with its Android
notes and signing guide. From HaBytes: `NfcScanActivity.kt` and
`lib/platform/nfc.dart` for tags, the native store and method channel pattern,
`BootReceiver`, and the release workflow.

## 7. Permissions

| Permission | For | Needed |
| --- | --- | --- |
| Accessibility service | Knowing which app is in front; the overlay | Yes |
| NFC | Reading and writing tags | Yes |
| Receive boot completed | Resuming a session after a restart | Yes |
| Ignore battery optimisations | Not being stopped by MIUI | In practice, yes |
| Query all packages | Listing installed apps for the block list | Yes |
| Device admin | Not being uninstallable during a session | **(Q3)** |
| Usage access, display over other apps | Only for the fallback detection | Fallback only |

None of this would pass Play Store review without a justification form, which
does not matter: the app is installed from its own releases, like HaBytes.

## 8. Testing

* Kotlin unit tests for the session logic, kept free of Android classes:
  whether an app is covered given the session and the time, unlocks expiring,
  a key scanned in and out of a session, time measured monotonically.
* On the phone, by hand: every route into a blocked app (launcher, recents,
  notification, link, split screen), the screen turning off, swiping the app
  away, a restart mid-session, and each exit in section 4 that is meant to be
  closed.

## 9. Open questions

Not decided. Each lists the options and what it changes. Where I have a
leaning it is marked as mine, not as the answer.

### Q1. What does scanning the tag do?

| Option | Means | Cost |
| --- | --- | --- |
| a. End the session | One scan and everything is open again. | Simplest. But one weak moment ends the whole session. |
| b. Unlock one app, until the session ends | Scan while a covered app is in front; that app is free. | Little more work. The app you most need blocked is the one you will unlock. |
| c. Unlock one app for a set time | "15 minutes of this", then it covers itself again. | Needs expiries and a re-cover while the app is open. The most useful, and the closest to the note ("which unlocks an app"). |

*Leaning:* c, with the time fixed in settings to begin with.

Affects: section 3, whether the session state carries unlocks, and the cover's text.

### Q2. How does a session start and end?

* Start: by hand in the app; on a schedule (weekdays 9 to 13); by scanning the
  tag on the way out; or a mix.
* End: only by the tag; after a set length; at a set time; or never, meaning
  blocking is simply on until switched off.

*Leaning:* start by hand, end by tag or at a time set when starting. Schedules later.

Affects: section 3, and whether a scan outside a session means anything.

### Q3. How strict?

| Level | Closes | Needs |
| --- | --- | --- |
| 1. Honest | Nothing. Force-stopping the app ends it. | Nothing extra |
| 2. Inconvenient | Covers the accessibility and app-info pages of Settings during a session. | More packages and screens to recognise |
| 3. Locked | Also device admin, so it cannot be uninstalled; covers the page that removes that too. | Device admin; an emergency exit (Q4) |

*Leaning:* 2. It stops the impulse, which is the actual problem, without the
risk that a bug locks you out of your phone's settings.

Affects: section 4, the device-admin permission, and whether Q4 matters.

### Q4. Is there an emergency exit, and what does it cost?

If the tag is lost, broken or simply at home while you are away for a week,
there has to be some way out. Options: a second tag kept somewhere awkward; a
long wait (start the exit, it takes effect in an hour); typing a long random
string shown on screen; nothing, and you uninstall it over USB.

*Leaning:* a wait. It cannot be done on impulse and needs nothing kept anywhere.

### Q5. One block list, or several?

One list is enough to start. Named lists ("Work", "Evening") make schedules
(Q2) more useful and cost a second screen and a choice when starting.

### Q6. Flutter or native for the screens?

See section 6. *Leaning:* native Kotlin with Compose. The app is four small
screens and most of its code is native anyway, so Flutter would add a bridge
and thirty megabytes for little. The price is not sharing HaBytes' theme code.

### Q7. One tag, or one per app?

One key that does whatever Q1 says is simplest. A tag per app (the "Instagram
tag" kept in a drawer) lets different apps cost different effort, and only
means anything under Q1 b or c.

### Q8. Should it know about HaBytes?

An idea, not a requirement: apps unlock when today's habits are done ("no
YouTube until I have read 20 pages"), read from HaBytes' API with a token.
It would be the one thing no bought product has. It also adds a network
dependency to something that otherwise has none.

*Leaning:* leave room for it (an "unlock condition" that today is only "a key
was scanned"), build it after the rest works.

### Q9. The name

"Scrolly" is someone else's product. Fine as a working name on your own phone;
worth changing before the code and the package name harden around it.

## 10. Milestones

Provisional, and assuming the leanings above. Reorder once section 9 is settled.

1. **Block one app** — accessibility service, a hard-coded package, a cover. Proves detection and the cover on MIUI before anything else is built.
2. **Sessions and a block list** — stored state, the list screen, start by hand, survives a restart.
3. **Keys** — register a tag, scan to unlock (per Q1).
4. **Setup** — the permission checklist; battery-saving exemption.
5. **Strictness** — whatever Q3 decides.
6. **Releases** — signing and the workflow, from Dev-kit.

## 11. Later

* Schedules.
* Named block lists.
* A home-screen widget or quick-settings tile to start a session.
* HaBytes as an unlock condition (Q8).
* Blocking websites in the browser.
