# MusicReader — Decisions

Every decision that changes or clarifies the spec, the data model or the stack is recorded here with its date. Newest decisions go at the end of each section.

## D-001 — Toolchain versions (2026-11-13)

Checked on 2026-11-13 against the official repositories (Google Maven `dl.google.com/android/maven2`, Maven Central, `services.gradle.org`) and release pages. Use these exact versions in Stage 0. Re-check before upgrading and record the change here.

| Component | Version | Source / note |
|---|---|---|
| Kotlin (Gradle plugin, Compose compiler plugin) | 2.4.21 | latest 2.4.x on Maven Central |
| Android Gradle Plugin | 9.5.1 | latest 9.5.x stable on Google Maven; built-in Kotlin; max API 37 |
| Gradle wrapper | 9.9.1 | current Gradle release; AGP 9.5 needs at least the version its release notes list, confirm in Stage 0 |
| Compose BOM | 2026.09.00 | latest stable BOM on Google Maven |
| Material 3 | from the Compose BOM | |
| kotlinx.serialization JSON | 1.13.0 | latest release on Maven Central |
| AndroidX DataStore | 1.3.0 | latest stable on Google Maven |
| Oboe | 1.11.0 | `com.google.oboe:oboe` prefab AAR on Google Maven |
| compileSdk / targetSdk / minSdk | 37 / 36 / 29 | project rule |
| C++ standard | C++17 | project rule |
| NDK, CMake, JDK | to be fixed in Stage 0 | pick the versions AGP 9.5.1 ships with by default and record them here |

Sources:
- AGP 9.5.0 release notes: https://developer.android.com/build/releases/agp-9-5-0-release-notes
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Kotlin 2.4.20 release post: https://blog.jetbrains.com/kotlin/2026/09/kotlin-2-4-20-released/
- Oboe releases: https://github.com/google/oboe/releases

## D-002 — Repository files (2026-11-13)

The repository starts with documentation only, no app code: `docs/SPEC.md`, `docs/DATA_MODEL.md`, `docs/DECISIONS.md`, `docs/BACKLOG.md`, `docs/DEVICE_TESTS.md`, `PROGRESS.md`, `CLAUDE.md`, `AGENTS.md`, `LICENSE` (added after D-004). Stages 0–16 are listed in PROGRESS.md.

## Answers to the open questions (Mark, 2026-11-13)

The questions and Mark's answers, word for word, are below. D-003 to D-015 turn each answer into a decision. SPEC.md Part 2 and DATA_MODEL.md were updated to match.

### D-003 — Two lyric lines = two voices at the same time (Q1: "(a) two voices sung at the same time")
- `LyricLine.voice` 0 = main, 1 = second voice, both on the same beats.
- A phone microphone cannot separate two people singing together, so each voice is captured in its own takes (sing voice 0, then voice 1).
- While writing or editing voice 1, voice 0 is shown below it (spec: "when writing the second then the first apears bellow it").
- Singing practice picks which voice to practice; the other voice can be shown as a guide.

### D-004 — Repository settings (Q10: "public, whatever you see more combinient")
- Public repository on GitHub.
- License: **MIT**. It is short, permissive and compatible with the allowed dependency licenses (Apache-2.0, MIT, BSD, zlib). File: `LICENSE`.

### D-005 — Tuning, capo, left-handed (Q2: "tuning standart, Left-handed diagrams")
- Version 1: standard tuning only (E A D G B E, MIDI 40 45 50 55 59 64).
- Capo: not requested, so there is no capo setting in version 1. `Instrument.capo` stays in the model, fixed at 0, so it can be added later without a schema change (BACKLOG).
- Left-handed diagrams: yes. Setting `leftHanded` mirrors fretboard diagrams (chord long-press, dictionary, tab finger view).

### D-006 — App language (Q3: "both")
- UI in English and Spanish (Android string resources `values/` and `values-es/`). Setting `uiLanguage`: follow system, English or Spanish.

### D-007 — Syllable splitting (Q3: "syllable splitting for Spanish and English only")
- Automatic splitter for Spanish (rule-based) and English (with hand-split override). `Song.language` is `es` or `en`. Manual split is always available.

### D-008 — Strumming (Q4: "one pattern per section")
- One `StrumPattern` per section (`SectionContent.strum`), one bar long, repeating every bar and strumming whichever chord is current. `ChordEvent.strum` removed.

### D-009 — Chord grid (Q5: "half beats too")
- Chords snap to a half-beat grid (`beat` is a multiple of 0.5).

### D-010 — Syllable pitch (Q8: "one initial note, one final note and pitch curve")
- Each syllable stores `pitchStartMidi`, `pitchEndMidi` (continuous) and `pitchCurve` (points `{t 0..1, midi}` from the sung take). `pitchMidi` removed.
- The note editor drags the initial note, the final note and the duration freely with no snapping. Moving an end note reshapes the curve linearly so it still starts and ends on the two notes; changing the duration stretches it.
- The singing practice line and judge follow the curve (straight glide when there is no curve).

