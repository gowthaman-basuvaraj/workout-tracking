# Uncomplicated

**A log, not a scoreboard.**

A deliberately small Android log for training and eating. You log sets on a day, it
remembers what you did last time, and it tells you when to add a little. You log what you
ate, and it counts how often. Everything lives as plain CSV in a folder you choose, so
Syncthing/Dropbox can sync it and your laptop can read it.

It records; it does not grade. There are no goals, no targets, no nagging and no score —
`Coffee 270` is the whole message, and you decide what to make of it.

No account, no network access, no database.

## The name and the mark

**Uncomplicated** because the app's only promise is that logging something takes a few
seconds and nothing is asked of you afterwards. No streak to protect, no goal to miss, no
colour that turns red.

The mark is three strokes of decreasing length — a short list, getting shorter. One flat
colour (`#2A5C8A`, defined once in `res/values/brand.xml`), no gradient, no glyph and no
dumbbell, so it also works as the monochrome and themed-icon layer without a second
drawing. It is a placeholder done properly, not a logo: `ic_launcher_foreground.xml` and
`ic_splash.xml` are the same path at two scales, so replacing it later means editing two
files.

The splash is the platform's own (`core-splashscreen`), held only while the data folder is
being read and capped at 1.5s, so a slow or unreachable folder can never strand you on it.

## What it does

**Log a day.** Pick a date (arrows or the date picker), tap **Log**, type an exercise, enter
reps and weight. The five kinds sit in a bar along the bottom, where a thumb is, ordered by
how many days out of the last thirty you actually logged each one — so the busiest thing is
nearest. Sleep drops to the end once tonight is logged, since there is only ever one night.
The ranking comes off a 30-day window, so it shifts over weeks rather than between taps and
a position learned by thumb stays learned. The exercise picker starts empty and fills itself in from whatever you
type — the second time you log "Bench press" it is one tap.

**Weight or bodyweight.** A set is either weighted (`40 kg`) or a bodyweight movement
(`BW`, or `BW +10 kg` for weighted pull-ups). The toggle is on the log screen.

**See previous sessions while you log.** Choosing an exercise shows your last five
sessions, how long ago each was, and your best set ever — before you enter anything.

**Progressive overload suggestions.** Underneath that is a concrete target for today, with
the reasoning spelled out. The rule is double progression on a clock you control:

- Inside your overload window the app asks you to **repeat** the last session. Adding load
  every session is how people stall.
- Once the window has elapsed *since your last actual improvement*, it proposes the
  smallest next step: **+1 rep** until you reach the top of your rep range, then
  **+1 weight increment** with reps reset to the bottom of the range.
- For pure bodyweight movements it adds reps until a configurable ceiling, then suggests
  adding load.
- Cardio gets the same treatment on whichever metric you track — roughly +5% distance, or
  +5% duration if you do not log distance.

The clock runs from your last *improvement*, not your last session, so a missed week does
not quietly reset your progression.

**Browse by exercise.** The Exercises tab lists everything you have ever logged. Open one
to see every session, your best set, estimated 1RM trend, and what is due next. Tap a
session to jump to that day. Exercises can be renamed (history is rewritten with them).

**Dashboard.** Pick a reporting window — **week, month (the default), quarter or year** —
and the whole page re-scopes to it: days trained, sets, tonnage against the previous window
of the same length, records, cardio totals and most-logged exercises. The choice is
remembered between launches.

Windows are rolling ("the last 30 days"), not calendar-aligned, so the comparison against
the previous window is always like-for-like and the page never looks empty on the 1st of
the month. The volume chart re-buckets to stay readable — 7 daily bars for a week, 8 weekly
bars for a month, 6 monthly bars for a quarter, 12 for a year.

**Share.** The share icon hands the window's figures to the system share sheet as plain
text — the same summary `unc summary` prints, built from the same code. Text rather
than an image so it pastes into a message, an email or your own notes and stays readable.
Nothing leaves the device until you pick a destination.

Three things deliberately ignore the window: the streak and the all-time footer are
absolute, and the **due for an overload** list follows your overload frequency setting
instead.

