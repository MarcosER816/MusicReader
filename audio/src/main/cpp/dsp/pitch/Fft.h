// Radix-2 complex FFT (own code). Tables are built in the constructor; transforms never allocate.
#pragma once

#include <cstddef>
#include <vector>

namespace mr {

class Fft {
public:
    /** [size] must be a power of two. */
    explicit Fft(size_t size);

    size_t size() const { return n_; }

    /** In-place forward transform of re/im (each [size] long). */
    void forward(float *re, float *im) const;

    /** In-place inverse transform, scaled by 1/size. */
    void inverse(float *re, float *im) const;

private:
    void transform(float *re, float *im) const;

    size_t n_;
    std::vector<size_t> bitReverse_;
    std::vector<float> cos_;
    std::vector<float> sin_;
};

/**
 * Autocorrelation r[τ] = Σ x[j]·x[j+τ] for τ = 0..n-1 of an [n]-sample window, through an FFT of
 * twice the size (no circular wrap). Buffers are allocated once.
 */
class Autocorrelation {
public:
    explicit Autocorrelation(size_t window);
    /** Writes r[0..window) into [out]. */
    void compute(const float *x, float *out);

private:
    size_t window_;
    Fft fft_;
    std::vector<float> re_, im_;
};

}  // namespace mr
