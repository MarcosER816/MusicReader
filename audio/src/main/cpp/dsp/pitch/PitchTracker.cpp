#include "PitchTracker.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace mr {

PitchTracker::PitchTracker(int sampleRate, PitchRange range)
    : sampleRate_(sampleRate), yin_(sampleRate, kWindow, range), mpm_(sampleRate, kWindow, range), window_(kWindow) {
    // Bilinear-transform Butterworth high-pass coefficients (Q = 1/√2).
    const double w = std::tan(3.14159265358979323846 * kHighPassHz / sampleRate);
    const double q = 1.0 / std::sqrt(2.0);
    const double norm = 1.0 / (1.0 + w / q + w * w);
    hpB0_ = norm;
    hpB1_ = -2.0 * norm;
    hpB2_ = norm;
    hpA1_ = 2.0 * (w * w - 1.0) * norm;
    hpA2_ = (1.0 - w / q + w * w) * norm;
}

void PitchTracker::setMethod(PitchMethod m) {
    if (m != method_) smoother_.reset();
    method_ = m;
}

void PitchTracker::reset() {
    filled_ = 0;
    expectedFrame_ = -1;
    holding_ = false;
    hpX1_ = hpX2_ = hpY1_ = hpY2_ = 0.0;
    smoother_.reset();
}

double PitchTracker::hzToMidi(double hz) { return 69.0 + 12.0 * std::log2(hz / 440.0); }
double PitchTracker::midiToHz(double midi) { return 440.0 * std::pow(2.0, (midi - 69.0) / 12.0); }

size_t PitchTracker::process(const float *x, size_t n, int64_t firstFrame, PitchFrame *out, size_t capacity) {
    if (expectedFrame_ >= 0 && firstFrame != expectedFrame_) reset();  // samples were lost
    expectedFrame_ = firstFrame + static_cast<int64_t>(n);
    size_t produced = 0;
    for (size_t i = 0; i < n; ++i) {
        const double in = x[i];
        const double y = hpB0_ * in + hpB1_ * hpX1_ + hpB2_ * hpX2_ - hpA1_ * hpY1_ - hpA2_ * hpY2_;
        hpX2_ = hpX1_;
        hpX1_ = in;
        hpY2_ = hpY1_;
        hpY1_ = y;
        window_[filled_++] = static_cast<float>(y);
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
    bool rawVoiced = false;
    double rawMidi = 0.0;
    const bool decaying = holding_ && f.levelDb < notePeakDb_ - kDecayDb;
    const bool holdLevel = holding_ && f.levelDb >= gateDb_ - kHoldGateDb;
    const double holdConfidence = d.voicedConfidence() - kHoldConfidence;
    auto nearHeld = [&](double m) {
        const double diff = m - heldMidi_;
        const double k = std::round(diff / 12.0);
        return std::fabs(k) <= 1.0 && std::fabs(diff - 12.0 * k) < 1.0;  // same note or an octave off
    };
    if (e.hz > 0.0) {
        rawMidi = hzToMidi(e.hz);
        // A new note must pass the gate and the detector's confidence.
        rawVoiced = f.levelDb >= gateDb_ && e.confidence >= d.voicedConfidence();
        // Hysteresis: a held note keeps sounding with relaxed limits while it stays on that
        // note (or an octave off, which the smoother folds back).
        if (!rawVoiced && holdLevel && e.confidence >= holdConfidence && nearHeld(rawMidi)) rawVoiced = true;
    }
    // A fading note whose free choice landed elsewhere (a sub- or overtone in the noisy tail):
    // if the window is still periodic at the held note, the note goes on there.
    if (decaying && holdLevel && !(e.hz > 0.0 && nearHeld(rawMidi))) {
        const PitchEstimate alt = d.estimateNear(midiToHz(heldMidi_));
        if (alt.hz > 0.0 && alt.confidence >= holdConfidence) {
            rawVoiced = true;
            rawMidi = hzToMidi(alt.hz);
            f.confidence = alt.confidence;
        }
    }
    // A fading string loses its fundamental first, so the free choice can land an octave up.
    // If the window is clearly more periodic at the held note's period, it is still that note;
    // a real (sung) octave leap is at least as periodic at the new period and is followed.
    if (decaying && holdLevel && rawVoiced && e.hz > 0.0 && std::fabs(rawMidi - heldMidi_) > 6.0 && nearHeld(rawMidi)) {
        const PitchEstimate alt = d.estimateNear(midiToHz(heldMidi_));
        if (alt.hz > 0.0 && alt.confidence > e.confidence + kOctaveMargin) {
            rawMidi = hzToMidi(alt.hz);
            f.confidence = alt.confidence;
        }
    }
    const PitchSmoother::Output s = smoother_.push(rawVoiced, rawVoiced ? rawMidi : 0.0);
    f.voiced = s.voiced;
    if (s.voiced) {
        f.midi = s.midi;
        f.hz = midiToHz(s.midi);
        // The peak belongs to one note: another pitch (sung legato) starts a new one.
        if (!holding_ || std::fabs(s.midi - noteMidi_) > 0.75) {
            noteMidi_ = s.midi;
            notePeakDb_ = f.levelDb;
        } else {
            notePeakDb_ = std::max(notePeakDb_, f.levelDb);
        }
        holding_ = true;
        heldMidi_ = s.midi;
    } else {
        holding_ = false;
    }
    // The window's center, and the median of 5 lags by 2 frames.
    f.frame = endFrame - static_cast<int64_t>(kWindow / 2) - static_cast<int64_t>(2 * kHop);
    return f;
}

}  // namespace mr