### D-011 — Chord colors with accidentals (Q6: "yes")
- Color is chosen by root letter only: Bb uses B's color, D# uses D's color. The full name is still shown.

### D-012 — Finger colors (Q7: "yes, not thumb")
- Default finger colors accepted. No thumb: fingers are 0 (open) and 1–4. Finger 5 removed from TabEvent and Settings.

### D-013 — Repeated sections (Q11: "copies stay linked unless the user changes it")
- The song timeline (`Section`: type, label) is separate from the content (`SectionContent`: length, strum, lyrics, chords, tab). Duplicating a section makes a linked copy that shares the same content, so editing one edits all. "Unlink" makes an independent copy.

### D-014 — Exported mix (Q9: "M4A, no metronome")
- The mix of official recordings is exported as M4A (AAC, 48 kHz) to Downloads, without the metronome click. Recordings inside the app stay WAV.

### D-015 — Working interpretation (Q12: "No")
- No changes requested; the working interpretation is confirmed together with D-003 to D-014.

### Questions as asked

1. "Up to two lines of lyrics": (a) two voices sung at the same time, (b) while writing, the previous line shows below the one being written, (c) at most two lyric lines per row of the song, or (d) something else?
2. Standard tuning only for version 1? Capo? Left-handed diagrams?
3. App language: English, Spanish or both? Syllable splitting for Spanish and English only?
4. Strumming: down/up arrows I place myself, one pattern per section, or just one strum per chord for version 1?
5. Chords on whole beats only, or half beats too?
6. Color of chords with accidentals: by letter (Bb uses B's color, D# uses D's)?
7. Are the default finger colors OK? Do you need the thumb?
8. One pitch per syllable, or also keep the sung curve for display?
9. Export format for the mixed recording (WAV, M4A or both)? Include the metronome click?
10. Public or private repo? License?
11. When a Chorus repeats, should the copies stay linked (edit one, all change) or independent?
12. Anything in the working interpretation to change?

### Answers as given

1.-(a) two voices sung at the same time
2.-tuning standart, Left-handed diagrams
3.-both, syllable splitting for Spanish and English only
4.-one pattern per section
5.-half beats too
6.-yes
7.-yes, not thumb
8.-one initial note, one final note and pitch curve
9.-M4A, no metronome
10.-public, whatever you see more combinient
11.-copies stay linked unless the user changes it
12.-No

## D-016 — Stage 0 toolchain, as built (2026-11-13)

Checked again for Stage 0 against Google Maven, Maven Central, services.gradle.org, the Android SDK repository (`repository2-3.xml`) and the GitHub tags of each action. These are the exact versions in `gradle/libs.versions.toml` and `.github/workflows/android.yml`; D-001 is superseded where they differ.

| Component | Version | Note |
|---|---|---|
| Gradle wrapper | 9.9.1 | with `distributionSha256Sum` |
| Android Gradle Plugin | 9.5.1 | built-in Kotlin; no `kotlin-android` plugin applied in modules |
| Kotlin Gradle plugin | 2.4.21 | AGP 9.5.1 bundles KGP 2.2.10; declaring `org.jetbrains.kotlin.android` 2.4.21 with `apply false` in the root build upgrades it (checked with `buildEnvironment`: `2.2.10 -> 2.4.21`) |
| Compose compiler plugin | 2.4.21 | same as Kotlin |
| Compose BOM | 2026.09.00 | latest stable BOM |
| activity-compose | 1.13.0 | |
| lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | |
| core-ktx | 1.19.1 | |
| kotlinx.serialization JSON | 1.13.0 | |
| DataStore preferences | 1.3.0 | |
| Oboe | 1.11.0 | prefab, `ANDROID_STL=c++_shared` |
| JUnit | 4.13.2 | plus `kotlin-test-junit` |
| NDK | 29.0.14206865 (r29) | latest r29 |
| CMake | 4.1.2 | |
| compileSdk / targetSdk / minSdk | 37 / 36 / 29 | lint's `OldTargetApi` warning is disabled because targetSdk 36 is the project rule |
| JDK | 21 (Temurin in CI) | bytecode target Java 17 |
| GitHub Actions | `actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7` | |

## D-017 — Stage 0 build and app decisions (2026-11-13)

