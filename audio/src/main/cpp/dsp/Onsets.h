// Onsets (claps, strums, taps) in a mono recording.
#pragma once

#include <cstddef>
#include <vector>

namespace mr {

struct Onset {
    /** Sample index where the sound starts. */
    size_t sample = 0;
    /** How much louder than just before (energy ratio); larger is a clearer onset. */
    double strength = 0.0;
};

/**
 * Finds sudden rises in short-term energy: a 1 ms hop, 5 ms windows compared with the 25 ms
 * before them. An onset needs a rise of [minRatio] and must be louder than the quiet parts of
 * the recording; onsets closer than [refractorySeconds] are merged.
 */
std::vector<Onset> detectOnsets(const float *x, size_t n, int sampleRate, double minRatio = 6.0,
                                double refractorySeconds = 0.08);

}  // namespace mr
