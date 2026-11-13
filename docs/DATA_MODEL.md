# MusicReader — Data model

Serialization: kotlinx.serialization JSON. `schemaVersion = 1`.
Pure Kotlin types live in `core/model`. All types are immutable data classes.
Any change to this file must be agreed with Mark and recorded in [DECISIONS.md](DECISIONS.md). A change to the JSON shape bumps `schemaVersion` and needs a migration with a unit test.

## Song

| Field | Type | Notes |
|---|---|---|
| id | String | stable unique id |
| schemaVersion | Int | 1 |
| title | String | |
| artist | String? | |
| language | `es` \| `en` | used for syllable splitting |
| instrument | Instrument | version 1: only `GUITAR_6_STANDARD`, tuning MIDI `[40,45,50,55,59,64]` (low E to high e), capo `0` (field kept for later, not editable in v1; D-005) |
| tempoBpm | Double | |
| timeSignature | `{ num: Int, den: Int }` | |
| sections | List&lt;Section&gt; | the song timeline, in order |
| sectionContents | List&lt;SectionContent&gt; | the musical content; linked sections share one (D-013) |
| createdAt | Instant (epoch ms) | |
| updatedAt | Instant (epoch ms) | |

## Section (one entry in the timeline)

| Field | Type | Notes |
|---|---|---|
| id | String | |
| type | `INTRO` \| `VERSE` \| `PRE_CHORUS` \| `CHORUS` \| `BRIDGE` \| `OUTRO` | OUTRO is shown as "Outro / Coda" |
| label | String? | e.g. "Chorus 2" |
| contentId | String | id of a SectionContent |

Linked copies (D-013): duplicating a section creates a new Section with the **same** `contentId`, so editing one changes all of them. "Unlink" copies the content to a new SectionContent with new ids and points this Section at it. A SectionContent that no Section references is deleted on save.

## SectionContent

| Field | Type | Notes |
|---|---|---|
| id | String | |
| lengthBeats | Int | always whole bars (multiple of `timeSignature.num`) |
| strum | StrumPattern? | one strumming pattern for the whole section (D-008) |
| lyricLines | List&lt;LyricLine&gt; | |
| chords | List&lt;ChordEvent&gt; | |
| tab | List&lt;TabEvent&gt; | |

Beats inside a section are relative to the section start.

## LyricLine

| Field | Type | Notes |
|---|---|---|
| id | String | |
| voice | Int | 0 = main voice, 1 = second voice, sung **at the same time** as voice 0 (D-003) |
| text | String | |
| words | List&lt;Word&gt; | |

Each voice is captured in its own takes (one microphone cannot separate two people singing together). In the editor, while writing or editing voice 1, voice 0 is shown below it.

## Word

| Field | Type | Notes |
|---|---|---|
| id | String | |
| text | String | |
| syllables | List&lt;Syllable&gt; | |
| syllableOverride | Boolean / manual split | true when the user split the word by hand instead of the automatic splitter |

## Syllable

| Field | Type | Notes |
|---|---|---|
| id | String | |
| text | String | |
| startBeat | Double? | null until sung or set |
| durationBeats | Double? | decimal, no snapping |
| pitchStartMidi | Double? | initial note, continuous (e.g. 64.27) (D-010) |
| pitchEndMidi | Double? | final note, continuous (D-010) |
| pitchCurve | List&lt;CurvePoint&gt; | sung pitch curve from the take; empty if not sung (D-010) |
| freestyle | Boolean | no judged pitch |
| takeId | String? | which take produced the timing (last take wins) |

### CurvePoint

| Field | Type | Notes |
|---|---|---|
| t | Double | position inside the syllable, 0.0 = start, 1.0 = end |
| midi | Double | continuous pitch |

The curve is stored at most one point per 10 ms of the take. Because `t` is relative, changing `durationBeats` stretches the curve. When the user moves the initial or final note by Δstart or Δend semitones, every point is shifted by `(1 - t) * Δstart + t * Δend`, so the curve always starts at `pitchStartMidi` and ends at `pitchEndMidi`. The practice line and the singing judge follow this curve; with an empty curve they use a straight glide from the initial to the final note.

