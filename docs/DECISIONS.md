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