**Cardio.** Activity name, duration and optional distance, with a built-in stopwatch that
fills the duration in when you stop it. Pace is computed for you.

**Sleep.** Duration and a three-point quality. A night is filed under the **morning you
woke up**, so logging in the morning needs no date. One night per day — logging again
replaces it. The dashboard reports the average *next to how many nights it came from*,
because an average over 11 logged nights in a month is a different claim from 29, plus
nights under 6h and the quality mix. No sleep score, deliberately.

**Stress — events, not a mood scale.** Name the thing that wound you up or down, and how
much it counted (minor / notable / major). Direction and weight are remembered per item,
so a deadline is always an enhancer and a walk is always a buster: repeats are one tap.

The dashboard leads with **busters you reached for**, and on how many days — "14 deadline
events" tells you work is stressful, which you knew, whereas what you reached for is the
part you control. There is no enhancer-to-buster ratio anywhere: you log stressors when
you are least able to log anything, so that number would claim a precision the data has
not got.

**Food — a log, not a calorie counter.** What you had, what it was for (breakfast, lunch,
dinner, snack, drink) and where it sat on a three-point scale: healthy, everyday,
indulgent. That is the whole schema. There are no calories, macros, portion weights, food
database or barcode scanner, and none are planned.

Optionally, what was in it: **plants, egg, dairy, meat**, any number of them at once. Four
tags is deliberately too few to describe a plate -- they are there so the dashboard can
count how often you reach for each, not so the log can describe a meal, and anything finer
would be nutrition tracking coming back in through the side door. They are remembered per
item like everything else, and an item left untagged simply goes uncounted.

The catalog remembers each item's usual purpose and rating, so the hundredth coffee is one
tap. Picking an item shows **how often it has come up in the last 30 days before you log
it** — "90 times in the last 30 days" lands harder at the point of decision than it ever
does on a dashboard afterwards.

The dashboard then reports, flatly: how many things, split by purpose, the healthy /
everyday / indulgent mix, the tag counts with the coverage they came from, your
most-logged items, consecutive days with nothing indulgent, and what share you log after
21:00. Tags are counts rather than a proportional mix, because they overlap and are
optional: a dosa with curd is two of them, and an untagged item is none. It never editorialises — `Coffee 270` is the whole
message, and you supply the reaction.

**Settings.** Data folder, overload frequency (7 / 15 / 30 days), weight increment, rep
range, bodyweight rep ceiling, kg/lb, km/mi, and your bodyweight (optional — it makes
bodyweight exercises contribute real tonnage to the volume charts).

**Reminders — off until you ask, and as many as you want.** Nothing is scheduled until you
switch them on in Settings. Then add as many as you like — Breakfast, Lunch, Dinner and
Bedtime are one tap each, or name your own — and give each one its own time and days. A
reminder with no days ticked never fires, and the row says so rather than quietly doing
nothing.

Each notification asks whether there is anything to write down. None of them check whether
you logged anything and none will ever say you missed a day.

Local only. `AlarmManager` and a `BroadcastReceiver`, no Firebase, no push service, no new
dependency, and still no `INTERNET` permission in the manifest. Each reminder owns one
pending alarm and one notification slot, both addressed by a small id that is never reused
— so editing one replaces its alarm instead of adding a second, breakfast cannot overwrite
dinner's notification, and a deleted reminder's still-pending alarm finds nothing and
stops. The alarm is inexact
(`setAndAllowWhileIdle`) so it needs no exact-alarm permission and still fires in Doze —
Android may deliver it up to an hour late, which the settings screen says plainly rather
than implying a precision it has not got. A receiver on `BOOT_COMPLETED` and
`MY_PACKAGE_REPLACED` re-arms it, since both drop pending alarms.

**Lock — optional, off by default.** Switch it on in Settings and the app asks for a
fingerprint, or the screen lock, on opening and after a minute in the background. A quick
trip to another app does not ask; the grace period is pure logic in `:core` with tests,
because getting it wrong makes the lock unusable rather than merely annoying.

