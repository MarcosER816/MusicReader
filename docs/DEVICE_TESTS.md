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
Date tested:
Audio route: not relevant (no audio yet)

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Install over the Stage 0 app; create two songs with several sections; force-stop (Settings → Apps → MusicReader → Force stop) and reopen | Both songs, their settings and sections are still there | |
| 2 | Tap tempo on the song settings screen; change the time signature | A believable BPM after 3–4 taps; bars and section lengths follow the time signature | |
| 3 | Add sections of all six types; read each description; change a section's type afterwards | Names and descriptions match the spec; the type can be changed | |
| 4 | Reorder sections by dragging the ≡ handle | Comfortable, the order is kept after reopening | |
| 5 | Rename, duplicate and delete songs (and Undo); duplicate linked/independent, unlink and delete sections (and Undo) | Everything works; no crash | |

