#!/usr/bin/env python3
"""Convert chords-db's guitar dictionary into the asset MusicReader bundles.

Source: https://github.com/tombatossals/chords-db, file lib/guitar.json (MIT License,
Copyright (c) 2016 David Rubert).

Usage:
    python3 tools/convert_chords_db.py <path/to/lib/guitar.json> <path/to/chords-db/LICENSE> <source-commit>

Writes:
    core/data/src/main/assets/chords/guitar.json          the dictionary
    core/data/src/main/assets/chords/LICENSE-chords-db.txt  the MIT notice

Layout of lib/guitar.json (checked against the real file, commit df06fa7):
    main     {"strings": 6, "fretsOnChord": 4, "name": "guitar"}
    tunings  {"standard": ["E2", "A2", "D3", "G3", "B3", "E4"]}
    keys     ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
    suffixes ["major", "minor", "dim", ..., "/E", "m/B", ...]
    chords   {"C": [...], "Csharp": [...], ...}   one list per key, entries:
        {"key": "C#", "suffix": "major", "positions": [
            {"frets": [-1, 3, 2, 0, 1, 0],   low E first; relative to baseFret, -1 muted, 0 open
             "fingers": [0, 3, 2, 0, 1, 0],  0 none, 1-4 index..pinky
             "baseFret": 1,
             "barres": [1],                  relative frets that are barred
             "capo": true,                   optional: the barre covers the whole neck width
             "midi": [48, 52, 55, 60, 64]}]} sounding notes, low to high

The output keeps the frets relative (the app converts them, and tests that conversion) and
only cleans and checks the data:
    - every position must have 6 frets and 6 fingers, frets in -1..fretsOnChord, baseFret >= 1;
    - midi must equal standard tuning + absolute fret for every sounding string;
    - a finger of -1 (one data slip, on a muted string) becomes 0.
"""

import json
import sys
from pathlib import Path

STANDARD_TUNING = [40, 45, 50, 55, 59, 64]
OUT_DIR = Path(__file__).resolve().parent.parent / "core/data/src/main/assets/chords"


def absolute(fret: int, base_fret: int) -> int:
    if fret <= 0:
        return fret
    return base_fret + fret - 1


def convert(source: dict, commit: str) -> dict:
    frets_on_chord = source["main"]["fretsOnChord"]
    if source["main"]["strings"] != 6:
        raise ValueError("expected a 6-string guitar dictionary")
    chords = []
    fixes = 0
    for group in source["chords"].values():
        for chord in group:
            positions = []
            for index, p in enumerate(chord["positions"]):
                frets, fingers, base = p["frets"], p["fingers"], p["baseFret"]
                where = f'{chord["key"]} {chord["suffix"]} #{index}'
                if len(frets) != 6 or len(fingers) != 6:
                    raise ValueError(f"{where}: expected 6 strings")
                if base < 1 or any(f < -1 or f > frets_on_chord for f in frets):
                    raise ValueError(f"{where}: fret out of range")
                midi = [STANDARD_TUNING[s] + absolute(f, base) for s, f in enumerate(frets) if f >= 0]
                if midi != p["midi"]:
                    raise ValueError(f"{where}: midi {p['midi']} does not match frets ({midi})")
                cleaned = [max(0, f) for f in fingers]
                fixes += cleaned != fingers
                if any(f > 4 for f in cleaned):
                    raise ValueError(f"{where}: finger out of range")
                positions.append({
                    "frets": frets,
                    "fingers": cleaned,
                    "baseFret": base,
                    "barres": p["barres"],
                    "capo": bool(p.get("capo", False)),
                    "midi": p["midi"],
                })
            chords.append({"key": chord["key"], "suffix": chord["suffix"], "positions": positions})
    print(f"{len(chords)} chords, {sum(len(c['positions']) for c in chords)} positions, {fixes} finger fixes")
    return {
        "source": f"tombatossals/chords-db lib/guitar.json at commit {commit} (MIT License, see LICENSE-chords-db.txt)",
        "fretsOnChord": frets_on_chord,
        "tuning": STANDARD_TUNING,
        "keys": source["keys"],
        "chords": chords,
    }


def main() -> None:
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    source_path, license_path, commit = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
    converted = convert(json.loads(source_path.read_text(encoding="utf-8")), commit)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "guitar.json").write_text(json.dumps(converted, separators=(",", ":")), encoding="utf-8")
    notice = (
        "The chord dictionary guitar.json is converted from tombatossals/chords-db\n"
        f"(https://github.com/tombatossals/chords-db, lib/guitar.json, commit {commit}).\n\n"
    )
    (OUT_DIR / "LICENSE-chords-db.txt").write_text(notice + license_path.read_text(encoding="utf-8"), encoding="utf-8")
    print(f"wrote {OUT_DIR}")


if __name__ == "__main__":
    main()
