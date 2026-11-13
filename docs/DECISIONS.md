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
