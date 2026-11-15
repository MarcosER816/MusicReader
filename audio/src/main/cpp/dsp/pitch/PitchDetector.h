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
    /**
     * After detect(): the best period within ±3 % of [hz] in the same window, refined, with
     * its confidence (hz = 0 if there is no dip/peak there). Used to keep following a fading
     * note when the free choice lands on a sub- or overtone.
     */
    virtual PitchEstimate estimateNear(double hz) const = 0;
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

    /** Lag search range [from, to] around the period of [hz] (±3 %), clamped to the detector's range. */
    bool lagsNear(double hz, size_t &from, size_t &to) const;

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
    PitchEstimate estimateNear(double hz) const override;

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
    PitchEstimate estimateNear(double hz) const override;

private:
    double k_;
    std::vector<double> nsdf_;
};

/** Parabolic peak/valley offset (−0.5..0.5) of three neighbouring values. */
double parabolicOffset(double left, double center, double right);

}  // namespace mr
