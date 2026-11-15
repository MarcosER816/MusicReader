// Metronome beat positions in audio frames. Every beat frame is computed from an anchor, never
// by adding up intervals, so there is no drift and each click lands on an exact sample.
#pragma once

#include <cmath>
#include <cstdint>

namespace mr {

struct BeatGrid {
    /** Frame of beat [anchorBeat]. */
    int64_t anchorFrame = 0;
    int64_t anchorBeat = 0;
    double framesPerBeat = 24000.0;
    int beatsPerBar = 4;
    /** Beat that starts a bar (accented); bars count from here. */
    int64_t barOriginBeat = 0;
};

/** Frames per beat for [bpm] beats per minute (a beat is one denominator note, D-018). */
inline double framesPerBeat(double sampleRate, double bpm) { return sampleRate * 60.0 / bpm; }

/** Frame where [beat] starts. */
inline int64_t beatFrame(const BeatGrid &g, int64_t beat) {
    return g.anchorFrame + static_cast<int64_t>(std::llround(static_cast<double>(beat - g.anchorBeat) * g.framesPerBeat));
}

/** First beat (not before the anchor) that starts at or after [frame]. */
inline int64_t firstBeatAtOrAfter(const BeatGrid &g, int64_t frame) {
    if (frame <= g.anchorFrame) return g.anchorBeat;
    int64_t k = g.anchorBeat + static_cast<int64_t>(std::ceil(static_cast<double>(frame - g.anchorFrame) / g.framesPerBeat));
    while (k > g.anchorBeat && beatFrame(g, k - 1) >= frame) --k;
    while (beatFrame(g, k) < frame) ++k;
    return k;
}

/** Fractional beat at [frame] (may be before the anchor: negative distance). */
inline double beatAt(const BeatGrid &g, double frame) {
    return static_cast<double>(g.anchorBeat) + (frame - static_cast<double>(g.anchorFrame)) / g.framesPerBeat;
}

/** Beat 1 of a bar. */
inline bool isAccent(const BeatGrid &g, int64_t beat) {
    const int64_t d = beat - g.barOriginBeat;
    const int64_t m = ((d % g.beatsPerBar) + g.beatsPerBar) % g.beatsPerBar;
    return m == 0;
}

/**
 * The grid after a tempo or time-signature change. The change takes effect on the first beat
 * at or after [notBeforeFrame], so the beats already heard stay where they were. A new time
 * signature starts a new bar on that beat.
 */
inline BeatGrid retempo(const BeatGrid &g, int64_t notBeforeFrame, double newFramesPerBeat, int newBeatsPerBar) {
    const int64_t k = firstBeatAtOrAfter(g, notBeforeFrame);
    BeatGrid n;
    n.anchorFrame = beatFrame(g, k);
    n.anchorBeat = k;
    n.framesPerBeat = newFramesPerBeat;
    n.beatsPerBar = newBeatsPerBar;
    n.barOriginBeat = newBeatsPerBar == g.beatsPerBar ? g.barOriginBeat : k;
    return n;
}

}  // namespace mr