It uses the platform's own `BiometricPrompt`, not `androidx.biometric` — which measured at
+338 KB and a fragment dependency. `minSdk` is 28 instead, which is where the framework
prompt starts. Device credential is always allowed alongside the fingerprint: there is no
recovery path here, and a sensor that stops recognising you must not shut you out.

It locks the app, not the files. The log is plain CSV in a folder you chose, so anyone
with the phone unlocked can read it from a file manager, or from your laptop if the folder
is synced. Encrypting it would break the thing the app exists for, and the settings screen
says so rather than implying a protection it does not provide.

**How it works.** A short guide behind the `?` on the day screen, and in Settings. First
run offers it once and never mentions it again whichever way you answer. It is a list of
sections rendered by about forty lines of Compose rather than HTML in a WebView: that way
it inherits the app's palette, dark mode and text scaling instead of restating them in
CSS, and the app keeps having no browser engine in it.

## Your data

On first run you pick a folder. Everything goes there as CSV:

```
your-folder/
  strength-2026-11.csv    one row per set
  cardio-2026-11.csv      one row per cardio session
  food-2026-11.csv        one row per food/drink logged
  sleep-2026-11.csv       one row per night, filed under the wake date
  stress-2026-11.csv      one row per stress event
  stress.csv              the stress catalog, with each item's usual direction
  exercises.csv           the picker's catalog (rebuilt from the logs, safe to delete)
  foods.csv               the food catalog: each item's usual purpose, rating and tags
  settings.csv            a read-only copy of the app's settings, for desktop tools
  README.md               the column-by-column schema
  analyze.py              a pandas starter script
```

`README.md` and `analyze.py` are generated: the app rewrites them when it ships newer
copies, so they never document a schema that has moved on. Keep your own analysis in a
separate file.

One file per month, so logging today only rewrites the current month — small, localised
writes that sync tools handle well. There is no database and no lock file, so there is
nothing for a sync client to corrupt. Files whose names contain `conflict` are ignored, so
a sync conflict copy never double-counts your sets.

The app re-reads the folder whenever it comes to the foreground and when you tap refresh,
so edits you make on your laptop show up.

### On your laptop

```bash
cd /path/to/synced/folder
pip install pandas
python analyze.py                           # overall summary
python analyze.py --exercise "Bench Press"  # one lift's full history
python analyze.py --food                    # the food log on its own
python analyze.py --sleep                   # nights, coverage, quality mix
python analyze.py --stress                  # what came up, what you reached for
```

Or straight into pandas:

```python
import glob, pandas as pd
sets = pd.concat([pd.read_csv(f) for f in glob.glob("strength-*.csv")], ignore_index=True)
sets["date"] = pd.to_datetime(sets["date"])
sets["volume"] = sets.weight * sets.reps
print(sets.set_index("date").volume.resample("W-MON").sum())
```

`README.md` in the data folder documents every column.

## Logging from a laptop

`uncomplicated.jar` writes straight into the same synced folder, so you do not have to pick up
your phone to record a coffee. The app sees the change the next time it opens.

```bash
./gradlew :cli:jar                    # -> cli/build/libs/uncomplicated.jar
alias unc='java -jar ~/bin/uncomplicated.jar'
unc init ~/sync/uncomplicated         # remember the folder
```

```bash
unc coffee                      # purpose, rating and tags come from the catalog
unc dosa --plants --dairy       # what was in it, any number of tags
unc biscuits -n 2 --indulgent   # override either of them
unc set bench 10x42.5 --sets 3  # weight unit inferred from your last weighted set
unc cardio run --time 31 --dist 5.3
unc sleep 7h30 --ok             # 7h30, 7:30, 7.5 and 450m all parse
unc stress deadline             # direction and weight remembered per item
unc ls food cof                 # what names exist
unc summary                     # the same text the share button produces
unc today                       # what is logged
unc undo                        # remove what you just logged
unc help set                    # per-command help
```

