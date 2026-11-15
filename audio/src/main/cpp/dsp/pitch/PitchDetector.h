// Pitch detectors: YIN and the McLeod pitch method behind one interface.
// Own implementations of the published algorithms (de Cheveigné & Kawahara 2002;
// McLeod & Wyvill 2005). Every buffer is allocated in the constructor.
#pragma once

#include <cstddef>
#include <vector>

#include "Fft.h"

namespace mr {

struct PitchEstimate {
    /** Fundamental frequency in Hz; 0 when no period was found. */
    double hz = 0.0;
    /** 0..1 periodicity: YIN 1 − d′(τ), MPM the normalized peak height. */
    double confidence = 0.0;
};

struct PitchRange {
    double minHz = 60.0;
    double maxHz = 1200.0;
};

class PitchDetector {
public:
    virtual ~PitchDetector() = default;
    /** Analyzes one window of [windowSize()] samples. */
    virtual PitchEstimate detect(const float *window) = 0;
    /** Confidence from which a frame can count as voiced. */
    virtual double voicedConfidence() const = 0;
    size_t windowSize() const { return window_; }

protected:
    PitchDetector(int sampleRate, size_t window, PitchRange range);

    /** Fills r_ (autocorrelation) and energy_ (prefix sums of squares) for [x]. */
    void prepare(const float *x);
    /** Σ x² over j = 0..n-1-τ plus Σ x² over j = τ..n-1. */
    double energyTerms(size_t tau) const {
        const size_t n = window_;
        return (energy_[n - tau]) + (energy_[n] - energy_[tau]);
    }

    int sampleRate_;
    size_t window_;
    size_t minLag_;
    size_t maxLag_;
    Autocorrelation acf_;
    std::vector<float> r_;
    std::vector<double> energy_;
};

/** YIN: cumulative mean normalized difference d′(τ) with an absolute threshold. */
class YinDetector : public PitchDetector {
public:
    YinDetector(int sampleRate, size_t window = 2048, PitchRange range = {}, double threshold = 0.12);
    PitchEstimate detect(const float *window) override;
    double voicedConfidence() const override { return 0.85; }

private:
    double threshold_;
    std::vector<double> cmnd_;
};

/** McLeod pitch method: normalized square difference n(τ), first key maximum ≥ k · highest. */
class MpmDetector : public PitchDetector {
public:
    MpmDetector(int sampleRate, size_t window = 2048, PitchRange range = {}, double k = 0.9);
    PitchEstimate detect(const float *window) override;
    double voicedConfidence() const override { return 0.80; }

private:
    double k_;
    std::vector<double> nsdf_;
};

/** Parabolic peak/valley offset (−0.5..0.5) of three neighbouring values. */
double parabolicOffset(double left, double center, double right);

}  // namespace mr
