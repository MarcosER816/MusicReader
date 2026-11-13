# MusicReader — agent rules

MusicReader is a personal, sideloaded Android app for guitar players and singers. Owner: Mark (knows C and Python; tests on a POCO F7 Ultra, Android 15 / HyperOS, 120 Hz).

- Spec: [docs/SPEC.md](docs/SPEC.md). Model: [docs/DATA_MODEL.md](docs/DATA_MODEL.md). Decisions: [docs/DECISIONS.md](docs/DECISIONS.md). Progress: [PROGRESS.md](PROGRESS.md). Also: [docs/BACKLOG.md](docs/BACKLOG.md), [docs/DEVICE_TESTS.md](docs/DEVICE_TESTS.md).

## Stack
- Kotlin 2.4.x, Jetpack Compose + Material 3 (Compose BOM 2026.09.00 or newer stable), AGP 9.5.x with built-in Kotlin, compileSdk 37, targetSdk 36, minSdk 29.
- C++17 audio core on Oboe 1.11.0 via JNI.
- kotlinx.serialization JSON files for songs, DataStore for settings.
- Check exact versions on the official release pages and record them in DECISIONS.md.

## Modules
`app`, `core/model` (pure Kotlin), `core/data`, `core/dsp`, `audio` (C++), `feature/*` as needed.

## Build and verify
- Build: `./gradlew lint testDebugUnitTest assembleDebug`. CI must upload an installable APK on every push.
- No emulator or device: verify with tests and CI, then ask Mark to test the APK.

## Workflow
- One stage per branch (`stage-NN-name`) and one PR into main.
- NEVER STOP A STAGE EARLY: a stage is finished only when every task and every Done item is complete, CI is green, the verification questions have been asked, and Mark has written "Stage N approved". No TODOs, stubs or placeholders. If CI fails, fix and push again. If Mark reports a problem, fix it on the same branch and ask again.
- Never start the next stage on your own. Never change the spec silently; ask.
- Update PROGRESS.md at the end of every stage.

## Code rules
- Licenses: only Apache-2.0, MIT, BSD, zlib. No GPL/LGPL/AGPL code (TarsosDSP, aubio, Essentia): read them for reference only.
- All musical timing comes from the audio clock, never from frame time. No allocations in audio callbacks or per-frame drawing.
- Unit-test every pure-logic piece (model, music theory, syllables, DSP on synthetic signals, alignment, judging).
- Immutable data classes, StateFlow UI state, no logic in composables.
