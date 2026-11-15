#include "PitchDetector.h"

#include <algorithm>
#include <cmath>

namespace mr {

double parabolicOffset(double left, double center, double right) {
    const double denom = left - 2.0 * center + right;
    if (std::fabs(denom) < 1e-15) return 0.0;
    const double off = 0.5 * (left - right) / denom;
    return std::clamp(off, -0.5, 0.5);
}

PitchDetector::PitchDetector(int sampleRate, size_t window, PitchRange range)
    : sampleRate_(sampleRate),
      window_(window),
      minLag_(static_cast<size_t>(std::floor(sampleRate / range.maxHz))),
      maxLag_(std::min(window / 2, static_cast<size_t>(std::ceil(sampleRate / range.minHz)))),
      acf_(window),
      r_(window),
      energy_(window + 1) {
    if (minLag_ < 2) minLag_ = 2;
}

void PitchDetector::prepare(const float *x) {
    acf_.compute(x, r_.data());
    energy_[0] = 0.0;
    for (size_t i = 0; i < window_; ++i) energy_[i + 1] = energy_[i] + static_cast<double>(x[i]) * x[i];
}

// --- YIN ---

YinDetector::YinDetector(int sampleRate, size_t window, PitchRange range, double threshold)
    : PitchDetector(sampleRate, window, range), threshold_(threshold), cmnd_(maxLag_ + 2) {}

PitchEstimate YinDetector::detect(const float *x) {
    prepare(x);
    PitchEstimate e;
    if (energy_[window_] < 1e-12) return e;  // silence
    // Difference d(τ) = energy terms − 2 r(τ); cumulative mean normalization.
    const size_t last = maxLag_ + 1;
    cmnd_[0] = 1.0;
    double running = 0.0;
    for (size_t tau = 1; tau <= last; ++tau) {
        const double d = std::max(0.0, energyTerms(tau) - 2.0 * r_[tau]);
        running += d;
        cmnd_[tau] = running > 0.0 ? d * static_cast<double>(tau) / running : 1.0;
    }
    // First dip under the threshold, followed down to its minimum.
    size_t tau = 0;
    for (size_t t = minLag_; t <= maxLag_; ++t) {
        if (cmnd_[t] < threshold_) {
            while (t + 1 <= maxLag_ && cmnd_[t + 1] < cmnd_[t]) ++t;
            tau = t;
            break;
        }
    }
    if (tau == 0) {  // nothing under the threshold: best guess, low confidence
        tau = minLag_;
        for (size_t t = minLag_; t <= maxLag_; ++t)
            if (cmnd_[t] < cmnd_[tau]) tau = t;
    }
    // A dip at half the true period (weak fundamental, strong 2nd harmonic) is shallow compared
    // with the dip at the full period; prefer the full period then.
    if (2 * tau <= maxLag_ && cmnd_[tau] > 0.05) {
        size_t t2 = 2 * tau;
        if (cmnd_[t2 - 1] < cmnd_[t2]) --t2;
        if (cmnd_[t2 + 1] < cmnd_[t2]) ++t2;
        if (cmnd_[t2] < 0.5 * cmnd_[tau]) tau = t2;
    }
    const double off = (tau > 1 && tau < last) ? parabolicOffset(cmnd_[tau - 1], cmnd_[tau], cmnd_[tau + 1]) : 0.0;
    e.hz = sampleRate_ / (static_cast<double>(tau) + off);
    e.confidence = std::clamp(1.0 - cmnd_[tau], 0.0, 1.0);
    return e;
}

// --- MPM ---

MpmDetector::MpmDetector(int sampleRate, size_t window, PitchRange range, double k)
    : PitchDetector(sampleRate, window, range), k_(k), nsdf_(maxLag_ + 2) {}

PitchEstimate MpmDetector::detect(const float *x) {
    prepare(x);
    PitchEstimate e;
    if (energy_[window_] < 1e-12) return e;
    const size_t last = maxLag_ + 1;
    for (size_t tau = 0; tau <= last; ++tau) {
        const double m = energyTerms(tau);
        nsdf_[tau] = m > 0.0 ? 2.0 * r_[tau] / m : 0.0;
    }
    // Key maxima: the highest point between each positive-going zero crossing and the next
    // negative-going one, after the first negative region.
    size_t tau = 1;
    while (tau < last && nsdf_[tau] > 0.0) ++tau;
    size_t keys[64];
    size_t keyCount = 0;
    double highest = 0.0;
    while (tau < last && keyCount < 64) {
        while (tau < last && nsdf_[tau] <= 0.0) ++tau;  // to the positive-going crossing
        if (tau >= last) break;
        size_t best = tau;
        while (tau < last && nsdf_[tau] > 0.0) {
            if (nsdf_[tau] > nsdf_[best]) best = tau;
            ++tau;
        }
        if (best >= minLag_ && best <= maxLag_) {
            keys[keyCount++] = best;
            highest = std::max(highest, nsdf_[best]);
        }
    }
    if (keyCount == 0 || highest <= 0.0) return e;
    const double cut = k_ * highest;
    size_t pick = keys[0];
    for (size_t i = 0; i < keyCount; ++i) {
        if (nsdf_[keys[i]] >= cut) {
            pick = keys[i];
            break;
        }
    }
    const double off = parabolicOffset(nsdf_[pick - 1], nsdf_[pick], nsdf_[pick + 1]);
    const double peak = nsdf_[pick] - 0.25 * (nsdf_[pick - 1] - nsdf_[pick + 1]) * off;
    e.hz = sampleRate_ / (static_cast<double>(pick) + off);
    e.confidence = std::clamp(peak, 0.0, 1.0);
    return e;
}

}  // namespace mr
