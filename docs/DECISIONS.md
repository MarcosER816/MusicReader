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

The repository starts with documentation only, no app code: `docs/SPEC.md`, `docs/DATA_MODEL.md`, `docs/DECISIONS.md`, `docs/BACKLOG.md`, `docs/DEVICE_TESTS.md`, `PROGRESS.md`, `CLAUDE.md`, `AGENTS.md`. Stages 0–16 are listed in PROGRESS.md.

## Open questions (asked 2026-11-13, waiting for Mark)

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

Answers: _pending_
