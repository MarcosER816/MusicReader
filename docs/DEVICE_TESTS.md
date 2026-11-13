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
Date tested:
Audio route: not relevant (no audio yet)

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Download the artifact and install the APK (allow installs from unknown apps) | Installs; MusicReader icon (yellow note) in the launcher | |
| 2 | Open the app, tap **About** | Shows `Version 0.0.<run>`, `Build <run>`, `Audio core: Oboe 1.11.0` | |
| 3 | Install the APK of a later run over the first one, without uninstalling | Installs as an update; About shows the higher run number | |
| 4 | Look at the theme | Dark theme; note your preference (dark, light or follow system) | |
| 5 | Use the app a little (back button, rotate, switch apps) | No crash | |
