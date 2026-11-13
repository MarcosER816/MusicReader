# MusicReader — Progress

Update this file at the end of every stage. A stage is **done** only when every task and Done item is complete, CI is green, the verification questions were asked, and Mark wrote "Stage N approved".

Status values: `not started` · `in progress` · `waiting for Mark` · `done`

| # | Stage | Branch | Status | PR | Approved |
|---|---|---|---|---|---|
| 0 | Project skeleton: Gradle modules, versions, CI that builds and uploads the APK | stage-00-skeleton | done | [#1](https://github.com/MarcosER816/MusicReader/pull/1) | 2026-11-13 (device checks passed; Mark moved on to Stage 1) |
| 1 | Core model, JSON storage, beat/time helpers, song library, song settings (tempo, tap tempo, time signature), sections (types, descriptions, length, reorder, linked copies) | stage-01-model | waiting for Mark | [#2](https://github.com/MarcosER816/MusicReader/pull/2), [#3](https://github.com/MarcosER816/MusicReader/pull/3) | |
| 2 | Merged into Stage 1 by Mark's Stage 1 request (D-019) | — | merged into 1 | | |
| 3 | Lyrics entry: lyric lines, second line, Spanish/English syllable splitting, manual split | stage-03-lyrics | not started | | |
| 4 | Audio core: C++ Oboe engine, JNI, audio clock, metronome/count-in, latency calibration per route | stage-04-audio | not started | | |
| 5 | DSP: pitch tracking and onset detection, tested on synthetic signals | stage-05-dsp | not started | | |
| 6 | Lyric capture by singing: select word range, count-in, record take, align to syllables, last take wins | stage-06-capture | not started | | |
| 7 | Note editor: per-syllable pitch and duration, free drag with no snapping | stage-07-note-editor | not started | | |
| 8 | Chord dictionary: chord symbols, all voicings, finger numbers, fretboard diagram | stage-08-chord-dictionary | not started | | |
| 9 | Chord editor: place chords on the beat grid above the lyrics, choose voicing, one strumming pattern per section, half-beat grid | stage-09-chord-editor | not started | | |
| 10 | Tab editor: tab events between chords and lyrics, techniques (bend, hammer-on, pull-off, slide, vibrato, harmonics, mute) | stage-10-tab-editor | not started | | |
| 11 | Song viewer: layer checkboxes, chord colors, synchronized glisten, long-press diagram, tab finger colors, slow autoscroll | stage-11-viewer | not started | | |
| 12 | User configuration: chord colors, finger colors, practice lower layers, judge windows | stage-12-settings | not started | | |
| 13 | Practice — singing: right-to-left note line, pitch arrow with trail, gold/green/red, particles | stage-13-practice-sing | not started | | |
| 14 | Practice — chords and tab: right-to-left lanes, timing judge, gold/green/red, particles, lower layers | stage-14-practice-play | not started | | |
| 15 | Recordings: record practice, keep as official (one per type), synced mix playback, export to Downloads | stage-15-recordings | not started | | |
| 16 | Follow mode: lyrics and chords advance as Mark plays the chords | stage-16-follow | not started | | |

## Log

### Stage 0 — skeleton (2026-11-13)
- Gradle 9.9.1 wrapper, version catalog, AGP 9.5.1 with built-in Kotlin, Kotlin 2.4.21, Compose BOM 2026.09.00 (D-016).
- Modules: `app`, `core/model`, `core/data`, `core/dsp`, `audio` (C++17, CMake, Oboe 1.11.0 prefab, JNI).
- Dark Material 3 app: Home (empty song list) and About (version, build, Oboe version read through JNI). English and Spanish strings.
- CI `.github/workflows/android.yml`: lint, unit tests and debug APK on every push and PR; APK artifact `MusicReader-<branch>-<run>.apk`.
- Committed debug keystore; versionCode = CI run number, versionName = `0.<stage>.<run>` (D-017).
- README install guide; `Pitch` (MIDI ↔ Hz) in `core/model` with 5 unit tests.
- Device checks passed (DEVICE_TESTS.md). Dark theme only for now.

### Stage 1 — model, library, song settings, sections (2026-11-13)
- `core/model`: every class of DATA_MODEL.md as `@Serializable` immutable data classes; `SongJson` with `SchemaMigrations` (version-by-version upgrade hook, newer versions rejected); `BeatTime` (beats ↔ seconds, bar/beat positions, song length); `TapTempo`; `SongOps` (pure edit operations, linked/independent sections).
- `core/data`: `FileSongRepository`: one `<id>.json` per song, atomic writes (temp file, fsync, rename), list/load/save/delete, summaries as a StateFlow.
- App: navigation-compose; Library (create, rename, duplicate, delete with Undo); Song settings (title, artist, language, tempo slider/−/+/tap tempo, time signature presets and custom); Sections (add with type chips and descriptions, length in bars, drag to reorder, duplicate linked/independent, unlink, delete with Undo, edit type/length/name).
- Tests: model (round trip of a full song, migrations, beat/time, tap tempo, section operations), repository (save/load across instances, atomicity, delete), ViewModels (auto-save, reorder, undo, linked copies, settings).
- Decisions D-018 (beat definition) and D-019.
- Follow-up PR #3: fixed a data-loss bug (the last edits could be lost when leaving a song while its save was in flight); no loading spinner; Robolectric UI flow test (create song → settings → sections → library → stored song → recreate).
