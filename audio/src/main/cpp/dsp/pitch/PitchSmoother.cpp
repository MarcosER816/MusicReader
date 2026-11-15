#include "PitchSmoother.h"

#include <algorithm>
#include <cmath>

namespace mr {

void PitchSmoother::reset() {
    voiced_.fill(false);
    next_ = 0;
    count_ = 0;
    hasReference_ = false;
    sinceReference_ = 0;
    folds_ = 0;
    silentRun_ = 0;
}

PitchSmoother::Output PitchSmoother::push(bool voiced, double midi) {
    Output out;
    if (voiced && silentRun_ >= kGapFrames) {
        // A new note after a breath: nothing from the note before may pull it (no octave
        // correction against it, no old values in the median).
        voiced_.fill(false);
        hasReference_ = false;
        folds_ = 0;
    }
    silentRun_ = voiced ? 0 : silentRun_ + 1;
    // Octave correction against the last smoothed note, only while the voice is continuous.
    if (voiced && hasReference_ && sinceReference_ <= 10) {
        const double d = midi - reference_;
        const double k = std::round(d / 12.0);
        if ((k == 1.0 || k == -1.0) && std::fabs(d - 12.0 * k) < 1.0) {
            if (folds_ < kMaxFolds) {
                midi -= 12.0 * k;
                ++folds_;
                out.folded = true;
            } else {
                // It stayed there: a real leap. The folded frames were the new note too.
                folds_ = 0;
                for (int i = 0; i < kSize; ++i)
                    if (voiced_[static_cast<size_t>(i)]) midi_[static_cast<size_t>(i)] += 12.0 * k;
                reference_ = midi;
            }
        } else {
            folds_ = 0;
        }
    }

    voiced_[static_cast<size_t>(next_)] = voiced;
    midi_[static_cast<size_t>(next_)] = midi;
    next_ = (next_ + 1) % kSize;
    count_ = std::min(count_ + 1, kSize);

    std::array<double, kSize> v{};
    int n = 0;
    for (int i = 0; i < count_; ++i)
        if (voiced_[static_cast<size_t>(i)]) v[static_cast<size_t>(n++)] = midi_[static_cast<size_t>(i)];
    ++sinceReference_;
    if (n < 3) return out;  // fewer than 3 of the last 5 voiced
    // Insertion sort of at most 5 values.
    for (int i = 1; i < n; ++i) {
        const double key = v[static_cast<size_t>(i)];
        int j = i - 1;
        while (j >= 0 && v[static_cast<size_t>(j)] > key) {
            v[static_cast<size_t>(j + 1)] = v[static_cast<size_t>(j)];
            --j;
        }
        v[static_cast<size_t>(j + 1)] = key;
    }
    out.voiced = true;
    out.midi = n % 2 ? v[static_cast<size_t>(n / 2)] : 0.5 * (v[static_cast<size_t>(n / 2 - 1)] + v[static_cast<size_t>(n / 2)]);
    reference_ = out.midi;
    hasReference_ = true;
    sinceReference_ = 0;
    return out;
}

}  // namespace mr
