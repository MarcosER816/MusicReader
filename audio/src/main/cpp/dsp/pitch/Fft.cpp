#include "Fft.h"

#include <cmath>
#include <utility>

namespace mr {

Fft::Fft(size_t size) : n_(size), bitReverse_(size), cos_(size / 2), sin_(size / 2) {
    size_t bits = 0;
    while ((size_t{1} << bits) < n_) ++bits;
    for (size_t i = 0; i < n_; ++i) {
        size_t r = 0;
        for (size_t b = 0; b < bits; ++b)
            if (i & (size_t{1} << b)) r |= size_t{1} << (bits - 1 - b);
        bitReverse_[i] = r;
    }
    for (size_t k = 0; k < n_ / 2; ++k) {
        const double a = -2.0 * 3.14159265358979323846 * static_cast<double>(k) / static_cast<double>(n_);
        cos_[k] = static_cast<float>(std::cos(a));
        sin_[k] = static_cast<float>(std::sin(a));
    }
}

void Fft::transform(float *re, float *im) const {
    for (size_t i = 0; i < n_; ++i) {
        const size_t j = bitReverse_[i];
        if (j > i) {
            std::swap(re[i], re[j]);
            std::swap(im[i], im[j]);
        }
    }
    for (size_t len = 2; len <= n_; len <<= 1) {
        const size_t half = len / 2, step = n_ / len;
        for (size_t start = 0; start < n_; start += len) {
            for (size_t k = 0; k < half; ++k) {
                const float wr = cos_[k * step], wi = sin_[k * step];
                const size_t a = start + k, b = a + half;
                const float xr = re[b] * wr - im[b] * wi;
                const float xi = re[b] * wi + im[b] * wr;
                re[b] = re[a] - xr;
                im[b] = im[a] - xi;
                re[a] += xr;
                im[a] += xi;
            }
        }
    }
}

void Fft::forward(float *re, float *im) const { transform(re, im); }

void Fft::inverse(float *re, float *im) const {
    // IFFT(x) = conj(FFT(conj(x))) / n
    for (size_t i = 0; i < n_; ++i) im[i] = -im[i];
    transform(re, im);
    const float s = 1.0f / static_cast<float>(n_);
    for (size_t i = 0; i < n_; ++i) {
        re[i] *= s;
        im[i] = -im[i] * s;
    }
}

Autocorrelation::Autocorrelation(size_t window) : window_(window), fft_(window * 2), re_(window * 2), im_(window * 2) {}

void Autocorrelation::compute(const float *x, float *out) {
    const size_t n = fft_.size();
    for (size_t i = 0; i < window_; ++i) {
        re_[i] = x[i];
        im_[i] = 0.f;
    }
    for (size_t i = window_; i < n; ++i) re_[i] = im_[i] = 0.f;
    fft_.forward(re_.data(), im_.data());
    for (size_t i = 0; i < n; ++i) {
        re_[i] = re_[i] * re_[i] + im_[i] * im_[i];
        im_[i] = 0.f;
    }
    fft_.inverse(re_.data(), im_.data());
    for (size_t i = 0; i < window_; ++i) out[i] = re_[i];
}

}  // namespace mr
