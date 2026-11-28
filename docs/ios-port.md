# The iOS port: where it stands, and what is already decided

Written 28 November 2026, at commit `4256413`. This is the document to read first
when picking the iOS work back up.

Nothing here is urgent. The Android app is unaffected and still shipping — the
work so far only moved code between modules, and the last release build
(versionCode 38) was signed and verified by CI with everything described below
already in place.

---

## The rule

**We do not fight iOS.** Wherever the two platforms disagree, iOS wins and we
take what it offers.

If iOS has no toast, we do not build a convincing fake toast. If iOS does not
want to hand us an arbitrary folder, we use the folder it does hand us. The
Android behaviour is not the specification — the logged data is. Everything in
*Known issues* below has already been decided on that basis, so the decision
does not have to be made again under time pressure.

The one thing that is not negotiable is the file format. A month of food on an
iPhone must be the same CSV as a month of food on Android, because the whole
point of this app is that the files outlive it.

---

## Where it stands

```
:core   4,072 lines   logic, storage, settings, app state
:ui     6,239 lines   every screen, in Compose Multiplatform
:app      454 lines   activity, ViewModel, two store adapters, notifications
:cli    1,198 lines   the desktop logger
```

**10,311 of roughly 12,000 lines are already shared.** What is left on Android is
an activity, a ViewModel, two adapters and the notification plumbing — and all
of it has a declared counterpart waiting on the iOS side.

What exists and works today:

- `:core` compiles for JVM, native (`mingwX64`/`linuxX64`) and wasm.
- `:ui` compiles for Android and wasm. All 5,700 lines of Compose.
- 174 tests, including the data layer, which is testable because the storage
  port made it so.
- CI runs both canaries on every build.
- `core/build.gradle.kts` already declares `iosArm64()` and
  `iosSimulatorArm64()` — they are only registered when the build host is a Mac.

What does not exist: an `iosApp` target, the iOS implementations of the eight
seams, and a Mac to compile them on.

---

## The eight seams

This is the entire platform boundary. Three ports in `:core`, five in `:ui`.
Everything else is shared.

| # | Seam | Where | Android today | iOS answer |
|---|------|-------|---------------|------------|
| 1 | `DocumentStore` | `core/data` | Storage Access Framework | `NSFileManager` |
| 2 | `KeyValueStore` | `core/data` | SharedPreferences | `NSUserDefaults` |
| 3 | `ReminderScheduler` | `core/logic` | `AlarmManager` | `UNUserNotificationCenter` |
| 4 | `rememberToaster` | `ui` | `Toast` | **nothing equivalent** — see below |
| 5 | `rememberSharer` | `ui` | `ACTION_SEND` intent | `UIActivityViewController` |
| 6 | `rememberFolderPicker` | `ui` | `OpenDocumentTree` | **the hard one** — see below |
| 7 | `rememberNotificationPermission` | `ui` | `POST_NOTIFICATIONS` | `requestAuthorization` |
| 8 | `rememberAuthenticator` | `ui` | `BiometricPrompt` | `LAContext` |

Five of the eight are near-direct translations. Two need a decision, and both
decisions are made below.

The seams in `:ui` are `remember` functions returning a lambda, not an
interface. That is not a style preference: on Android three of them must be
registered during composition, because an Activity result launcher cannot be
created from a click handler. An interface would have been a shape Android could
not implement.

---

## Known issues, and the ruling on each

### 1. iOS will not casually hand over a folder

**The issue.** The app's storage model is "point me at a folder, I will keep CSV
files in it, sync it with whatever you like". That is natural on Android and on
a desktop. On iOS an app gets its own sandboxed `Documents` directory, and
reaching outside it means `UIDocumentPickerViewController` with security-scoped
URLs and bookmarks that have to be persisted and re-resolved on every launch.

**The ruling.** Use the app's own `Documents` directory. Expose it to the Files
app with `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace` in
`Info.plist`. The person can then see, open, copy and back up their CSVs from
Files, and iCloud Drive will sync them if they have it on.

Do not build the SAF equivalent. Arbitrary folder picking can be added later as
an option if it is ever actually wanted; it is not needed for a first version.

**What this costs, honestly.** The Syncthing/Dropbox/Nextcloud story does not
really exist on iOS — Syncthing in particular has no serious iOS client. iOS
users get iCloud Drive or they get manual copies out of Files. That is a real
reduction, and it is the price of not fighting the platform. The CSVs remain
readable, portable and theirs, which is the part that matters.

**Already done for this:** `DocumentStore` deliberately has no concept of
picking, permissions, URIs or MIME types. `useFolder(location: String)` takes an
opaque string that `:core` never interprets. On iOS that string can simply be
the Documents path, decided once at startup — and `rememberFolderPicker` can
call back immediately without showing anything.

