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
| instrument | Instrument | default `GUITAR_6_STANDARD`, tuning MIDI `[40,45,50,55,59,64]` (low E to high e), capo `0` |
| tempoBpm | Double | |
| timeSignature | `{ num: Int, den: Int }` | |
| sections | List&lt;Section&gt; | in song order |
| createdAt | Instant (epoch ms) | |
| updatedAt | Instant (epoch ms) | |

## Section

| Field | Type | Notes |
|---|---|---|
| id | String | |
| type | `INTRO` \| `VERSE` \| `PRE_CHORUS` \| `CHORUS` \| `BRIDGE` \| `OUTRO` | OUTRO is shown as "Outro / Coda" |
| label | String? | e.g. "Verse 2" |
| lengthBeats | Int/Double | always whole bars (multiple of `timeSignature.num`) |
| lyricLines | List&lt;LyricLine&gt; | |
| chords | List&lt;ChordEvent&gt; | |
| tab | List&lt;TabEvent&gt; | |

Beats inside a section are relative to the section start.

## LyricLine

| Field | Type | Notes |
|---|---|---|
| id | String | |
| voice | Int | 0 = main, 1 = second line |
| text | String | |
| words | List&lt;Word&gt; | |

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
| pitchMidi | Double? | continuous (e.g. 64.27) |
| freestyle | Boolean | no judged pitch |
| takeId | String? | which take produced the timing (last take wins) |

## ChordEvent

| Field | Type | Notes |
|---|---|---|
| id | String | |
| beat | Double | snapped to the grid |
| symbol | `{ root: A..G, accidental: NONE \| SHARP \| FLAT, suffix: String, bass: Note? }` | e.g. Bb9 = root B, FLAT, "9" |
| voicingId | String | id in the chord dictionary (frets + fingers) |
| strum | StrumPattern? | |

## StrumPattern

| Field | Type | Notes |
|---|---|---|
| subdivision | Int | e.g. 2 = eighths, 4 = sixteenths |
| strokes | List&lt;{ atBeat: Double, dir: DOWN \| UP \| MUTE, accent: Boolean }&gt; | |

## TabEvent

| Field | Type | Notes |
|---|---|---|
| id | String | |
| beat | Double | |
| durationBeats | Double | |
| string | Int | 1 = high e … 6 = low E |
| fret | Int | |
| finger | Int | 0 open, 1–4, 5 thumb |
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
- `fingerColors`: finger 0..5 → color
- `viewerLayers`: lyrics / chords / tab visibility (at least one on)
- `practiceLowerLayers`: per practice type, which other layers show below the lane (never the practiced one)
- `autoscrollSpeed`
- `judgeWindows`: `{ goldMs: 50, greenMs: 120, redMs: 250, goldCents: 35, greenCents: 100 }`
- `latencyProfiles`: per audio route (speaker, wired, Bluetooth, USB)
- `uiLanguage`

## Storage layout

```
filesDir/
  songs/
    <id>.json          # written atomically: temp file + rename
    <id>/
      rec/*.wav        # practice recordings
      takes/           # raw sung takes for lyric capture
```

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
| 5 thumb | `#8D6E63` |
