# MusicReader — Progress

Update this file at the end of every stage. A stage is **done** only when every task and Done item is complete, CI is green, the verification questions were asked, and Mark wrote "Stage N approved".

Status values: `not started` · `in progress` · `waiting for Mark` · `done`

Stage numbers follow Mark's stage requests (D-021): his Stage 1 covered the old stages 1–2 and his Stage 2 the old stage 3, so the later stages moved up by one.

| # | Stage | Branch | Status | PR | Approved |
|---|---|---|---|---|---|
| 0 | Project skeleton: Gradle modules, versions, CI that builds and uploads the APK | stage-00-skeleton | done | [#1](https://github.com/MarcosER816/MusicReader/pull/1) | 2026-11-13 (device checks passed; Mark moved on to Stage 1) |
| 1 | Core model, JSON storage, beat/time helpers, song library, song settings (tempo, tap tempo, time signature), sections (types, descriptions, length, reorder, linked copies) | stage-01-model | done | [#2](https://github.com/MarcosER816/MusicReader/pull/2), [#3](https://github.com/MarcosER816/MusicReader/pull/3) | 2026-11-13 ("Stage 1 approved") |
| 2 | Lyrics editor (per section, two voices), Spanish syllabifier (rules), English syllabifier (Liang + hyph-en-us), syllable view with tap split/merge, edits keep unchanged words | stage-02-lyrics | waiting for Mark | [#4](https://github.com/MarcosER816/MusicReader/pull/4) | |
| 3 | Audio core: C++ Oboe engine, JNI, audio clock, metronome/count-in, latency calibration per route | stage-03-audio | not started | | |
| 4 | DSP: pitch tracking and onset detection, tested on synthetic signals | stage-04-dsp | not started | | |
| 5 | Lyric capture by singing: select word range, count-in, record take, align to syllables, last take wins | stage-05-capture | not started | | |
| 6 | Note editor: per-syllable pitch and duration, free drag with no snapping | stage-06-note-editor | not started | | |
| 7 | Chord dictionary: chord symbols, all voicings, finger numbers, fretboard diagram | stage-07-chord-dictionary | not started | | |
| 8 | Chord editor: place chords on the beat grid above the lyrics, choose voicing, one strumming pattern per section, half-beat grid | stage-08-chord-editor | not started | | |
| 9 | Tab editor: tab events between chords and lyrics, techniques (bend, hammer-on, pull-off, slide, vibrato, harmonics, mute) | stage-09-tab-editor | not started | | |
| 10 | Song viewer: layer checkboxes, chord colors, synchronized glisten, long-press diagram, tab finger colors, slow autoscroll | stage-10-viewer | not started | | |
| 11 | User configuration: chord colors, finger colors, practice lower layers, judge windows | stage-11-settings | not started | | |
| 12 | Practice — singing: right-to-left note line, pitch arrow with trail, gold/green/red, particles | stage-12-practice-sing | not started | | |
| 13 | Practice — chords and tab: right-to-left lanes, timing judge, gold/green/red, particles, lower layers | stage-13-practice-play | not started | | |
| 14 | Recordings: record practice, keep as official (one per type), synced mix playback, export to Downloads | stage-14-recordings | not started | | |
| 15 | Follow mode: lyrics and chords advance as Mark plays the chords | stage-15-follow | not started | | |

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

