// Latency calibration: automatic (chirps) and manual (playing along with clicks).
#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace mr {

struct LatencyResult {
    /** Round-trip latency in milliseconds; negative if it could not be measured. */
    double latencyMs = -1.0;
    /** 0..1: how much the measurement can be trusted. */
    double confidence = 0.0;
    /** How many of the chirps or clicks were found. */
    int found = 0;
    int expected = 0;
    /** Spread of the single measurements (median absolute deviation), ms. */
    double spreadMs = 0.0;
};

/**
 * Automatic calibration. [recording] holds input samples where sample 0 has frame index
 * [recordingStartFrame] on the output's frame counter; chirp k was played from output frame
 * [chirpFrames][k]. Each chirp is searched up to [maxLatencyMs] after it was played.
 */
LatencyResult analyzeChirps(const float *recording, size_t n, int64_t recordingStartFrame, const int64_t *chirpFrames,
                            size_t chirpCount, const float *chirp, size_t chirpLength, int sampleRate,
                            double maxLatencyMs = 800.0);

/**
 * Manual calibration: the user claps or strums with clicks played at [clickFrames]. For each
 * click the strongest onset from a quarter interval before to three quarters after is taken;
 * the result is the median offset.
 */
LatencyResult analyzeTaps(const float *recording, size_t n, int64_t recordingStartFrame, const int64_t *clickFrames,
                          size_t clickCount, int sampleRate);

/** Median of [v] (v is reordered). 0 for an empty vector. */
double median(std::vector<double> &v);

}  // namespace mr
