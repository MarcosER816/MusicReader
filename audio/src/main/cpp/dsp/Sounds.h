// Test and click sounds, generated once (never in the audio callback).
#pragma once

#include <vector>

namespace mr {

/**
 * Linear chirp from [f0] to [f1] Hz over [seconds], with short raised-cosine fades so it starts
 * and ends at zero. A chirp correlates to one sharp peak, which makes the delay easy to find.
 */
std::vector<float> makeChirp(int sampleRate, double f0, double f1, double seconds, float amplitude);

/** A short decaying sine "tick": [hz] pitch, [seconds] long. */
std::vector<float> makeClick(int sampleRate, double hz, double seconds, float amplitude);

/** The calibration chirp: 40 ms, 600 Hz to 6 kHz (phone speakers play little below 500 Hz). */
inline std::vector<float> calibrationChirp(int sampleRate) { return makeChirp(sampleRate, 600.0, 6000.0, 0.040, 0.8f); }

/** Metronome clicks: the accent (beat 1) is higher and louder. */
inline std::vector<float> accentClick(int sampleRate) { return makeClick(sampleRate, 1760.0, 0.030, 0.9f); }
inline std::vector<float> normalClick(int sampleRate) { return makeClick(sampleRate, 1175.0, 0.030, 0.6f); }

}  // namespace mr
