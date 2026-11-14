# MusicReader — Device tests

Nothing runs on an emulator or a device during development. Each stage ends with a list of checks that Mark runs on the phone with the APK from CI.

**Test device:** POCO F7 Ultra, Android 15 / HyperOS, 120 Hz display.

## How to install the APK

1. Open the GitHub Actions run for the stage's latest commit.
2. Download the `app-debug` artifact (a zip) and unzip it.
3. Copy the `.apk` to the phone and open it (allow "install unknown apps" for the file manager once).
4. If the install fails because of a signature mismatch, uninstall the old MusicReader first. Note: uninstalling deletes the app's songs.

## How to report

For each check, write `OK` or describe what went wrong (what you did, what you expected, what happened; a screenshot or screen recording helps). Write "Stage N approved" when all checks pass.

## Template for each stage

```
### Stage NN — name
APK: commit <sha>, CI run <link>
Date tested:
Audio route (speaker / wired / Bluetooth / USB):

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | | | |
```

## Results

### Stage 0 — skeleton
APK: from PR [#1](https://github.com/MarcosER816/MusicReader/pull/1), branch `stage-00-skeleton`
Date tested: 2026-11-13
Audio route: not relevant (no audio yet)

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Download the artifact and install the APK (allow installs from unknown apps) | Installs; MusicReader icon (yellow note) in the launcher | OK |
| 2 | Open the app, tap **About** | Shows `Version 0.0.<run>`, `Build <run>`, `Audio core: Oboe 1.11.0` | OK (0.0.5) |
| 3 | Install the APK of a later run over the first one, without uninstalling | Installs as an update; About shows the higher run number | OK |
| 4 | Look at the theme | Dark theme; note your preference (dark, light or follow system) | OK — dark only for now |
| 5 | Use the app a little (back button, rotate, switch apps) | No crash | OK |

### Stage 1 — model, library, song settings, sections
APK: `MusicReader-stage-01-model-24.apk` or later (PR [#3](https://github.com/MarcosER816/MusicReader/pull/3), branch `stage-01-model`)
Date tested: 2026-11-13
Audio route: not relevant (no audio yet)

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Install over the Stage 0 app; create two songs with several sections; force-stop (Settings → Apps → MusicReader → Force stop) and reopen | Both songs, their settings and sections are still there | OK |
| 2 | Tap tempo on the song settings screen; change the time signature | A believable BPM after 3–4 taps; bars and section lengths follow the time signature | OK |
| 3 | Add sections of all six types; read each description; change a section's type afterwards | Names and descriptions match the spec; the type can be changed | OK (type can be changed) |
| 4 | Reorder sections by dragging the ≡ handle | Comfortable, the order is kept after reopening | OK |
| 5 | Rename, duplicate and delete songs (and Undo); duplicate linked/independent, unlink and delete sections (and Undo) | Everything works; no crash | OK (updated without losing data; no crash; no changes wanted) |

### Stage 2 — lyrics and syllables
APK: `MusicReader-stage-02-lyrics-31.apk` or later (PR [#4](https://github.com/MarcosER816/MusicReader/pull/4), branch `stage-02-lyrics`)
Date tested: 2026-11-13 (round 1, APK run 31)
Audio route: not relevant (no audio yet)

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Paste a Spanish song and an English song (song language set in Settings), open Syllables | Most words split correctly; send the wrong ones | Round 1: English wrong: y-ou, mak-ing, de-spite, y-ou'll, oth-er → fixed in round 2 (D-022) |
| 2 | Split by tapping between letters and join by tapping a dot, with one hand | Easy to hit | Round 1: unclear what "splitting and merging" means → explained; hint text rewritten |
| 3 | Second voice: write it while the main voice shows below | Works as meant (D-003) | Round 1: wanted per line, not per verse → changed in round 2 (D-022) |
| 4 | Punctuation and capitals | Kept as typed — or say what you prefer | OK: keep as typed |
| 5 | Update over Stage 1, force-stop, reopen: lyrics and manual splits still there | No data lost, no crash | OK: no data lost, no crash |

Round 2 (2026-11-13, APK run 35):

| # | Check | Result |
|---|---|---|
| 1 | Spanish and English songs split | OK — everything split correctly |
| 2 | Split / join by tap with one hand | OK |
| 3 | Second voice per line | OK — works as meant |
| 4 | Punctuation and capitals | OK — nothing else to change |
| 5 | Update without losing data | OK |

### Stage 3 — song viewer
APK: `MusicReader-stage-03-viewer-46.apk` or later (PR [#5](https://github.com/MarcosER816/MusicReader/pull/5), branch `stage-03-viewer`)
Date tested: 2026-11-14
Audio route: not relevant (no audio yet)

How: Library ⋮ → "Load demo song (debug)" → "View".

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Autoscroll at the slowest speed | Slow enough and smooth | OK |
| 2 | Chord colors on the dark background | C red, D orange, E yellow, F lime, G sky blue, A magenta, B lavender; readable | OK |
| 3 | Glisten | All chords shine together; smooth at 120 Hz | OK |
| 4 | Try to uncheck all three checkboxes | The last one cannot be unchecked | OK — the last one turns gray |
| 5 | Text sizes ("Aa") at arm's length | Comfortable | OK |
| 6 | Update over Stage 2, reopen songs | No data lost, no crash | OK — no crash |

### Stage 4 — chords
APK: `MusicReader-stage-04-chords-55.apk` or later (PR [#6](https://github.com/MarcosER816/MusicReader/pull/6), branch `stage-04-chords`)
Date tested:
Audio route: not relevant (no audio yet)

How: a song's Sections → ⋮ on a section → "Chords". Viewer: "View", long-press a chord.

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Look up ten chords you really use | All there, with the shapes you play | |
| 2 | Finger numbers and colors on the diagrams | Correct | |
| 3 | Tap a beat to add a chord | Lands on the tapped beat (half-beat grid) | |
| 4 | Lyrics under the chord grid | Clear which syllable/word is under each chord | |
| 5 | Viewer long-press | Shows the chosen shape | |
| 6 | Update over Stage 3 | No data lost, no crash | |

