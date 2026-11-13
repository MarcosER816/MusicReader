# MusicReader — Progress

Update this file at the end of every stage. A stage is **done** only when every task and Done item is complete, CI is green, the verification questions were asked, and Mark wrote "Stage N approved".

Status values: `not started` · `in progress` · `waiting for Mark` · `done`

| # | Stage | Branch | Status | PR | Approved |
|---|---|---|---|---|---|
| 0 | Project skeleton: Gradle modules, versions, CI that builds and uploads the APK | stage-00-skeleton | not started | | |
| 1 | Core model: data classes, JSON serialization, atomic file storage, schema tests | stage-01-model | not started | | |
| 2 | Song library and song settings: create/rename/delete, title, tempo, time signature, sections and section types with descriptions | stage-02-library | not started | | |
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

_No stages started yet._
