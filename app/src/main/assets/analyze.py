#!/usr/bin/env python3
"""Starter analysis for a Workout Log data folder.

Run it from inside the synced folder:

    python analyze.py
    python analyze.py --exercise "Bench Press"
    python analyze.py --weeks 26

Requires pandas:  pip install pandas
"""
import argparse
import glob
import os
import sys

try:
    import pandas as pd
except ImportError:
    sys.exit("This script needs pandas:  pip install pandas")

HERE = os.path.dirname(os.path.abspath(__file__))

LB_PER_KG = 2.2046226218
MI_PER_KM = 0.621371192


def _read(pattern):
    """Concatenate every monthly file matching a glob pattern."""
    files = sorted(glob.glob(os.path.join(HERE, pattern)))
    # Skip sync-conflict copies so a conflicted folder does not double-count.
    files = [f for f in files if "conflict" not in os.path.basename(f).lower()]
    if not files:
        return pd.DataFrame()
    df = pd.concat([pd.read_csv(f) for f in files], ignore_index=True)
    df["date"] = pd.to_datetime(df["date"])
    return df.sort_values("date").reset_index(drop=True)


def load_sets():
    """All strength sets, normalised to kg, with e1RM and volume columns added."""
    df = _read("strength-*.csv")
    if df.empty:
        return df
    df["weight_kg"] = df["weight"].where(df.get("unit", "kg") != "lb", df["weight"] / LB_PER_KG)
    df["e1rm_kg"] = (df["weight_kg"] * (1 + df["reps"] / 30)).where(df["weight_kg"] > 0)
    df["volume_kg"] = df["weight_kg"] * df["reps"]
    return df


def load_cardio():
    """All cardio sessions, normalised to km and minutes."""
    df = _read("cardio-*.csv")
    if df.empty:
        return df
    df["distance_km"] = df["distance"].where(
        df.get("dist_unit", "km") != "mi", df["distance"] / MI_PER_KM
    )
    df["minutes"] = df["duration_sec"] / 60.0
    return df


def show_exercise(sets, name):
    sel = sets[sets["exercise"].str.lower() == name.lower()]
    if sel.empty:
        known = ", ".join(sorted(sets["exercise"].unique()))
        sys.exit("No sets for %r.\nKnown exercises: %s" % (name, known))

    per_day = sel.groupby("date").agg(
        sets=("reps", "size"),
        total_reps=("reps", "sum"),
        top_weight_kg=("weight_kg", "max"),
        best_e1rm_kg=("e1rm_kg", "max"),
        volume_kg=("volume_kg", "sum"),
    )
    print("== %s ==" % sel["exercise"].iloc[-1])
    print(per_day.round(1).to_string())
    print()
    print("-- every set --")
    cols = ["date", "set_no", "reps", "weight", "unit", "bodyweight", "notes"]
    ordered = sel.sort_values(["date", "set_no"])
    print(ordered[[c for c in cols if c in ordered.columns]].to_string(index=False))


def show_summary(sets, cardio, weeks):
    if not sets.empty:
        print("== Strength ==")
        print("sets logged    : %d" % len(sets))
        print("training days  : %d" % sets["date"].nunique())
        print("exercises used : %d" % sets["exercise"].nunique())
        print("date range     : %s -> %s" % (sets["date"].min().date(), sets["date"].max().date()))
        print()

        print("-- per exercise --")
        per_ex = sets.groupby("exercise").agg(
            sets=("reps", "size"),
            total_reps=("reps", "sum"),
            best_e1rm_kg=("e1rm_kg", "max"),
            last_done=("date", "max"),
        ).sort_values("sets", ascending=False)
        per_ex["last_done"] = per_ex["last_done"].dt.date
        print(per_ex.round(1).to_string())
        print()

        # pandas labels a W-MON bucket with the week's END date; the app's chart labels the
        # same bucket with its Monday start. Same numbers, dates shifted by six days.
        print("-- weekly tonnage (kg), week ending, last %d weeks --" % weeks)
        weekly = sets.set_index("date")["volume_kg"].resample("W-MON").sum()
        print(weekly.tail(weeks).round(0).to_string())
        print()

    if not cardio.empty:
        print("== Cardio ==")
        print("sessions       : %d" % len(cardio))
        print("total time     : %.1f h" % (cardio["minutes"].sum() / 60))
        print("total distance : %.1f km" % cardio["distance_km"].sum())
        print()
        print("-- per activity --")
        print(
            cardio.groupby("activity").agg(
                sessions=("minutes", "size"),
                minutes=("minutes", "sum"),
                km=("distance_km", "sum"),
            ).round(1).to_string()
        )
        print()
        print("-- weekly cardio minutes, week ending, last %d weeks --" % weeks)
        print(cardio.set_index("date")["minutes"].resample("W-MON").sum().tail(weeks).round(0).to_string())


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--exercise", help="show the full history of one exercise")
    ap.add_argument("--weeks", type=int, default=12, help="how many weeks of trend to print")
    args = ap.parse_args()

    sets = load_sets()
    cardio = load_cardio()

    if sets.empty and cardio.empty:
        sys.exit("No strength-*.csv or cardio-*.csv files found in %s" % HERE)

    if args.exercise:
        if sets.empty:
            sys.exit("No strength data in this folder.")
        show_exercise(sets, args.exercise)
    else:
        show_summary(sets, cardio, args.weeks)


if __name__ == "__main__":
    main()
