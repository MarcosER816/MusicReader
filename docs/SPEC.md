# MusicReader — Specification

This file has two parts:

1. **Feature spec**: Mark's original text, copied word for word. Do not edit it. Spelling is left as written.
2. **Working interpretation**: how the spec is read for implementation. Each point is confirmed or changed by Mark's answers in [DECISIONS.md](DECISIONS.md).

Never change either part silently. Ask Mark first and record the answer in DECISIONS.md.

---

## Part 1 — Feature spec (verbatim)

When creating or changing the song:
-upload the lyrics from a song (the user after adding the lyrics can sing the song to configure the timing and the notes of each word (they must select all the words they are going to sing, then again and so on (the user can select parts that he already sang to make the performance better as many times as he wants, the last configuration takes the place)), then after the user thinks is close enough then he can see the notes for each syllable and how much beats that syllable lasts , and the user can move the note of the syllable and the beats that it lasts (it doesnt snaps)) (up to two lines of lyrics (when writing the second then the first apears bellow it))
-Add chords in any beat of the song (self aligthment to the beat while showing the lyrics bellow where the chords are) (search for a dictionary that shows every way to do each posible chord for the user to be able to select what form of the chord he wants) (add the number of each finger)
-Add typing tabs (they will show bellow the chords and on top of the lyrics) (it includes special moves like bending, hammer ons, slides, vibratos, and harmonics)
-configure the song: [change the time signature, change the tempo of the song]
-being able to change the type of the verse (Intro, Verse, Pre-Chorus, Chorus, Bridge, Outro / Coda)

When seeing the song:
-Be able to scroll down to see the song slowly
-Being able to configure wether they see the lyrics, the chords, and the tabs (each with a checkbox and t least one has to be active at a time)
-if able to see the chords on top of the lyrics (each chord must have its own color: [C: red, D: orange(medium), E: yellow, F: lime , G: sky blue, A: magenta, B:lavender or light purple] each diferent type of modification to each chord shows (as D#, Bb9, Cmaj7, etc)) (The chords have to have each the same animation of gliscening.) (If the user holds the chord it must show the way its done on the fret with witch finger goes where with the same color scheme as the tab)
-the tab shows each number and the color of the finger
-A practice mode (the user can practice either chords, tabs, or the singing part (lyrics))(if chords the user sees the chord on top (same chord clor scheme) and the struming ritm going from right to left on the velocity of the time signature (if the user hits het chord perfectly then it iluminates on a gold color and lets golden particles go out, if its a litle late or soon then green, if its too soon or too late then on red)) (if it is tab then its the same as the chords but with the notes) (and if it is singing then it shows the syllables bellow and an arrow that represents the signing of the user where it goes higer if if the pitch is higer and lower if the pitch is lower and it uses the same notes the user recorded and configured, the arrow must follow the line of the notes and when there is a silence the line stops (the arrow leaves a trail behind it, and when the arrow is close to the line then it begins to show golden particles, when its not too far it is green and when its far it becomes red) the line advances from right to left) the mode must let you record the practice and then ask you if you want to keep it if you want to keep it then you can (only one oficial recording per type of practice).
-A play button using the official practice recordings that the user did all at the same time to know how it sounds and then it lets the user download the recording sampled.
-A mode where it shows you the lyrics and the chords and it advances as the user plays the chords to avoid having to scroll down

User configuration:
-Being able to change the color of the chord on the song
-being able to change the colors for each finger
-being able to see (lyrics, the tab or the chords as a checkbox for each type of practice (the ting the user is practicing doesnt appear)) then it apears bellow of the practice also going from right to left on the rithm

Section type descriptions to show in the app:
- Intro: the opening section that sets the key, tempo, mood and style.
- Verse: the storytelling section. Same music each time, the lyrics change.
- Pre-Chorus: a short, optional section that builds tension before the chorus.
- Chorus: the emotional, catchy peak. Repeats the same lyrics and main message.
- Bridge: a contrasting section, often near the middle, with new chords or melody.
- Outro / Coda: the concluding section that winds down or resolves the song.

---

## Part 2 — Working interpretation

Status: **pending confirmation** (open questions in DECISIONS.md).

- Lyric capture: select a word range, count-in, sing. The app records pitch and onsets and gives each syllable a start beat, a duration in beats (decimal) and a continuous pitch. Singing a range again overwrites only that range (last take wins). The note editor drags pitch and duration freely with NO snapping.
- Chords snap to the beat grid and sit above the lyrics. Tabs sit between chords and lyrics.
- Chord color goes by root letter; the full name is shown (D#, Bb9, Cmaj7). All chord labels share one synchronized glisten animation. Long-press shows the fretboard diagram with finger colors equal to the tab finger colors.
- Practice lanes scroll right to left at the tempo. Gold = perfect plus gold particles, green = slightly off, red = far off.
- One official recording per practice type per song. Play mixes them in sync; the mix can be exported to Downloads.
- Follow mode advances lyrics + chords by listening to the chords I play.
