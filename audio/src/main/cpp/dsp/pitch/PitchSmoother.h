// Smoothing of a frame-by-frame pitch track: octave-jump correction, then the median of the
// last 5 frames. A frame is voiced when at least 3 of the last 5 were.
#pragma once

#include <array>
#include <cstdint>

namespace mr {

class PitchSmoother {
public:
    static constexpr int kSize = 5;
    /** A ±12 semitone jump is folded back for this many frames before it is believed. */
    static constexpr int kMaxFolds = 3;

    struct Output {
        bool voiced = false;
        double midi = 0.0;
        /** True when this frame's raw value was folded by an octave. */
        bool folded = false;
    };

    /**
     * [decaying]: the held note is fading (level well under its peak). Then an octave jump is
     * always folded back: a fading string loses its fundamental first, it does not leap.
     */
    Output push(bool voiced, double midi, bool decaying = false);
    void reset();

private:
    std::array<bool, kSize> voiced_{};
    std::array<double, kSize> midi_{};
    int next_ = 0;
    int count_ = 0;
    bool hasReference_ = false;
    double reference_ = 0.0;
    int sinceReference_ = 0;
    int folds_ = 0;
};

}  // namespace mr
