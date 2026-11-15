#include "PitchTracker.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace mr {

PitchTracker::PitchTracker(int sampleRate, PitchRange range)
    : sampleRate_(sampleRate), yin_(sampleRate, kWindow, range), mpm_(sampleRate, kWindow, range), window_(kWindow) {}

void PitchTracker::setMethod(PitchMethod m) {
    if (m != method_) smoother_.reset();
    method_ = m;
}

void PitchTracker::reset() {
    filled_ = 0;
    expectedFrame_ = -1;
    smoother_.reset();
}

double PitchTracker::hzToMidi(double hz) { return 69.0 + 12.0 * std::log2(hz / 440.0); }
double PitchTracker::midiToHz(double midi) { return 440.0 * std::pow(2.0, (midi - 69.0) / 12.0); }

size_t PitchTracker::process(const float *x, size_t n, int64_t firstFrame, PitchFrame *out, size_t capacity) {
    if (expectedFrame_ >= 0 && firstFrame != expectedFrame_) reset();  // samples were lost
    expectedFrame_ = firstFrame + static_cast<int64_t>(n);
    size_t produced = 0;
    for (size_t i = 0; i < n; ++i) {
        window_[filled_++] = x[i];
        if (filled_ < kWindow) continue;
        if (produced < capacity) out[produced++] = analyze(firstFrame + static_cast<int64_t>(i) + 1);
        // Slide by one hop: once per 10 ms.
        std::memmove(window_.data(), window_.data() + kHop, (kWindow - kHop) * sizeof(float));
        filled_ = kWindow - kHop;
    }
    return produced;
}

PitchFrame PitchTracker::analyze(int64_t endFrame) {
    PitchFrame f;
    // Level of the newest 20 ms.
    const size_t levelLen = static_cast<size_t>(sampleRate_ / 50);
    double acc = 0.0;
    for (size_t i = kWindow - levelLen; i < kWindow; ++i) acc += static_cast<double>(window_[i]) * window_[i];
    const double rms = std::sqrt(acc / static_cast<double>(levelLen));
    f.levelDb = rms > 1e-6 ? 20.0 * std::log10(rms) : -120.0;

    PitchDetector &d = method_ == PitchMethod::kYin ? static_cast<PitchDetector &>(yin_) : static_cast<PitchDetector &>(mpm_);
    const PitchEstimate e = d.detect(window_.data());
    f.confidence = e.confidence;
    const bool rawVoiced = e.hz > 0.0 && f.levelDb >= gateDb_ && e.confidence >= d.voicedConfidence();
    const PitchSmoother::Output s = smoother_.push(rawVoiced, rawVoiced ? hzToMidi(e.hz) : 0.0);
    f.voiced = s.voiced;
    if (s.voiced) {
        f.midi = s.midi;
        f.hz = midiToHz(s.midi);
    }
    // The window's center, and the median of 5 lags by 2 frames.
    f.frame = endFrame - static_cast<int64_t>(kWindow / 2) - static_cast<int64_t>(2 * kHop);
    return f;
}

}  // namespace mr
