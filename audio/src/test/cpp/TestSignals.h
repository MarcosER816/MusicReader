// Helpers for synthetic test signals.
#pragma once

#include <cstdint>
#include <random>
#include <vector>

namespace mr::test {

/** Adds [src] times [gain] into [dst] starting at [at]. */
inline void mixInto(std::vector<float> &dst, const std::vector<float> &src, size_t at, float gain) {
    for (size_t i = 0; i < src.size() && at + i < dst.size(); ++i) dst[at + i] += gain * src[i];
}

/** Adds white noise of standard deviation [sigma]. */
inline void addNoise(std::vector<float> &x, float sigma, uint32_t seed) {
    std::mt19937 rng(seed);
    std::normal_distribution<float> d(0.f, sigma);
    for (float &v : x) v += d(rng);
}

/** Simple one-pole low-pass, like a small speaker or a mic with poor highs. */
inline void lowPass(std::vector<float> &x, float a) {
    float y = 0.f;
    for (float &v : x) {
        y += a * (v - y);
        v = y;
    }
}

}  // namespace mr::test