**Names cannot drift.** `coffee`, `COFFEE` and `  Coffee ` all resolve through the same
case-insensitive key the app uses, and the catalog's own spelling is what gets written —
so the CLI can never create a second "coffee" beside your "Coffee". Partial names work
(`cof`), an ambiguous one lists the candidates and writes nothing, and an unknown one is
**refused** with a did-you-mean rather than silently minting a catalog entry from a typo.
`--new` is how you add something deliberately.

It appends a single row rather than rewriting the month, which keeps the window for a sync
conflict far smaller than the app's own writes.

**Settings travel with the data.** The app publishes `settings.csv` into the folder, so
`unc due` applies your real overload window and `unc set` logs in your real unit
rather than guessing. That copy is **one-way and read-only**: the app rewrites it whenever
a setting changes, so edits made on a laptop are lost — change settings in the app. Without
the file the CLI uses defaults, reads units off your most recent logged row, and says so.

## Building

Requires JDK 17 and the Android SDK (platform 36). `local.properties` already points at
`C:/Users/gowthaman/AppData/Local/Android/Sdk` — edit it if your SDK lives elsewhere.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :cli:jar               # cli/build/libs/uncomplicated.jar
./gradlew :core:jvmTest          # the shared logic: overload, CSV, catalog, stats
./gradlew installDebug           # to a connected device or emulator
```

### Release builds

Release is R8-minified and resource-shrunk (12.5 MB debug → 1.4 MB release). Signing
material is read from the environment, never from a file in the repo:

```bash
export RELEASE_STORE_FILE=/path/to/your.jks
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=...
export RELEASE_KEY_PASSWORD=...

./gradlew release           # both of the below
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab
```

Both, because they are for different things. The **APK** is what you sideload onto your
own phone or hand to someone directly. The **bundle** is the only format Play accepts for
a new app — you upload the `.aab` and Play builds the per-device APKs from it.

Mind the asymmetry when checking a build: an unsigned APK is renamed to
`app-release-unsigned.apk`, so the filename gives it away, but an unsigned **bundle keeps
the same name**. Only the signature proves anything, and `apksigner` cannot read a bundle:

```bash
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

`jar verified.` is the pass, `jar is unsigned.` the failure. The warning underneath about a
self-signed certificate is expected and not a problem: an upload key is self-signed by
design, and Play re-signs with the app signing key on its side. Do not add `-strict`, which
reports exactly that as an error.

They also work as Gradle properties (`-PRELEASE_STORE_FILE=...`) if you prefer.

### Versioning

`versionCode` is the commit count (`git rev-list --count HEAD`) and `versionName` is the
latest tag, or `1.0` until there is one. Nothing to remember before a release: tag when
the name should change, and the code takes care of itself.

Play rejects an upload whose `versionCode` is not strictly higher than the last, and a
number once used is burned forever — so both ways of getting it wrong fail the build
rather than producing a number quietly. Building outside a git checkout is an error, and
so is a commit count below the floor in `app/build.gradle.kts`, which is what a shallow
clone (`--depth 1`) looks like: it answers `1`, and that would look perfectly normal.

Deliberately not Jenkins' `BUILD_NUMBER` — it is per-job, so recreating the job resets it
to 1 and every later upload is refused until the count climbs past what was already used.

Without those variables the build still succeeds but emits `app-release-unsigned.apk`
instead — which is what CI checks for, so an unsigned APK can never be archived as a
release. CI verifies the bundle's signature separately, for the reason above. Keep `app/build/outputs/mapping/release/mapping.txt` for any build you ship;
without it a production stack trace is unreadable.

## Continuous integration

`Jenkinsfile` is a self-contained declarative pipeline — point a Jenkins
**Pipeline from SCM** job at this repo and nothing needs pre-installing on the agent
beyond a POSIX shell with `git`, `curl`, `unzip` and `tar`.

It provisions Temurin JDK 17 and the Android SDK (cmdline-tools 23.0, platform-tools,
`android-36`, build-tools `36.0.0`) into `$HOME/.android-ci`, which persists across builds,
then runs the committed Gradle wrapper. Stages: checkout → provision JDK → provision SDK →
`clean testDebugUnitTest assembleDebug` → optionally a signed release.

Test results are published via `junit`; APKs, the release bundle, the CLI jar and the R8
mapping file are archived.