- **Application id** `com.musicreader.app`. Changing it later would install as a different app, so it stays.
- **Signing:** `keystore/debug.keystore` is committed on purpose. It is not a secret: store and key password `android`, alias `androiddebugkey`, RSA 2048, valid 100 years. CN=MusicReader Debug. Certificate SHA-256 `43:04:47:9a:91:9e:61:4e:2f:d4:cb:7e:28:6f:60:3f:df:50:61:8a:b4:0b:45:e9:91:a0:58:91:f8:56:2a:94`. Every APK, from CI or a local build, has this signature, so each one installs as an update.
- **Versions:** `versionCode` = `GITHUB_RUN_NUMBER` (1 for local builds); `versionName` = `0.<stage>.<run>`, with the stage taken from `musicreader.stage` in `gradle.properties`. Bump that property at the start of each stage.
- **ABIs:** `arm64-v8a` (the POCO F7 Ultra) and `x86_64` only. No 32-bit.
- **Pure Kotlin modules** (`core/model`, `core/dsp`) use the Kotlin JVM plugin plus the standalone Android lint plugin. Each also registers a `testDebugUnitTest` task that runs its `test` task, so `./gradlew testDebugUnitTest` covers them.
- **Theme:** dark by default (Material 3 dark color scheme). Mark confirmed after Stage 0: dark theme only for now.
- **UI language:** English and Spanish string resources from the start (D-006).
- **CI artifact name:** `MusicReader-<branch>-<run>.apk`. Characters that are not allowed in artifact names (such as `/`) become `-`. GitHub always delivers the artifact as a zip that contains the APK.

## D-018 — What a "beat" is (2026-11-13)

- A beat is one note of the time signature's denominator, and `tempoBpm` counts those notes per minute. A 4/4 bar has 4 quarter-note beats, a 6/8 bar 6 eighth-note beats, a 12/8 bar 12.
- seconds = beats × 60 / tempoBpm. Bar index = floor(beat / num), beat in bar = beat − bar × num (`BeatTime`).
- Time signatures: numerator 1–32, denominator 1, 2, 4, 8, 16 or 32. The presets are 2/4, 3/4, 4/4, 6/8 and 12/8; anything else is "custom".
- Changing the time signature keeps each section's number of bars and recomputes `lengthBeats` (4 bars of 4/4 = 16 beats become 4 bars of 3/4 = 12 beats). Events inside sections (chords, tab, syllables) keep their beat positions; how they move when bars change size is decided in Stages 6–10, where they are first edited.

## D-019 — Stage 1 decisions (2026-11-13)

- **Stage plan:** Mark's Stage 1 request includes the old Stage 2 (library, song settings and sections). Stage 2 in PROGRESS.md is marked "merged into Stage 1"; the other stage numbers are unchanged.
- **Tempo:** 30–300 BPM, stored with one decimal. The slider and the −/+ buttons give whole BPM. Tap tempo averages the intervals of the last 8 taps; a pause longer than 2.5 s starts a new count, and the song tempo follows from the second tap.
- **Saving:** every edit updates the screen at once and is written to disk 300 ms after the last change. Pending edits are written when the song screens close. Writes are atomic (D-002/DATA_MODEL storage); the repository keeps songs in memory, so the library always shows the latest data.
- **Deleting a song** hides it and shows "Undo" for about 10 s. The file is deleted when the snackbar goes away. If the app is killed before that, the song is kept.
- **Deleting a section** can also be undone from its snackbar.
- **Sections:** "Duplicate (linked)" shares the content with the original (D-013). "Duplicate (independent)" and "Unlink" make a deep copy with new ids. Type and name belong to each timeline entry; length and music belong to the shared content. Section names are the type plus a number when the type appears more than once ("Verse 2"), unless the user gives a name. A section has 1–999 bars, 4 by default.
- **Reorder:** drag the handle (≡) on the left of a section card. The list scrolls while dragging past its top or bottom edge. Drag-to-reorder is written in the app; no library is used.
- **New song:** the lyrics language defaults to Spanish when the phone is in Spanish, otherwise English. After creation the song's settings open.
- **Navigation:** navigation-compose 2.10.2 with type-safe routes. The Sections and Song settings screens share one ViewModel per song.
- **Library versions added:** kotlinx-coroutines 1.11.0 (core and test), navigation-compose 2.10.2.
- **Not built in this stage:** settings persistence with DataStore (the `AppSettings` class and its defaults exist; it is saved once the settings screens arrive in Stage 12). `Recording` exists as a class; where recordings are listed is decided in Stage 15.

## D-020 — Syllable splitting (2026-11-13)

- **Module:** new pure Kotlin module `core/lyrics` (syllabifiers and lyrics operations), next to `core/model`.
- **Spanish:** rule-based splitter written for MusicReader (`SpanishSyllabifier`), no third-party code. It covers:
  - the digraphs ch, ll, rr, qu, and gu before e/i;
  - h treated as a consonant;
  - y as a consonant before a vowel and as a vowel otherwise;
  - strong vowels a e o and accented á é í ó ú, weak vowels i u ü;
  - hiatus between two strong vowels or two identical weak ones; diphthongs and triphthongs;
  - the inseparable consonant groups pl bl cl gl fl kl pr br tr dr cr gr fr kr;
  - 3–4 consonants: the last two stay together when inseparable, otherwise only the last moves on.
  
  "tl" is split (at-lán-ti-co), and prefixes such as "sub-" follow the general rules ("su-bra-yar").
