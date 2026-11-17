# Workout Log

A deliberately small Android training and eating log. You log sets on a day, it remembers
what you did last time, and it tells you when to add a little. You log what you ate, and it
counts how often. Everything lives as plain CSV in a folder you choose, so Syncthing/Dropbox
can sync it and your laptop can read it.

No account, no network access, no database.

## What it does

**Log a day.** Pick a date (arrows or the date picker), tap **Log**, type an exercise, enter
reps and weight. The exercise picker starts empty and fills itself in from whatever you
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

Three things deliberately ignore the window: the streak and the all-time footer are
absolute, and the **due for an overload** list follows your overload frequency setting
instead.

**Cardio.** Activity name, duration and optional distance, with a built-in stopwatch that
fills the duration in when you stop it. Pace is computed for you.

**Food — a log, not a calorie counter.** What you had, what it was for (breakfast, lunch,
dinner, snack, drink) and where it sat on a three-point scale: healthy, everyday,
indulgent. That is the whole schema. There are no calories, macros, portion weights, food
database or barcode scanner, and none are planned.

The catalog remembers each item's usual purpose and rating, so the hundredth coffee is one
tap. Picking an item shows **how often it has come up in the last 30 days before you log
it** — "90 times in the last 30 days" lands harder at the point of decision than it ever
does on a dashboard afterwards.

The dashboard then reports, flatly: how many things, split by purpose, the healthy /
everyday / indulgent mix, your most-logged items, consecutive days with nothing indulgent,
and what share you log after 21:00. It never editorialises — `Coffee 270` is the whole
message, and you supply the reaction.

**Settings.** Data folder, overload frequency (7 / 15 / 30 days), weight increment, rep
range, bodyweight rep ceiling, kg/lb, km/mi, and your bodyweight (optional — it makes
bodyweight exercises contribute real tonnage to the volume charts).

## Your data

On first run you pick a folder. Everything goes there as CSV:

```
your-folder/
  strength-2026-11.csv    one row per set
  cardio-2026-11.csv      one row per cardio session
  food-2026-11.csv        one row per food/drink logged
  exercises.csv           the picker's catalog (rebuilt from the logs, safe to delete)
  foods.csv               the food catalog, with each item's usual purpose and rating
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

## Building

Requires JDK 17 and the Android SDK (platform 36). `local.properties` already points at
`C:/Users/gowthaman/AppData/Local/Android/Sdk` — edit it if your SDK lives elsewhere.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # unit tests for the overload engine, CSV and stats
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

./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

They also work as Gradle properties (`-PRELEASE_STORE_FILE=...`) if you prefer.

Without those variables the build still succeeds but emits `app-release-unsigned.apk`
instead — which is what CI checks for, so an unsigned APK can never be archived as a
release. Keep `app/build/outputs/mapping/release/mapping.txt` for any build you ship;
without it a production stack trace is unreadable.

## Continuous integration

`Jenkinsfile` is a self-contained declarative pipeline — point a Jenkins
**Pipeline from SCM** job at this repo and nothing needs pre-installing on the agent
beyond a POSIX shell with `git`, `curl`, `unzip` and `tar`.

It provisions Temurin JDK 17 and the Android SDK (cmdline-tools 23.0, platform-tools,
`android-36`, build-tools `36.0.0`) into `$HOME/.android-ci`, which persists across builds,
then runs the committed Gradle wrapper. Stages: checkout → provision JDK → provision SDK →
`clean testDebugUnitTest assembleDebug` → optionally a signed release.

Test results are published via `junit`; APKs and the R8 mapping file are archived.

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

```
app/src/main/java/com/gowthaman/workout/
  AppViewModel.kt          UI state: selected day, derived day view, dashboard
  MainActivity.kt          single activity; reloads the folder on resume
  data/
    Models.kt              StrengthSet, CardioEntry, sessions, units, formatting
    Food.kt                FoodEntry, purpose and the healthy-to-indulgent scale
    Csv.kt                 CSV reader/writer, header-addressed rows
    CsvStore.kt            all folder I/O through the Storage Access Framework
    Repo.kt                in-memory source of truth with CSV write-through
    Prefs.kt               settings
  logic/
    Overload.kt            the progression engine
    Stats.kt               dashboard aggregates
  ui/                      Compose screens: Day, Log, Exercises, detail, Dashboard, Settings
app/src/test/              66 unit tests, mostly on Overload, Stats and food
```

Design notes worth knowing if you change things:

- **Weights keep the unit they were entered with.** Rows carry `unit`, and display converts.
  Switching kg/lb never rewrites history.
- **Everything is in memory.** A few years of training is well under a megabyte, so the repo
  holds the full data set and rewrites only the months a change touched.
- **Estimated 1RM is Epley** (`weight × (1 + reps/30)`), used to compare sets and detect
  improvement. Unweighted sets compare on reps instead.
- **Food counts are quantity-weighted.** `qty` means occurrences, never portions: two
  coffees in one sitting is `2`, and every total — including the after-21:00 share — counts
  it twice.
- **`logged_at` is UTC epoch millis.** The app reads hour-of-day in the device's zone;
  `analyze.py` converts to the reader's local zone. Treating it as UTC silently shifts
  every late-night entry by your offset.