### 2. iOS has no toast

**The issue.** There is no system-level transient message on iOS. The existing
comment in the code explains why a toast rather than a snackbar was chosen: some
confirmations fire as the screen is closing, and a snackbar lives inside the
screen that is going away — precisely when the message is worth reading.

**The ruling.** Render a small overlay in Compose, hosted at the root above
navigation rather than inside a screen. That keeps the property that made a
toast the right choice, and it is ordinary Compose, so it would work on any
platform.

If that turns out to look wrong on iOS, the fallback is to show nothing. The
confirmation is a nicety; the row is already written to the file by the time it
would appear.

### 3. Reminders work differently, and better

**The issue.** No `AlarmManager`. iOS schedules local notifications through
`UNUserNotificationCenter`.

**The ruling.** Use `UNCalendarNotificationTrigger` with `repeats: true` — one
trigger per reminder per selected weekday. This is a *better* fit than Android's
model: Android has to re-arm after every firing and after every reboot, which is
why `RescheduleReceiver` exists. iOS does not.

`Reminders.nextTrigger` in `:core` is not needed on iOS, which is fine — it stays
for Android. The `ReminderScheduler` port is two methods and `applyAll` is
already a full re-arm rather than a diff, which maps cleanly onto "remove all
pending requests for this id, add the new ones".

**Ceiling to remember:** iOS allows 64 pending notification requests per app.
Three reminders across seven days is 21. If reminders ever become
per-day-configurable in bulk, check this.

### 4. Biometrics

**The ruling.** `LAContext.evaluatePolicy(.deviceOwnerAuthentication)`. That
policy includes passcode fallback, which matches the Android choice
(`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`) deliberately — the lock is meant to
borrow the device's own unlock, not to be a separate secret.

**Trap:** an app that touches Face ID without `NSFaceIDUsageDescription` in
`Info.plist` is terminated by the system. Not a warning, a crash.

`Authenticator.unavailableReason` already exists and already drives the giving-way
behaviour: if the device has nothing to ask with, the lock switches itself off
rather than shutting someone out of their own log. That logic is shared and does
not need rewriting.

### 5. The look

**Open decision, with a recommendation.** Compose Multiplatform draws Material 3
on iOS. It will not look like a UIKit app.

Recommendation: keep Material. The app is already plain to the point of being
austere — flat surfaces, no gradients, system fonts, almost no chrome. It reads
as "a quiet app" more than it reads as "an Android app", and maintaining two
visual languages for a logging tool is a poor use of a weekend.

Revisit only if it genuinely looks wrong on a device. Do not decide this from
screenshots on a Windows machine.

### 6. Smaller things, already understood

- **No `AndroidViewModel`.** `AppModel` takes a `CoroutineScope` as a
  constructor argument precisely so this is not a problem. On iOS, create one at
  app scope and hand it over.
- **`Dispatchers.IO` exists on Kotlin/Native**, so `Repo`'s injected dispatcher
  has a real answer on iOS. It is injected because it does *not* exist in common
  code, which the canary pointed out.
- **Back gesture.** Compose Multiplatform ships a back handler, and
  `compose.ui backhandler` is already on the dependency graph. iOS edge-swipe
  behaviour should be checked on a real device.
- **Assets.** The app icon, splash and notification icon are all Android
  resources and need iOS equivalents in an asset catalog. The help glyph does
  not — it was moved into Kotlin as an `ImageVector` for exactly this reason.
- **`bundledDoc`.** `Repo` is handed a function that reads `README.md` and
  `analyze.py` out of the app bundle. On iOS these need to go into the bundle
  and be read with `NSBundle`. Small, but easy to forget until the folder has no
  README in it.

---

## What is left to do

Roughly in order. Nothing before step 4 needs a Mac.

1. **Write the iOS actuals for the three `:core` ports.** `NSFileManager`,
   `NSUserDefaults`, `UNUserNotificationCenter`. Perhaps 250 lines. These can be
   *written* on any machine; they just cannot be compiled without a Mac.
2. **Write the five `:ui` actuals.** The hard thinking is done — see the rulings
   above. Perhaps 200 lines.
3. **Add an `iosApp` target** and the Compose entry point
   (`ComposeUIViewController { Root(model) }`).
4. **Get a Mac.** Options, cheapest first:
   - A macOS CI runner (GitHub Actions, Codemagic) — enough to *compile* and run
     tests, not enough to feel the app.
   - A rented Mac by the hour (MacStadium, Scaleway).
   - A used Mac mini. The only option that lets you hold the thing and use it.
   You also need an Apple Developer account (~$99/year) to put it on a phone
   that is not a simulator.
