# Jotter data

Plain CSV, written by the Jotter Android app. Safe to sync with Syncthing, Dropbox,
Nextcloud or anything else that syncs files, and safe to read with pandas / R / Excel.

## Files

| File                   | Contents                                                  |
|------------------------|-----------------------------------------------------------|
| `strength-YYYY-MM.csv` | One row per **set**, for that calendar month               |
| `cardio-YYYY-MM.csv`   | One row per **cardio session**, for that calendar month     |
| `food-YYYY-MM.csv`     | One row per **food/drink logged**, for that calendar month |
| `exercises.csv`        | The exercise catalog that drives the app's autocomplete    |
| `foods.csv`            | The food catalog, with each item's usual purpose and rating|
| `settings.csv`         | A read-only copy of the app's settings (see below)         |

Data is split by month so that logging a workout only ever rewrites the current month's
file. That keeps sync conflicts small and localised.

## strength-YYYY-MM.csv

| Column       | Type   | Notes                                                              |
|--------------|--------|--------------------------------------------------------------------|
| `id`         | string | Stable row id                                                      |
| `date`       | date   | `YYYY-MM-DD`, the day the work was done                            |
| `exercise`   | string | Free text, matched case-insensitively by the app                   |
| `set_no`     | int    | 1-based set number within that exercise on that day                |
| `reps`       | int    | Repetitions                                                        |
| `weight`     | float  | **Added** load. `0` for a pure bodyweight set                      |
| `unit`       | string | `kg` or `lb` — recorded per row, exactly as entered                |
| `bodyweight` | bool   | `true` when the movement carries bodyweight (pull-ups, dips, ...)  |
| `notes`      | string | Free text, newlines stripped                                       |
| `logged_at`  | int    | Unix epoch millis when the row was saved                           |

A weighted pull-up is `bodyweight=true` with `weight=10`. A barbell squat is
`bodyweight=false` with `weight=100`. A plain push-up is `bodyweight=true`, `weight=0`.

## cardio-YYYY-MM.csv

| Column         | Type   | Notes                                   |
|----------------|--------|-----------------------------------------|
| `id`           | string | Stable row id                           |
| `date`         | date   | `YYYY-MM-DD`                            |
| `activity`     | string | Free text, e.g. `Run`, `Cycling`        |
| `duration_sec` | int    | Session duration in seconds             |
| `distance`     | float  | `0` when distance was not tracked       |
| `dist_unit`    | string | `km` or `mi`                            |
| `notes`        | string | Free text                               |
| `logged_at`    | int    | Unix epoch millis                       |

## food-YYYY-MM.csv

A log of *what* and *how*, deliberately not of calories or nutrients.

| Column      | Type   | Notes                                                        |
|-------------|--------|--------------------------------------------------------------|
| `id`        | string | Stable row id                                                |
| `date`      | date   | `YYYY-MM-DD`                                                 |
| `item`      | string | Free text, matched case-insensitively by the app             |
| `purpose`   | string | `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` or `DRINK`           |
| `scale`     | int    | `1` healthy, `2` everyday, `3` indulgent — the logger's call |
| `qty`       | int    | Occurrences, not portions. Two coffees at once is `2`        |
| `notes`     | string | Free text                                                    |
| `logged_at` | int    | Unix epoch millis when saved — a usable proxy for time of day|

There are no calorie, macro or portion columns, and none are planned. The question this
log answers is "how am I eating", not "how much".

## foods.csv

| Column       | Type   | Notes                                       |
|--------------|--------|---------------------------------------------|
| `name`       | string | Display name                                |
| `purpose`    | string | Remembered default, from the latest entry   |
| `scale`      | int    | Remembered default, from the latest entry   |
| `created_at` | int    | Unix epoch millis of first use              |
| `last_used`  | date   | Most recent day it appears in a log         |
| `uses`       | int    | Total quantity logged                       |

Like `exercises.csv`, this is a rebuilt index — deleting it loses nothing.

## exercises.csv

| Column       | Type   | Notes                                |
|--------------|--------|--------------------------------------|
| `name`       | string | Display name                         |
| `type`       | string | `STRENGTH` or `CARDIO`               |
| `created_at` | int    | Unix epoch millis of first use       |
| `last_used`  | date   | Most recent day it appears in a log  |
| `uses`       | int    | Number of sets / sessions logged     |

This file is a convenience index — it is rebuilt from the log files on every app start,
so deleting it loses nothing.

`README.md` and `analyze.py` are generated and kept up to date by the app — if you change
the app and it ships newer copies, these get rewritten. Keep your own analysis in a
separate file so it is never overwritten.

## settings.csv

A copy of the app's settings, so desktop tools can agree with the app about units and the
overload window instead of guessing.

**This file is read-only.** The app owns these values and rewrites the whole file whenever
any setting changes, so anything you edit here will be overwritten without warning. Change
settings in the app.

| Column  | Notes                        |
|---------|------------------------------|
| `key`   | Setting name                 |
| `value` | Its value                    |

| Key                      | Meaning                                              |
|--------------------------|------------------------------------------------------|
| `overload_period_days`   | 7, 15 or 30 — how often an overload is proposed       |
| `weight_unit`            | `kg` or `lb`                                          |
| `distance_unit`          | `km` or `mi`                                          |
| `weight_increment`       | Smallest loadable step, in `weight_unit`              |
| `rep_min` / `rep_max`    | The double-progression rep window                     |
| `bodyweight_rep_ceiling` | Reps past which bodyweight moves get added load       |
| `user_bodyweight_kg`     | Optional; `0` when unset                              |
| `dashboard_period`       | `WEEK`, `MONTH`, `QUARTER` or `YEAR`                  |

The first row has the key `_note` and restates the warning, so it is visible on opening
the file. Unknown keys are ignored, and anything missing falls back to a default.

## Quick start on a laptop

```bash
pip install pandas
python analyze.py                          # overall summary
python analyze.py --exercise "Bench Press" # one lift's full history
```

Or straight into pandas:

```python
import glob, pandas as pd

# How often did each thing come up this year?
food = pd.concat([pd.read_csv(f) for f in glob.glob("food-*.csv")], ignore_index=True)
print(food.groupby("item")["qty"].sum().sort_values(ascending=False).head(20))
print(food.groupby("purpose")["qty"].sum())

sets = pd.concat([pd.read_csv(f) for f in glob.glob("strength-*.csv")], ignore_index=True)
sets["date"] = pd.to_datetime(sets["date"])

# estimated 1RM progression for one lift
bench = sets[sets.exercise.str.lower() == "bench press"].copy()
bench["e1rm"] = bench.weight * (1 + bench.reps / 30)
print(bench.groupby("date").e1rm.max())

# weekly tonnage
sets["volume"] = sets.weight * sets.reps
print(sets.set_index("date").volume.resample("W-MON").sum())
```

## Syncing notes

- Point your sync tool at this folder. There is no database and no lock file, so there is
  nothing to corrupt.
- If two devices edit the same month while offline, your sync tool will leave a conflict
  copy (e.g. `strength-2026-11.sync-conflict-....csv`) next to the original. The app
  ignores files it does not recognise, so nothing breaks — merge the two by hand when
  convenient.
- Editing a CSV on your laptop is fine. The app re-reads the folder on resume and with the
  refresh action, so external edits show up.

<!-- jotter-docs v4 — this file is generated by the app; edit a copy, not this. -->
