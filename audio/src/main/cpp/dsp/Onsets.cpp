#include "Onsets.h"

#include <algorithm>
#include <cmath>

namespace mr {

std::vector<Onset> detectOnsets(const float *x, size_t n, int sampleRate, double minRatio, double refractorySeconds) {
    std::vector<Onset> onsets;
    const size_t hop = std::max<size_t>(1, static_cast<size_t>(sampleRate / 1000));  // 1 ms
    const size_t win = 5 * hop;                                                     // 5 ms
    if (n < win * 8) return onsets;

    // Energy per hop over a 5 ms window starting at the hop.
    const size_t frames = (n - win) / hop + 1;
    std::vector<double> e(frames);
    for (size_t j = 0; j < frames; ++j) {
        double acc = 0.0;
        const float *p = x + j * hop;
        for (size_t i = 0; i < win; ++i) acc += static_cast<double>(p[i]) * p[i];
        e[j] = acc / static_cast<double>(win);
    }

    // The quiet level of the recording: the 20th percentile of the energy.
    std::vector<double> sorted(e);
    std::nth_element(sorted.begin(), sorted.begin() + static_cast<long>(frames / 5), sorted.end());
    const double floorEnergy = std::max(sorted[frames / 5], 1e-10);
    const double minEnergy = floorEnergy * 10.0;  // 10 dB above the quiet level

    const size_t back = 25, gap = 5;  // compare with [j-25, j-5) hops
    const size_t refractory = static_cast<size_t>(refractorySeconds * 1000.0);
    size_t last = 0;
    bool any = false;
    for (size_t j = back; j < frames; ++j) {
        if (e[j] < minEnergy) continue;
        double before = 0.0;
        for (size_t k = j - back; k < j - gap; ++k) before += e[k];
        before = std::max(before / static_cast<double>(back - gap), floorEnergy);
        const double ratio = e[j] / before;
        if (ratio < minRatio) continue;
        // Refine to the first sample in the window that reaches half the window's peak.
        const float *p = x + j * hop;
        float peak = 0.f;
        for (size_t i = 0; i < win; ++i) peak = std::max(peak, std::fabs(p[i]));
        size_t at = j * hop;
        for (size_t i = 0; i < win; ++i) {
            if (std::fabs(p[i]) >= 0.5f * peak) {
                at = j * hop + i;
                break;
            }
        }
        if (any && j - last < refractory) {
            if (ratio > onsets.back().strength && j - last < gap) {
                onsets.back().strength = ratio;  // same onset, still rising
            }
            continue;
        }
        onsets.push_back({at, ratio});
        last = j;
        any = true;
    }
    return onsets;
}

}  // namespace mr