- **English:** Liang's algorithm (as used by TeX), written for MusicReader (`EnglishHyphenator`), with the American English patterns `hyph-en-us`:
  - **License:** Copyright (C) 1990, 2004, 2005 Gerard D.C. Kuiken. "Copying and distribution of this file, with or without modification, are permitted in any medium without royalty provided the copyright notice and this notice are preserved."
  - This is an all-permissive notice, not literally one of Apache-2.0/MIT/BSD/zlib. **Mark accepted this exception on 2026-11-13.**
  - The notice is kept in `core/lyrics/src/main/resources/hyphenation/hyph-en-us.LICENSE.txt`, which ships inside the app. The patterns come from the hyph-utf8 project (github.com/hyphenation/tex-hyphen), version 2005-05-30.
- **English additions for singing** (first version; replaced by D-022):
  - Minimum syllable length 1 letter at the start and 2 at the end (TeX uses 2 and 3).
  - Every syllable must contain a vowel, so "s-tarlight" is refused.
  - When the patterns give no usable break, a vowel-group fallback splits the word: "-ing" becomes its own syllable; one consonant between vowels moves to the next syllable; two are split unless they are th/sh/ch/ph/wh/gh; a silent final e does not count. This gives to-night, a-bout, sing-ing, a-lone, o-pen.
- **Words as typed:** capitals, accents and punctuation stay exactly as typed. Punctuation is attached to the neighbouring syllable ("¿Dón-de", "siem-pre,"), and a hyphen inside a word always ends a syllable. Whether to keep punctuation in the stored words is a question for Mark at the end of Stage 2.

## D-021 — Lyrics editing (2026-11-13)

- **Stage numbers follow Mark's requests.** His Stage 1 covered the old stages 1–2 and his Stage 2 the old stage 3. PROGRESS.md was renumbered: audio core is now Stage 3, …, follow mode Stage 15.
- **Where lyrics live:** lyrics belong to the section content, so linked sections share them (D-013).
- **Two voices (D-003):** first built as one second-voice text per section; changed to one second-voice line per main line in D-022.
- **Text to lines, words and syllables:** one line per row of text (blank rows are dropped); words are separated by whitespace; each word is split by the song's lyrics language.
- **Editing keeps unchanged words:** a longest-common-subsequence match on word texts keeps every unchanged word, with its id, syllables, manual split and timing. Only new or changed words are split again. A word whose text changed in any way (including a capital or a comma) counts as new. Lines whose text did not change keep their id.
- **Manual splits (`syllableOverride = true`):**
  - In the syllable view, tap between two letters to split and tap a dot to join; long-press a word to reset it to the automatic split.
  - The automatic splitter never touches overridden words, including when the lyrics language changes.
  - Split syllables lose their timing. A join of two timed syllables keeps the first start and spans both durations, with the first's start pitch and the second's end pitch.
- **Sections screen:** tapping a section card now opens its lyrics, and the card shows the first lyric line. "Edit section" (type, bars, name) moved to the ⋮ menu, next to "Lyrics".

## D-022 — Stage 2 feedback from Mark (2026-11-13)

- **Second voice per line, not per verse.**
  - Each main lyric line can have one second-voice line, sung at the same time. `LyricLine.pairedLineId` names the main line (DATA_MODEL.md). This is a new optional field, so schemaVersion stays 1.
  - On the Lyrics screen, the "Second voice" tab shows one field per main line, with that main line shown below the field while you write.
  - Editing a main line in place keeps its id, so its second line stays attached. Deleting a main line deletes its second line.
  - Second-voice lines saved by the first Stage 2 build (no `pairedLineId`) are attached in order to main lines that have none; any that are left over are dropped.
- **English syllables are placed the way they are sung.** Mark's corrections: "you" not "y-ou", "ma-king", "des-pite", "o-ther".
  - The hyph-en-us patterns now only decide which syllables a word has. The break inside the consonants between two vowel groups follows singing rules, like the Spanish ones:
    - one consonant goes to the next syllable (ma-king);
    - th sh ch ph wh ck and the blends pl bl cl gl fl pr br tr dr cr gr fr wr stay together and go on (o-ther);
    - gh stays behind (laugh-ing);
    - other pairs are split (des-pite);
    - with three or more consonants, the last two go on when they stay together or are st/sp/sk/sc (un-der-stand), otherwise only the last one;
    - before a final "-le", the consonant before the l goes on (lit-tle).
  - y at the start of a word or before a vowel is a consonant (you, be-yond), except before a final silent e (good-bye).
  - A final e is silent unless the patterns start a syllable right before it.
  - When the patterns give no usable break, every vowel-group gap gets one (to-night, a-bout, sin-ging). Words the patterns do not split and whose final e looks silent stay whole (e.g. "maybe") and can be split by tap.
- **Punctuation and capitals are kept as typed** (Mark: yes).

## D-023 — Song viewer (2026-11-13)