5. **Run it in the simulator**, fix what the canary could not see — layout,
   insets, the back gesture, how Material feels.
6. **Decide the look** (issue 5) on a real device.

---

## Rules that keep this working

Three things, and the first one is the only reason any of this is trustworthy.

**A green `:ui:assembleDebug` proves nothing about portability.** Shared code
compiled for the Android target can see `android.*` and `java.*` perfectly well,
so a `Context` in a screen builds there without a word of complaint. When the UI
was first moved, `assembleDebug` passed on the first attempt — and the wasm
target then found a share intent, `System.currentTimeMillis()`, `java.time`
formatting, an Android `Bundle`, a `Toast`, two dialogs and twelve
`LocalContext`s. Every one of those would otherwise have arrived as a compile
error on a Mac.

So:

```bash
./gradlew :core:compileKotlinLinuxX64 :ui:compileKotlinWasmJs
```

Run it after touching anything in `:core` or `:ui`. CI runs it on every build.
The wasm target is never shipped and never executed; it exists to be compiled,
because it has neither a JVM nor an Android runtime underneath it and therefore
refuses both `java.*` and `android.*`.

**Nothing in `:core` or `:ui` common code may import `android.*` or `java.*`.**
This is the rule the canary enforces. Platform code implements a port; it does
not own logic. If you find yourself adding a rule to `SafStore` or
`DirectoryStore`, it belongs in `CsvStore` instead — the two implementations
drifted once before, and the CLI wrote a nine-column row under an eight-column
header.

**The wasm stubs must stay in step.** `ui/src/wasmJsMain/.../Platform.wasmJs.kt`
holds five empty implementations. They are empty on purpose, not unfinished: the
target is never run. But a new seam needs a stub there or the canary stops
compiling, which is the build telling you the truth.

---

## Version pins, and why they are where they are

Do not bump these casually. Each one is load-bearing.

| Pin | Version | Why |
|---|---|---|
| AGP | 8.13.2 | Newer androidx needs AGP 9 and compileSdk 37 |
| compileSdk / targetSdk | 36 | Same |
| minSdk | 28 | Framework `BiometricPrompt` |
| Kotlin | 2.2.20 | R8 inside AGP 8.13.2 cannot parse Kotlin 2.3 metadata |
| Compose Multiplatform | 1.10.3 | **1.12 requires compileSdk 37 and AGP 9.1.** 1.10.3 matches the androidx.compose 1.10.4 the app already resolved |
| navigation-compose | 2.9.2 (JetBrains) | Multiplatform build, not Google's Android-only one |
| lifecycle | 2.9.4 (JetBrains) | Same |

They move as a set or not at all. The chain is: AGP → compileSdk → androidx →
Compose Multiplatform → Kotlin.

---

## Gotchas already paid for

Recorded so they are not rediscovered.

- **`compose.materialIconsExtended` is a trap.** It is the icon accessor Compose
  Multiplatform advertises, and it is the core set plus about two thousand icons
  nobody uses — 15 MB of dex that R8 strips from release builds but not from
  debug. Every icon these screens use is in the core set, so the dependency is
  `org.jetbrains.compose.material:material-icons-core:1.7.3`.
- **Measure APK size after a clean build.** An incremental build can leave the
  old APK in place; the icon bloat appeared to persist after it had been fixed
  because the file on disk had not been rewritten.
- **Material's date picker speaks UTC.** `selectedDateMillis` means UTC, so the
  conversion is pinned to UTC in both directions. Using the device zone hands
  back the previous day for anyone west of Greenwich — and this phone is at
  UTC+5:30, where the bug would have been invisible.
- **`versionCode` comes from `git rev-list --count HEAD`**, with a floor guard
  because a shallow clone would produce a small number and Play rejects a
  versionCode lower than one already uploaded.
- **Pushing and triggering a build at the same time races.** The push starts an
  SCM-triggered build at the *previous* commit with default parameters, and the
  manual request becomes the next build number. Check which revision a build
  actually used before believing its result.
- **An unsigned `.aab` keeps the same filename**, unlike an unsigned APK. The
  only way to tell is `jarsigner -verify` — and not `-strict`, because an upload
  key is self-signed by design.

---

## Things deliberately not done

So that their absence is not mistaken for an oversight.

- **`FLAG_SECURE`** — the lock does not hide the app from the recents screen.
- **A configurable lock grace period** — it is fixed at 60 seconds.
- **The keystore backup.** See `docs/release-keys.md`, which supersedes what
  this page said in an earlier draft. The key on Jenkins is the *upload* key,
  not the app signing key -- `life.uncomplicated` is enrolled in Play App
  Signing, so Google holds the signing key and a lost upload key is a reset
  request and a few days, not a dead listing. Still worth having a copy off
  that server, and that page says how.