Tick the **RELEASE** build parameter to also produce a signed release APK. That stage needs
four Jenkins credentials:

| Credential ID               | Kind        | Contents                |
|-----------------------------|-------------|-------------------------|
| `android-release-keystore`  | Secret file | your `.jks`/`.keystore` |
| `android-keystore-password` | Secret text | keystore password       |
| `android-key-alias`         | Secret text | key alias               |
| `android-key-password`      | Secret text | key password            |

The agent is assumed to be linux/x64. For arm64, change `x64` to `aarch64` in the JDK
download URL and `commandlinetools-linux-` to the matching archive.

## Layout

Three Gradle modules. `:core` is Kotlin Multiplatform with no Android on the classpath at
all, which is what lets the phone and the laptop run the same code instead of two copies
that drift apart. It builds for the JVM today; the sources are `commonMain`, so an iOS
target is a line in `core/build.gradle.kts` rather than a rewrite.

The rule that keeps it honest is checked by the compiler, not by discipline. A JVM target
alone cannot catch a stray `java.time` import — `java.*` is on its classpath. So the build
also compiles `:core` for a **native** target, chosen by whichever host is building
(`mingwX64` on Windows, `linuxX64` elsewhere, iOS on a Mac). Native has no JVM runtime, so
anything reaching for `java.*` fails to compile there. That is the whole guard: the day
someone adds `java.util.UUID` to shared code, the build says so.

Dates come from `kotlinx-datetime`. The handful of things the JVM gave away free and
multiplatform does not — `YearMonth`, a random row id, zero-padding, month names — live in
`core/data/Dates.kt`, a few lines each rather than a dependency.

```
core/     Kotlin Multiplatform (commonMain) — shared by :app and :cli
  data/
    Models.kt       StrengthSet, CardioEntry, sessions, units, formatting
    Food.kt         FoodEntry, purpose, the healthy-to-indulgent scale
    Sleep.kt        SleepNight, quality, duration parsing
    Stress.kt       StressEvent, enhancer/buster, minor-to-major
    Settings.kt     preferences as a plain value type
    Csv.kt          CSV reader/writer, header-addressed rows
    CsvFormat.kt    file names, headers, row <-> text, settings. The format, no I/O
    Catalog.kt      name matching, canonical spelling, catalog rebuilding
    DateFormat.kt   "3 days ago" and friends
    Dates.kt        today(), YearMonth, row ids, padding — the java.* replacements
  logic/
    Overload.kt     the progression engine
    Summary.kt      the shareable text, shared by the share sheet and the CLI
    Stats.kt        dashboard aggregates
  166 unit tests (jvmTest)

app/      Android
  data/CsvStore.kt  folder I/O through the Storage Access Framework
  data/Repo.kt      in-memory source of truth with CSV write-through
  data/Prefs.kt     SharedPreferences
  ui/               Compose screens: Day, Log, Library, detail, Dashboard, Settings

cli/      desktop
  Folder.kt         the same folder through java.io.File
  Main.kt           commands, matching, help
```

Design notes worth knowing if you change things:

- **Weights keep the unit they were entered with.** Rows carry `unit`, and display converts.
  Switching kg/lb never rewrites history.
- **Everything is in memory.** A few years of training is well under a megabyte, so the repo
  holds the full data set and rewrites only the months a change touched.
- **Nothing in `:core` may import `android.*` or `java.*`.** The first rule is why the CLI
  exists without a second implementation of anything; the second is why an iOS target stays
  one line away. The native canary target enforces the second one at compile time.
- **Estimated 1RM is Epley** (`weight × (1 + reps/30)`), used to compare sets and detect
  improvement. Unweighted sets compare on reps instead.
- **Food counts are quantity-weighted.** `qty` means occurrences, never portions: two
  coffees in one sitting is `2`, and every total — including the after-21:00 share — counts
  it twice.
- **`logged_at` is UTC epoch millis.** The app reads hour-of-day in the device's zone;
  `analyze.py` converts to the reader's local zone. Treating it as UTC silently shifts
  every late-night entry by your offset.
