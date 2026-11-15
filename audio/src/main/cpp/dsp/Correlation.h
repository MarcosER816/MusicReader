// Finding a known sound in a recording by normalized cross-correlation.
#pragma once

#include <cstddef>

namespace mr {

struct DelayEstimate {
    /** Lag in samples (fractional, from parabolic interpolation); -1 if nothing was searched. */
    double lag = -1.0;
    /** Peak normalized correlation, 0..1 (1 = the recording is an exact scaled copy). */
    double peak = 0.0;
};

/**
 * Where [ref] (m samples) best matches [signal] (n samples), for lags minLag..maxLag. The
 * correlation is normalized by the energy of both, so loudness does not matter, and its absolute
 * value is used, so an inverted microphone still matches.
 */
DelayEstimate findDelay(const float *signal, size_t n, const float *ref, size_t m, size_t minLag, size_t maxLag);

}  // namespace mr
