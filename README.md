# MusicReader

A personal Android app for guitar players and singers: lyrics, chords, tabs and practice.
Sideloaded (not on the Play Store).

- What the app will do: [docs/SPEC.md](docs/SPEC.md)
- Where the work is: [PROGRESS.md](PROGRESS.md)
- Rules for coding agents: [CLAUDE.md](CLAUDE.md)

## Install the app on your phone

Every push to GitHub builds a new APK (the Android install file). You download it from
GitHub Actions and install it on the phone. You only need to do steps 1–2 once.

### One-time setup

1. **Sign in to GitHub on the device you download with.** GitHub only lets signed-in users
   download build artifacts. You can download on the phone (browser) or on a computer and copy
   the file to the phone.
2. **Allow installs from your browser or file manager.** The first time you open an APK,
   Android (HyperOS) asks for permission: tap **Settings**, turn on
   **Allow from this source**, then go back. On HyperOS you may also see a security scan
   screen; tap **Install anyway** / **Continue**.

### Every new version

1. Open the repository on GitHub and tap the **Actions** tab.
2. Tap the newest run of **Android CI** with a green check mark. Pick the run for the branch
   you want to test (the branch name is shown under the run title). The stage branch,
   e.g. `stage-00-skeleton`, is the one to test during a stage.
3. Scroll down to **Artifacts** and tap the one named
   `MusicReader-<branch>-<run number>.apk`. GitHub downloads it as a **.zip** file.
4. Open the downloaded zip (Files app → Downloads) and extract it. Inside is
   `MusicReader-<branch>-<run number>.apk`.
5. Tap the `.apk` file and then **Install** (or **Update**).
6. Open MusicReader. Go to **About** to check the version: `0.<stage>.<run number>`.

### Updates keep your data

All builds are signed with the same key (`keystore/debug.keystore`, committed on purpose and
not secret), and every build has a higher version code (the CI run number). So a new APK
installs **over** the old one as an update and keeps your songs and recordings. Do **not**
uninstall first: uninstalling deletes the app's data.

If Android ever says the app "conflicts with an existing package", the installed copy was
signed with a different key (for example a build from somewhere else). Only then uninstall
it, and know that this removes its data.

## Build it yourself (optional)

Requirements: JDK 21 and the Android SDK (`ANDROID_HOME` set, or `sdk.dir` in
`local.properties`). The build downloads the NDK and CMake versions it needs.

```bash
./gradlew lint testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

| Module | What it holds |
|---|---|
| `app` | Android app, Compose Material 3 UI |
| `core/model` | Pure Kotlin data model and music logic |
| `core/data` | Song files (JSON) and settings (DataStore) |
| `core/dsp` | Pure Kotlin signal processing (pitch, onsets) |
| `core/lyrics` | Pure Kotlin lyrics parsing and syllable splitting (Spanish rules, English Liang patterns) |
| `audio` | C++17 audio core on Oboe, called from Kotlin through JNI |

## License

MIT, see [LICENSE](LICENSE).

The English hyphenation patterns (`core/lyrics/src/main/resources/hyphenation/hyph-en-us.*`) are Copyright (C) 1990, 2004, 2005 Gerard D.C. Kuiken, distributed under the permissive notice in `hyph-en-us.LICENSE.txt`.

The chord shapes (`core/data/src/main/assets/chords/guitar.json`) are converted from [chords-db](https://github.com/tombatossals/chords-db) by David Rubert, MIT License (`LICENSE-chords-db.txt` next to the file). To regenerate: `python3 tools/convert_chords_db.py <chords-db>/lib/guitar.json <chords-db>/LICENSE <commit>`.

