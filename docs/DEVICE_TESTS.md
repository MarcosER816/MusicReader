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

_No stages tested yet._
