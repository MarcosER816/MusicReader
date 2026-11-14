# MusicReader — Progress

Update this file at the end of every stage. A stage is **done** only when every task and Done item is complete, CI is green, the verification questions were asked, and Mark wrote "Stage N approved".

Status values: `not started` · `in progress` · `waiting for Mark` · `done`

Stage numbers follow Mark's stage requests (D-021, D-023). Stages 0–3 are as Mark asked; Mark's Stage 3 request says chords and tabs come in Stages 4–5. The order after that is the remaining plan, to be renumbered if Mark asks for a different order.

| # | Stage | Branch | Status | PR | Approved |
|---|---|---|---|---|---|
| 0 | Project skeleton: Gradle modules, versions, CI that builds and uploads the APK | stage-00-skeleton | done | [#1](https://github.com/MarcosER816/MusicReader/pull/1) | 2026-11-13 (device checks passed; Mark moved on to Stage 1) |
| 1 | Core model, JSON storage, beat/time helpers, song library, song settings (tempo, tap tempo, time signature), sections (types, descriptions, length, reorder, linked copies) | stage-01-model | done | [#2](https://github.com/MarcosER816/MusicReader/pull/2), [#3](https://github.com/MarcosER816/MusicReader/pull/3) | 2026-11-13 ("Stage 1 approved") |
| 2 | Lyrics editor (per section, two voices), Spanish syllabifier (rules), English syllabifier (Liang + hyph-en-us), syllable view with tap split/merge, edits keep unchanged words | stage-02-lyrics | done | [#4](https://github.com/MarcosER816/MusicReader/pull/4) | 2026-11-13 ("Stage 2 approved") |
| 3 | Song viewer: sections with chords/tab/lyrics on shared beat positions, layer checkboxes, chord colors, synchronized glisten, slow autoscroll, long-press chord sheet, font size, demo song, screenshot tests | stage-03-viewer | waiting for Mark | [#5](https://github.com/MarcosER816/MusicReader/pull/5) | |
| 4 | Chords: chord dictionary (all voicings, finger numbers, fretboard diagram), chord editor on the half-beat grid above the lyrics, one strumming pattern per section, long-press diagram in the viewer | stage-04-chords | not started | | |
| 5 | Tab editor: tab events between chords and lyrics, techniques (bend, hammer-on, pull-off, slide, vibrato, harmonics, mute) | stage-05-tab-editor | not started | | |
| 6 | Audio core: C++ Oboe engine, JNI, audio clock, metronome/count-in, latency calibration per route | stage-06-audio | not started | | |
| 7 | DSP: pitch tracking and onset detection, tested on synthetic signals | stage-07-dsp | not started | | |
| 8 | Lyric capture by singing: select word range, count-in, record take, align to syllables, last take wins | stage-08-capture | not started | | |
| 9 | Note editor: per-syllable pitch and duration, free drag with no snapping | stage-09-note-editor | not started | | |
| 10 | User configuration: chord colors, finger colors, practice lower layers, judge windows | stage-10-settings | not started | | |
| 11 | Practice — singing: right-to-left note line, pitch arrow with trail, gold/green/red, particles | stage-11-practice-sing | not started | | |
| 12 | Practice — chords and tab: right-to-left lanes, timing judge, gold/green/red, particles, lower layers | stage-12-practice-play | not started | | |
| 13 | Recordings: record practice, keep as official (one per type), synced mix playback, export to Downloads | stage-13-recordings | not started | | |
| 14 | Follow mode: lyrics and chords advance as Mark plays the chords | stage-14-follow | not started | | |

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

### Stage 2 — lyrics and syllables (2026-11-13)
- Stage 1 approved by Mark; device results in DEVICE_TESTS.md.
- New module `core/lyrics`: `SpanishSyllabifier` (rules), `EnglishHyphenator` (Liang + hyph-en-us, with a vowel rule and a fallback for singing), `WordSyllabifier` (keeps capitals and punctuation), `LyricsOps` (text ↔ lines/words/syllables with LCS merge, manual split/merge/reset, re-split on language change).
- Lyrics screen per section: main and second voice, text editor, syllable view (tap between letters to split, tap a dot to join, long-press to reset). Section cards show the first lyric line and open the lyrics.
- Tests: 84 Spanish words, 69 English words, lyrics operations, ViewModel lyrics flow, Robolectric UI flow (type lyrics → syllables → join by tap → restart keeps it).
- Decisions D-020 (syllable splitting, hyph-en-us license) and D-021 (lyrics editing, stage renumbering).
- Round 2 after Mark's device test: English syllables placed as sung (ma-king, des-pite, o-ther, you), second voice per line (`LyricLine.pairedLineId`), clearer syllable hint; hyph-en-us license exception accepted (D-020, D-022).

### Stage 3 — song viewer (2026-11-13)
- Stage 2 approved by Mark (round 2 device test all OK).
- `SheetLayoutBuilder` (core/model): rows per lyric line on a shared beat grid; chords, tab notes and lyrics placed by beat; `TabNotation`.
- `DataStoreSettingsRepository` (core/data): `AppSettings` in DataStore (layers, autoscroll speed, new `viewerFontScale`).
- Viewer screen ("View" on a song): layer checkboxes (last one locked), root-colored chord labels with one shared glisten, tab lane in finger colors, lyrics and second voice, bar lines, frame-driven autoscroll with logarithmic speed and pause on touch, text size, long-press chord sheet.
- Debug "Load demo song" (every root letter incl. D#, Bb9, Cmaj7; tab with fingers 0–4 and techniques).
- Tests: layout, tab notation, demo song, settings DataStore, viewer ViewModel, Roborazzi screenshots (all layers, lyrics + chords, tab only). Decision D-023.