- **Stage order:** Mark's Stage 3 request is the viewer and says chords and tabs come in Stages 4–5. PROGRESS.md is now: 3 viewer, 4 chords, 5 tab editor, 6 audio core, 7 DSP, 8 lyric capture, 9 note editor, 10 user configuration, 11–12 practice, 13 recordings, 14 follow mode.
- **Layout (`SheetLayoutBuilder`, pure and tested):**
  - Sections are stacked with their type as a header. Each section is cut into rows.
  - With lyrics, there is one row per main lyric line, and the lines share the section's beats equally (line i covers i·L/n to (i+1)·L/n). Without lyrics, rows are 2 bars long.
  - Every chord, tab note and lyric has an x position = (beat − row start) / row length. In a row, chords are on top, tab in the middle and lyrics at the bottom, so items at the same beat line up. Bar lines are drawn through the row.
- **Untimed lyrics** (before Stage 8 captures timing):
  - Whole words are placed at their character position in the line, so the line spreads over its row.
  - Timed syllables are placed at their start beat.
  - When labels would overlap (large text), a label is pushed right just enough, and a row that overflows is shifted left.
  - The second-voice line is drawn in italics under its main line.
- **Chord labels:**
  - Colored by root letter (D-011) with the user's chord colors (defaults in DATA_MODEL.md), and showing the full name.
  - One `InfiniteTransition` makes a single phase (2.8 s cycle: the sweep crosses during the first half, then rests), shared by every label, so all glisten together.
  - Each label draws a white gradient band over its own text (`BlendMode.SrcAtop` on an offscreen layer). The brush is built once per label size and only moved each frame: no allocation per frame, and the phase is read during drawing only (no recomposition).
- **Tab lane:** six string lines, high e on top. Fret numbers are drawn in finger colors; open strings get a white outline circle. Technique marks: 5h, 7p, 3/, 3\\, 7b, 7br, 3~, <12>, [7], x.
- **Autoscroll:**
  - Speed is in dp per second, 2–150 on a logarithmic slider, 15 by default.
  - Every frame scrolls by speed × time since the last frame (`withFrameNanos`), so the movement is smooth.
  - Touching the song pauses it, and so does reaching the end.
  - It is a reading aid, not musical timing; audio-clock timing starts with the audio stages.
- **Layer checkboxes:** Lyrics, Chords, Tabs. The last checked one is disabled. Saved in settings.
- **Text size:** "Aa" in the viewer's top bar opens a slider, 0.8–2.0 × normal. Saved in settings (`viewerFontScale`, new in DATA_MODEL.md).
- **Settings storage:** `DataStoreSettingsRepository` keeps the whole `AppSettings` as one JSON value in a Preferences DataStore, so new fields get defaults and unknown ones are ignored. `autoscrollSpeed` now means dp per second (it was a tempo multiplier, never used).
- **Long-press on a chord:** a bottom sheet with the chord name in its color, plus root, type and bass. The fretboard diagram is added to this sheet in Stage 4.
- **Demo song:** debug builds only, in the library's ⋮ menu: "Load demo song (debug)". It has Intro, Verse, Chorus and Outro; lyrics in two voices; chords C, Em, F, G7, Cmaj7, D#, Bb9, Am, D/F#, D, E, A, Bm (every root letter); and tab notes with fingers 0–4 and several techniques.
- **Opening the viewer:** the "View" button on a song's Sections screen.
- **Screenshot tests:** Roborazzi 1.79.0. Three images of the demo song (all layers, lyrics + chords, tab only) at 411×891 dp, compared with the references in `app/src/test/screenshots` on every test run, with a 1 % pixel-difference tolerance. The references are recorded on Linux with Robolectric native graphics (same as CI). To update them: `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true`.

## D-024 — Chord dictionary and chord placement (2026-11-14)

