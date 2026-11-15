// Real-time pitch tracking: a 2048-sample window moved by 480 samples (10 ms at 48 kHz),
// YIN or MPM, a level gate plus the detector's confidence for voiced/unvoiced, then smoothing.
// Runs on an analysis thread (never in the audio callback); all buffers are allocated once.
#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

#include "PitchDetector.h"
#include "PitchSmoother.h"

namespace mr {

enum class PitchMethod : int { kYin = 0, kMpm = 1 };

struct PitchFrame {
    /** Engine frame the pitch belongs to (window center, minus the median's 2-frame delay). */
    int64_t frame = 0;
    /** Smoothed frequency, 0 when unvoiced. */
    double hz = 0.0;
    /** Smoothed continuous MIDI note (69 = A4), 0 when unvoiced. */
    double midi = 0.0;
    /** The detector's confidence for this frame, 0..1. */
    double confidence = 0.0;
    /** RMS level of the newest 20 ms in dBFS. */
    double levelDb = -120.0;
    bool voiced = false;
};

class PitchTracker {
public:
    static constexpr size_t kWindow = 2048;
    static constexpr size_t kHop = 480;
    /** While a note is held it may be this much quieter than the gate… */
    static constexpr double kHoldGateDb = 6.0;
    /** …and this much less confident than a new note must be (hysteresis). */
    static constexpr double kHoldConfidence = 0.2;
    /** A held note this far under its peak is fading. */
    static constexpr double kDecayDb = 6.0;
    /** How much more periodic the held period must be to overrule an octave-off reading. */
    static constexpr double kOctaveMargin = 0.03;
    /** The input high-pass: removes DC offset and rumble below the lowest note. */
    static constexpr double kHighPassHz = 45.0;

    explicit PitchTracker(int sampleRate, PitchRange range = {});

    void setMethod(PitchMethod m);
    PitchMethod method() const { return method_; }
    /** Frames quieter than this are unvoiced (from the room-noise measurement). */
    void setGateDb(double db) { gateDb_ = db; }
    double gateDb() const { return gateDb_; }
    void reset();

    /**
     * Feeds [n] samples whose first one has engine frame [firstFrame]; writes up to [capacity]
     * finished frames to [out] and returns how many. A gap in the frames restarts the window.
     */
    size_t process(const float *x, size_t n, int64_t firstFrame, PitchFrame *out, size_t capacity);

    /** Frequency ↔ continuous MIDI (A4 = 440 Hz = 69). */
    static double hzToMidi(double hz);
    static double midiToHz(double midi);

private:
    PitchFrame analyze(int64_t endFrame);

    int sampleRate_;
    PitchMethod method_ = PitchMethod::kYin;
    double gateDb_ = -55.0;
    YinDetector yin_;
    MpmDetector mpm_;
    PitchSmoother smoother_;
    std::vector<float> window_;
    size_t filled_ = 0;
    int64_t expectedFrame_ = -1;
    bool holding_ = false;
    double heldMidi_ = 0.0;
    /** The note the peak belongs to: a new note (another pitch) starts a new peak. */
    double noteMidi_ = 0.0;
    double notePeakDb_ = -120.0;
    // 2nd-order Butterworth high-pass at 45 Hz (the Unprocessed microphone has DC and rumble).
    double hpB0_ = 1.0, hpB1_ = 0.0, hpB2_ = 0.0, hpA1_ = 0.0, hpA2_ = 0.0;
    double hpX1_ = 0.0, hpX2_ = 0.0, hpY1_ = 0.0, hpY2_ = 0.0;
};

}  // namespace mr
