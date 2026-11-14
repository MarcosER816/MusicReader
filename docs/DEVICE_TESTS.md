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
Date tested: 2026-11-14 (round 1, APK run 55)
Audio route: not relevant (no audio yet)

How: a song's Sections → ⋮ on a section → "Chords". Viewer: "View", long-press a chord.

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Look up ten chords you really use | All there, with the shapes you play | OK |
| 2 | Finger numbers and colors on the diagrams | Correct | OK |
| 3 | Tap a beat to add a chord | Lands on the tapped beat (half-beat grid) | Round 1: wants chords per syllable → round 2 (D-025) |
| 4 | Lyrics under the chord grid | Clear which syllable/word is under each chord | Round 1: not totally clear → round 2 (D-025) |
| 5 | Viewer long-press | Shows the chosen shape | OK |
| 6 | Update over Stage 3 | No data lost, no crash | OK; change wanted: chords per syllable |

Round 2 (2026-11-14, APK run 59):

| # | Check | Result |
|---|---|---|
| 1 | Chords you use, with your shapes | OK |
| 2 | Finger numbers and colors | Gm7 showed the same finger three times → fixed in round 3 (D-026: missing barres inferred) |
| 3 | Chord lands on the tapped syllable; half beats in sections without lyrics | OK — yes, keep half beats |
| 4 | Clear which syllable is under each chord | OK |
| 5 | Viewer long-press shows the chosen shape | OK |
| 6 | Update without losing data | OK |

Round 3 (2026-11-14, APK run 61):

| # | Check | Result |
|---|---|---|
| 1 | Chords you use, with your shapes | OK |
| 2 | Finger numbers and colors (Gm7 and others) | OK |
| 3 | Chord lands on the tapped syllable | OK |
| 4 | Clear which syllable is under each chord | OK |
| 5 | Viewer long-press shows the chosen shape | OK |
| 6 | Update without losing data; crash or change | OK — no crash, no change |

### Stage 5 — tabs
APK: `MusicReader-stage-05-tabs-72.apk` or later (PR [#7](https://github.com/MarcosER816/MusicReader/pull/7), branch `stage-05-tabs`)
Date tested: 2026-11-14 (APK run 72)
Audio route: not relevant (no audio yet)

How: a song's Sections → ⋮ on a section → "Tab". Viewer: "View" with Tabs checked.

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Type a riff with a bend, hammer-on, pull-off, slide, vibrato and a harmonic | Each one readable | OK |
| 2 | Order in the viewer | Chords, then tab, then lyrics | OK |
| 3 | Finger color on each number | Clear | OK |
| 4 | Typing speed (number pad, "Save + next") | Fast enough | OK — no other input method wanted |
| 5 | Update over Stage 4 | No data lost, no crash | OK |

### Stage 6 — user configuration
APK: `MusicReader-stage-06-settings-81.apk` or later (PR [#8](https://github.com/MarcosER816/MusicReader/pull/8), branch `stage-06-settings`)
Date tested: 2026-11-14 (APK run 81)
Audio route: not relevant (no audio yet)

How: Library ⋮ → "Settings".

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Change the color of C; open a song's viewer and the chord editor | C chords in the new color in both, and in the chord picker | OK |
| 2 | Change a finger color; look at a tab and at chord diagrams (viewer long-press, chord picker) | The finger's numbers and dots in the new color | OK |
| 3 | Force-stop and reopen; then "Reset to default" and "Reset all" | Colors kept after restart; resets restore the defaults | OK |
| 4 | Switch the language to Español and back | Every screen in the chosen language | OK |
| 5 | Update over Stage 5 | No data lost, no crash | OK — no data lost, no crash, no change wanted |

### Stage 7 — audio core
APK: `MusicReader-stage-07-audio-90.apk` or later (PR [#9](https://github.com/MarcosER816/MusicReader/pull/9), branch `stage-07-audio`)
Date tested:
Audio route: speaker and wired headphones

How:
- Metronome: open a song → metronome icon in the top bar (set tempo and time signature in the song's Settings).
- Calibration: Library ⋮ → Settings → Audio → Latency calibration.
- Diagnostics: Library ⋮ → Settings → Audio → Audio diagnostics.
- Reference: Google's OboeTester app (Play Store or GitHub releases) → "Round Trip Latency".

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | OboeTester round-trip latency on the speaker and on wired headphones | Two numbers in ms | |
| 2 | MusicReader automatic calibration on the same routes | Within about 5 ms of OboeTester; confidence high | |
| 3 | Metronome for two minutes at 60 BPM and at 180 BPM | Steady, accent on beat 1, the light matches the sound | |
| 4 | Diagnostics after five minutes of metronome | Underrun count (ideally 0) | |
| 5 | Plug and unplug headphones while the metronome runs | Keeps playing on the new route; route updates in Diagnostics | |
| 6 | Update over Stage 6 | No data lost, no crash | |
