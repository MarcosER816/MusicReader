#include "Calibration.h"

#include <algorithm>
#include <cmath>

#include "Correlation.h"
#include "Onsets.h"

namespace mr {

double median(std::vector<double> &v) {
    if (v.empty()) return 0.0;
    std::sort(v.begin(), v.end());
    const size_t h = v.size() / 2;
    return v.size() % 2 ? v[h] : 0.5 * (v[h - 1] + v[h]);
}

namespace {

double madOf(const std::vector<double> &values, double center) {
    std::vector<double> d;
    d.reserve(values.size());
    for (double x : values) d.push_back(std::fabs(x - center));
    return median(d);
}

}  // namespace

LatencyResult analyzeChirps(const float *recording, size_t n, int64_t recordingStartFrame, const int64_t *chirpFrames,
                            size_t chirpCount, const float *chirp, size_t chirpLength, int sampleRate,
                            double maxLatencyMs) {
    LatencyResult r;
    r.expected = static_cast<int>(chirpCount);
    const auto maxLag = static_cast<size_t>(maxLatencyMs * sampleRate / 1000.0);
    std::vector<double> lagsMs, peaks;
    for (size_t k = 0; k < chirpCount; ++k) {
        const int64_t start = chirpFrames[k] - recordingStartFrame;  // index of the emit frame
        if (start < 0 || static_cast<size_t>(start) + chirpLength >= n) continue;
        const size_t avail = n - static_cast<size_t>(start);
        const DelayEstimate d = findDelay(recording + start, avail, chirp, chirpLength, 0, maxLag);
        if (d.lag < 0.0 || d.peak < 0.2) continue;  // not heard (noise alone peaks near 0.1)
        lagsMs.push_back(d.lag * 1000.0 / sampleRate);
        peaks.push_back(d.peak);
    }
    r.found = static_cast<int>(lagsMs.size());
    if (lagsMs.empty()) return r;

    std::vector<double> sortedLags(lagsMs);
    const double med = median(sortedLags);
    // Mean of the measurements within 1 ms of the median: precise and robust to one outlier.
    double sum = 0.0;
    int inliers = 0;
    for (double l : lagsMs) {
        if (std::fabs(l - med) <= 1.0) {
            sum += l;
            ++inliers;
        }
    }
    std::vector<double> sortedPeaks(peaks);
    const double peakMed = median(sortedPeaks);
    r.latencyMs = sum / inliers;
    r.spreadMs = madOf(lagsMs, med);
    const double agreement = static_cast<double>(inliers) / static_cast<double>(chirpCount);
    const double clarity = std::min(1.0, peakMed / 0.3);
    r.confidence = agreement * clarity;
    return r;
}

LatencyResult analyzeTaps(const float *recording, size_t n, int64_t recordingStartFrame, const int64_t *clickFrames,
                          size_t clickCount, int sampleRate) {
    LatencyResult r;
    r.expected = static_cast<int>(clickCount);
    if (clickCount < 2) return r;
    const std::vector<Onset> onsets = detectOnsets(recording, n, sampleRate);
    const double interval = static_cast<double>(clickFrames[clickCount - 1] - clickFrames[0]) /
                            static_cast<double>(clickCount - 1);
    std::vector<double> offsetsMs;
    for (size_t k = 0; k < clickCount; ++k) {
        const double click = static_cast<double>(clickFrames[k] - recordingStartFrame);
        const double from = click - 0.25 * interval, to = click + 0.75 * interval;
        const Onset *best = nullptr;
        for (const Onset &o : onsets) {
            const auto s = static_cast<double>(o.sample);
            if (s < from || s >= to) continue;
            if (best == nullptr || o.strength > best->strength) best = &o;
        }
        if (best != nullptr) offsetsMs.push_back((static_cast<double>(best->sample) - click) * 1000.0 / sampleRate);
    }
    r.found = static_cast<int>(offsetsMs.size());
    if (offsetsMs.empty()) return r;
    std::vector<double> sorted(offsetsMs);
    r.latencyMs = median(sorted);
    r.spreadMs = madOf(offsetsMs, r.latencyMs);
    const double found = static_cast<double>(r.found) / static_cast<double>(clickCount);
    r.confidence = found * std::max(0.0, 1.0 - r.spreadMs / 30.0);
    return r;
}

}  // namespace mr