- **Source:** tombatossals/chords-db `lib/guitar.json`, commit df06fa7b425cf5fd29485ff6591236b3557e3fac (MIT License, Copyright (c) 2016 David Rubert).
  - Layout checked on the real file: 12 keys (C, C#, D, Eb, E, F, F#, G, Ab, A, Bb, B), 82 suffixes, 828 chords, 3,283 positions (2–7 per chord).
  - Each position has `frets` (relative to `baseFret`; −1 muted, 0 open; low E first), `fingers` (0–4), `baseFret` (1–14), `barres` (relative frets), optional `capo` and `midi`.
  - All 3,283 `midi` lists equal standard tuning + absolute fret.
- **Converter:** `tools/convert_chords_db.py <guitar.json> <LICENSE> <commit>` writes `core/data/src/main/assets/chords/guitar.json` (frets still relative) and `LICENSE-chords-db.txt`. It checks every position and turns the one −1 finger (F major shape 2, on a muted string) into 0. About shows the credit.
- **Loader** (`ChordDictionary`, core/data): absolute fret = baseFret + fret − 1 for fret > 0. A barre spans from the lowest to the highest string pressed at its fret, with the finger used there. The dictionary loads once in the background at app start.
- **21 shapes do not contain their root note**: rootless jazz voicings (maj9, 7b9, …) and a few upstream data slips (e.g. Bb m7 shape 1 sounds Am7; all four E madd9 shapes). They are kept as published; a test pins the list so any change in the data is noticed.
- **Names:** the dictionary suffix "major" is our "" and "minor" is "m". Slash suffixes ("/E", "m/B", "m9/Bb", "7/G") become a suffix plus a bass note.
- **voicingId:** `key|suffix|index`, e.g. `Eb|9|0`. Unknown ids fall back to the first shape of the chord type.
- **Enharmonics:** C#/Db, D#/Eb, F#/Gb, G#/Ab, A#/Bb find the same dictionary chords; the chord keeps the spelling the user chose (D#9 uses the Eb 9 shapes). The new setting `AppSettings.accidentals` (SHARPS by default, ♯/♭ in the picker) decides how the picker spells the 12 roots.
- **Picker:**
  - root (12), then type: major, m, 7, maj7, m7, 9, sus4, sus2, add9, 6, m6, dim, dim7, aug, 5, 7sus4, m9, maj9, m7b5, 11, 13 first, then every other type, slash chords last;
  - then a pager with every shape and its diagram. "Use this shape" saves the `voicingId`.
- **Diagram:**
  - strings vertical, low E on the left (mirrored when left-handed);
  - a nut at base fret 1, otherwise "Nfr";
  - × muted, o open (white outline);
  - dots in the finger colors with the number inside, and barres drawn as a bar in the finger's color.
- **Chord editor:** "Chords" in a section card's ⋮ menu.
  - Rows match the viewer, with a half-beat grid: short ticks on half beats, longer ones on beats, full lines on bars. The row's lyrics are under the grid.
  - Tap → picker → the chord lands on the nearest half beat (D-009). Adding on an occupied beat replaces that chord.
  - Drag a chord sideways to move it; it snaps when released.
  - Tap a chord to change it; long-press to delete it (Undo).
  - Section cards list their chords.
- **Viewer:** a long-press on a chord shows the chosen shape's diagram above root, type and bass.

## D-025 — Chords on syllables (2026-11-14, Mark's Stage 4 test)

Mark's answer to the Stage 4 test: "can you do it per syllables?"; which syllable sits under a chord was "not totally" clear.

- **In rows with lyrics, a chord is placed on a syllable.** `ChordEvent.syllableId` names the main-voice syllable. It is a new optional field, so schemaVersion stays 1.
- **Drawn above its syllable:** the chord is always drawn right above its syllable, in the chord editor and in the viewer.
- **Lyrics as text:** in rows with lyrics, the line is laid out as normal text (syllables of a word together, a gap between words, wrapping at word boundaries), with each chord above its syllable. A chord that would overlap the previous one moves right just enough. The tab lane stays between chords and lyrics; bar lines are only drawn in rows without lyrics.
- **Beat of a chord on a syllable:** the syllable's start beat once sung (Stage 8). Until then it is estimated from the syllable's character position in its line (the line's share of the section, D-023), snapped to the half-beat grid.
  - The beat is updated after every edit, so chords follow their syllables when lyrics, section lengths or the time signature change.
  - If the syllable is deleted, the chord keeps its beat and becomes a grid chord.
- **Rows without lyrics** (intros, solos) keep the half-beat grid (D-009). A grid chord in a row with lyrics is shown above the syllable nearest to its beat; dragging it onto a syllable places it there.
- **Chord editor:**
  - Tap a syllable (each has a dotted underline) to put a chord on it, or to change the chord above it.
  - Drag a chord onto another syllable to move it; tap a chord to change it; long-press to delete (Undo).
- **Demo song:** its chords in Verse and Chorus sit on syllables.

## D-026 — Barres the dictionary does not list (2026-11-14, Mark's Stage 4 round 2)

- **Problem:** Mark found Gm7 showing "three times the same finger". Its first shape (3x333x) presses D, G and B at fret 3 with finger 3, but chords-db lists no barre, so the diagram drew three separate "3" dots. 119 shapes have this gap.
- **Fix:** the loader adds the missing barre when one finger presses two or more strings at the same fret and every string between them is pressed at that fret or higher. Other fingers may press higher over a barre (as in a barre F).
- **Not inferred:** when a string in between is at a lower fret or muted, a bar there would be wrong, so the separate dots stay. Example: C6/9 x32233, finger 3 on A and B with D and G at fret 2.
- **Tests:** Gm7 and C6/9, plus a screenshot of Gm7.

## D-027 — Tabs (2026-11-14)

- **One beat grid in rows with lyrics:** lyrics are laid out as text (D-025). Each visual line gets a piecewise-linear beat→x axis (`LineAxes`, `BeatAxis`, pure and tested) through every syllable at its beat (sung, or estimated from its place in the line), reaching the right edge at the line's end beat. Tab notes, grid chords and bar lines are placed on that axis, so a note at a syllable's beat sits under that syllable.
  - Each visual line is drawn as chords, then a six-line tab staff, then lyrics.
  - Rows without lyrics are one line with a linear axis.
  - In the viewer, the staff appears only on lines with notes when Tabs is checked; in the tab editor every line has a staff.
- **Grid:** tab notes snap to quarter beats (16th notes in 4/4). Lengths offered: ¼, ½, 1, 2, 4 beats. Frets 0–24. Fingers 0 (open) … 4 (no thumb, D-012). A note on the same beat and string is replaced.
- **Techniques:** hammer-on h, pull-off p, slide up /, slide down \, bend ¼ / ½ / full, bend + release (same amounts), vibrato ~, natural harmonic <12>, artificial harmonic [12], muted x.
  - Hammer-ons, pull-offs and slides lead to the next note on the same string, if it starts within one beat after the note ends.
  - Bend amounts are stored in `bendQuarterTones` (1, 2, 4); harmonics store `harmonicFret` (= the fret).
- **Drawing:**
  - fret numbers in the finger's color, open strings with an outline;
  - an arc with h/p to the target note, or a short arc when there is none;
  - a diagonal line for slides, rising or falling;
  - a curved arrow with the amount above for bends, a dashed arrow back down for a release;
  - a wavy line over the note's length for vibrato;
  - brackets for harmonics, x for muted notes.
- **Editor:** "Tab" in a section's ⋮ menu.
  - Tap a string on the staff to add a note; the sheet has a fret number pad, finger, length and technique. Typing 1 then 2 gives 12; 2 then 5 gives 5, because 25 is above 24.
  - "Save + next" saves and opens the next note on the same string, right after this one.
  - Tap a note to edit it, drag it to move it (beat and string), long-press it to delete it.
  - Undo and redo cover tab edits while the editor is open.
- **Export:** the editor's "Export" shows the section's tab with Copy and Share.
  - alphaTex: `fret.string.duration`, chords of notes in parentheses, `r` rests, `|` bars; effects `{h}`, `{sl}` (or `{sou}`/`{sod}`), `{b (0 n)}`, `{b (0 n 0)}`, `{v}`, `{nh}`, `{ah}`, `x`.
  - ASCII: a chord line, six lines high e to low E, one column per quarter beat, `|` bars; bends written as the fret they reach (8b10).


## D-028 — User settings screen (Stage 6, 2026-11-14)
- **Where:** Library ⋮ → "Settings". Every setting is one `AppSettings` value in DataStore (D-023). Each change is saved at once. Every screen collects the same settings flow, so a change shows everywhere immediately and survives a restart.
- **Chord colors:** one row per root letter C D E F G A B. Each row has a swatch, a preview (`C  Cm7  C7` in the color) and the hex value. Tapping a row opens the color picker, which has:
  - hue, saturation and brightness sliders;
  - a palette;
  - a `#RRGGBB` field;
  - a live preview;
  - "Reset to default".

  "Reset all" restores all seven colors. Sharps and flats take the color of their letter (D-011).
- **Finger colors:** open string and fingers 1–4, with the same picker and resets. **No thumb** (D-012, Mark's answer to Q7). The Stage 6 task listed a thumb, so this is asked again at the end of the stage.
  - Two finger colors are "nearly the same" when their CIE76 ΔE (Lab, D65) is below 20 (`ColorMath.similarFingerPairs`). The screen then shows a red warning naming both fingers.
  - The warning does not block saving.
- **Default viewer layers:** checkboxes for lyrics, chords and tabs. The last checked one is disabled, as in the viewer (D-023). These are the same `viewerLayers` the viewer's own checkboxes change.
- **Also here:** sharps/flats for chord names (the same setting as the chord picker's ♯/♭) and left-handed diagrams.
- **App language:** System, English or Español, for every string in the app (all in `values/` and `values-es/`). Format names and symbols (alphaTex, ASCII, ♯, ♭, digits) are not translated.
  - Android 13+ uses the per-app language (`LocaleManager`, `locales_config.xml`), so it also shows in Android's app settings.
  - Android 10–12 keeps a copy of the choice in SharedPreferences. `MainActivity.attachBaseContext` applies it, and the activity is recreated.
  - The song language (syllable splitting) is separate and stays per song.
- **No fixed colors:** chord and finger colors come from the settings, everything else from the theme. A unit test scans the app's sources for `Color(0x…)` and named colors. Three exceptions are allowed:
  - the theme;
  - black or white for the number inside a finger dot, chosen for contrast with the user's color;
  - the viewer's white glisten.

  The chord editor's dotted underline now uses the theme's outline color.
- **Robust reading:** if the stored settings miss a letter or finger (e.g. written by an older version), the default fills in. Unknown fingers are dropped, and unreadable settings fall back to the defaults.

## D-029 — Audio engine, audio clock, metronome, latency calibration (Stage 7, 2026-11-14)
- **Engine (C++17, Oboe 1.11.0):**
  - The output stream is the master: LowLatency, Exclusive (Oboe falls back to Shared), 48 kHz float, stereo, Usage Media. The buffer starts at two bursts, and Oboe's `LatencyTuner` raises it after underruns.
  - The microphone stream (mono, VoiceRecognition preset) is opened **only for calibration**. It is read without blocking inside the output callback, so input and output share one frame counter, as in Oboe's full-duplex helper. The first 8 callbacks drop what piled up while the streams started.
  - Callbacks never allocate, lock, log or block:
    - commands reach the callback through a lock-free SPSC queue;
    - microphone samples leave through a lock-free SPSC ring buffer (about 11 s);
    - sounds are generated once, when the engine is created.
  - Engine frames continue across stream restarts, so the metronome and the clock do not jump when the route changes. During a restart the clock pauses.
- **Audio clock:** the output stream's `getTimestamp(CLOCK_MONOTONIC)` (presented frame plus time).
  - It is queried at most every 20 ms by whichever thread reads the clock, never in the callback (Oboe guards it with a lock), and published through a seqlock.
  - Reads extrapolate with the monotonic clock. They are capped at the written frame and never go backwards.
  - Until the first timestamp exists, the clock uses "written frame at the last callback minus the buffer".
  - Kotlin reads it through `AudioService` (`metronomeBeat()`, `clockSeconds()`). All musical timing comes from it, never from frame time; the screen only polls it to redraw.
- **Metronome:**
  - Rendered sample by sample in the callback. Beat k sits at `anchorFrame + round((k − anchorBeat) × framesPerBeat)`, so beats never drift (tested to be sample exact for any block size).
  - Beat 1 of the bar is accented (higher, louder click). A beat is one denominator note (D-018), so 6/8 clicks six eighths.
  - Start and tempo or meter changes take effect 120 ms ahead, on the next beat. Beats already heard stay where they were, and a new meter starts a new bar.
  - A metronome button on the song's Sections screen shows a beat light read from the audio clock. The metronome follows tempo and time-signature edits while it plays, and stops when the song is closed.
- **Lifecycle:**
  - Streams run only while something needs them: the metronome, a calibration, or the diagnostics screen.
  - Everything stops when the app goes to the background (not on rotation).
  - The screen stays on while audio runs.
  - The microphone permission is asked only when a calibration starts. If it is refused, a screen explains why it is needed, with "Try again" and "Open app settings".
- **Route changes:**
  - Oboe's disconnect callback only sets a flag. A watchdog (every 200 ms) reopens the streams on the control side.
  - An Android `AudioDeviceCallback` also reopens running streams 300 ms after an output device is added or removed, because a plug-in does not always disconnect the stream.
  - The route comes from the stream's device id: USB, wired, Bluetooth or speaker.
- **Latency calibration (Settings → Latency calibration):**
  - **Automatic:** 5 chirps (40 ms, 600 Hz to 6 kHz, 1 s apart) are played and recorded.
    - Each chirp is searched up to 800 ms after it was played, by normalized cross-correlation (absolute value, so an inverted mic still matches) with a parabolic sub-sample peak.
    - A chirp counts as heard at a peak of 0.2 or more (noise alone peaks near 0.1).
    - The result is the mean of the measurements within 1 ms of the median.
    - Confidence = (share of chirps within 1 ms of the median) × min(1, median peak / 0.3).
  - **Manual:** 16 clicks at 80 BPM, after 1.5 s to get ready. The user claps or strums on each click.
    - Onsets are found as energy rises (1 ms hop, 5 ms window against the 25 ms before, at least 10 dB above the quiet level).
    - For each click, the strongest onset from ¼ interval before to ¾ after is taken.
    - The result is the median offset. Confidence = found share × (1 − MAD / 30 ms).
    - On the speaker, the microphone also hears the click itself, at the same round trip as an on-time clap, so this does not bias the median.
  - The result is shown with its confidence, found count and spread. Below 50 % confidence the screen asks to measure again.
  - "Save for <route>" stores one `LatencyProfile` per route (`AppSettings.latencyProfiles`), and saved profiles can be cleared.
  - Bluetooth shows a warning that it is too slow for practice (150–300 ms). `LatencyOps.tooSlowForPractice` is true for Bluetooth, or for any route measured above 100 ms.
- **Diagnostics (Settings → Audio diagnostics):**
  - status, route, saved latency for the route;
  - sample rate, burst, buffer size and capacity (frames and ms), sharing and performance mode, audio API;
  - underruns (xRunCount), output latency from the timestamps, channels, device id, audio clock, callbacks, stream restarts;
  - microphone details while it is open.

  It keeps the streams running while open and refreshes twice a second.
- **Tests without a device:**
  - The DSP (`audio/src/main/cpp/dsp`: ring buffer, clock math, beat grid, renderer, chirp, correlation, onsets, calibration) is pure C++. It is built for the host and tested with GoogleTest (BSD-3-Clause) through `cmake` + `ctest` in its own CI job (`audio/src/test/cpp`).
  - Kotlin tests use a fake engine for the controller and a fake `AudioService` for the ViewModels.
- **Count-in:** not part of this stage. It comes with lyric capture (Stage 9), on the same metronome.
