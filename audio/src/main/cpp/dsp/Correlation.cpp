#include "Correlation.h"

#include <cmath>

namespace mr {

DelayEstimate findDelay(const float *signal, size_t n, const float *ref, size_t m, size_t minLag, size_t maxLag) {
    DelayEstimate result;
    if (m == 0 || n < m) return result;
    if (maxLag > n - m) maxLag = n - m;
    if (minLag > maxLag) return result;

    double refEnergy = 0.0;
    for (size_t i = 0; i < m; ++i) refEnergy += static_cast<double>(ref[i]) * ref[i];
    if (refEnergy <= 0.0) return result;

    // Energy of the signal window, updated as the window slides.
    double winEnergy = 0.0;
    for (size_t i = 0; i < m; ++i) winEnergy += static_cast<double>(signal[minLag + i]) * signal[minLag + i];

    const size_t lags = maxLag - minLag + 1;
    double best = -1.0, prev = 0.0, beforeBest = 0.0, afterBest = 0.0;
    size_t bestLag = minLag;
    bool takeNext = false;
    for (size_t k = 0; k < lags; ++k) {
        const size_t lag = minLag + k;
        if (k > 0) {
            const double out = signal[lag - 1], in = signal[lag + m - 1];
            winEnergy += in * in - out * out;
            if (winEnergy < 0.0) winEnergy = 0.0;
        }
        const float *s = signal + lag;
        double acc = 0.0;
        for (size_t i = 0; i < m; ++i) acc += static_cast<double>(s[i]) * ref[i];
        const double denom = std::sqrt(refEnergy * winEnergy);
        const double c = denom > 1e-12 ? std::fabs(acc) / denom : 0.0;
        if (takeNext) {
            afterBest = c;
            takeNext = false;
        }
        if (c > best) {
            best = c;
            bestLag = lag;
            beforeBest = prev;
            afterBest = 0.0;
            takeNext = true;
        }
        prev = c;
    }

    double frac = 0.0;
    if (bestLag > minLag && bestLag < maxLag) {
        const double denom = beforeBest - 2.0 * best + afterBest;
        if (std::fabs(denom) > 1e-12) frac = 0.5 * (beforeBest - afterBest) / denom;
        if (frac > 0.5) frac = 0.5;
        if (frac < -0.5) frac = -0.5;
    }
    result.lag = static_cast<double>(bestLag) + frac;
    result.peak = best;
    return result;
}

}  // namespace mr