## ChordEvent

| Field | Type | Notes |
|---|---|---|
| id | String | |
| beat | Double | snapped to a half-beat grid (multiples of 0.5) (D-009) |
| symbol | `{ root: A..G, accidental: NONE \| SHARP \| FLAT, suffix: String, bass: Note? }` | e.g. Bb9 = root B, FLAT, "9"; color comes from the root letter only (D-011) |
| voicingId | String | id in the chord dictionary (frets + fingers) |

## StrumPattern (one per SectionContent, D-008)

| Field | Type | Notes |
|---|---|---|
| subdivision | Int | e.g. 2 = eighths, 4 = sixteenths |
| strokes | List&lt;{ atBeat: Double, dir: DOWN \| UP \| MUTE, accent: Boolean }&gt; | `atBeat` is relative to the start of a bar; the pattern repeats every bar of the section and strums whichever chord is current |

## TabEvent

| Field | Type | Notes |
|---|---|---|
| id | String | |
| beat | Double | |
| durationBeats | Double | |
| string | Int | 1 = high e … 6 = low E |
| fret | Int | |
| finger | Int | 0 open, 1–4 (no thumb, D-012) |
| technique | `NONE` \| `HAMMER_ON` \| `PULL_OFF` \| `SLIDE_UP` \| `SLIDE_DOWN` \| `BEND` \| `BEND_RELEASE` \| `VIBRATO` \| `NATURAL_HARMONIC` \| `ARTIFICIAL_HARMONIC` \| `MUTE` | |
| bendQuarterTones | Int? | for BEND / BEND_RELEASE |
| targetFret | Int? | for slides, hammer-ons, pull-offs |
| harmonicFret | Int? | for harmonics |

## Recording

| Field | Type | Notes |
|---|---|---|
| practiceType | `SING` \| `CHORDS` \| `TAB` | |
| file | String | relative path under `songs/<id>/rec/` |
| sampleRate | Int | 48000 |
| startSongBeat | Double | song beat where the recording starts |
| startFrameOffset | Long | audio frame of that beat in the file |
| latencyCompensationMs | Double | applied when mixing |
| official | Boolean | at most one official recording per practice type per song |
| createdAt | Instant | |

## Settings (DataStore)

- `chordColors`: root letter → color
- `fingerColors`: finger 0..4 → color
- `leftHanded`: Boolean, mirrors fretboard diagrams (D-005)
- `viewerLayers`: lyrics / chords / tab visibility (at least one on)
- `practiceLowerLayers`: per practice type, which other layers show below the lane (never the practiced one)
- `autoscrollSpeed`
- `judgeWindows`: `{ goldMs: 50, greenMs: 120, redMs: 250, goldCents: 35, greenCents: 100 }`
- `latencyProfiles`: per audio route (speaker, wired, Bluetooth, USB)
- `uiLanguage`: `SYSTEM` \| `en` \| `es` (D-006)

## Storage layout

```
filesDir/
  songs/
    <id>.json          # written atomically: temp file + rename
    <id>/
      rec/*.wav        # practice recordings (WAV inside the app)
      takes/           # raw sung takes for lyric capture
```

Exported mix (D-014): the official recordings mixed in sync, **without** the metronome click, encoded as M4A (AAC) and saved to the public Downloads folder through MediaStore.

## Defaults

Chord colors (by root letter):

| Root | Color |
|---|---|
| C | `#E53935` (red) |
| D | `#FB8C00` (orange) |
| E | `#FDD835` (yellow) |
| F | `#C6FF00` (lime) |
| G | `#4FC3F7` (sky blue) |
| A | `#E040FB` (magenta) |
| B | `#B39DDB` (lavender) |

Finger colors:

| Finger | Color |
|---|---|
| 0 open | white outline |
| 1 index | `#42A5F5` |
| 2 middle | `#66BB6A` |
| 3 ring | `#FFA726` |
| 4 pinky | `#EF5350` |

No thumb (D-012).
