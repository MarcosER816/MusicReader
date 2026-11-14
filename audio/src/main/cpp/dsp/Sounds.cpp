#include "Sounds.h"

#include <algorithm>
#include <cmath>

namespace mr {

namespace {
constexpr double kPi = 3.14159265358979323846;
}

std::vector<float> makeChirp(int sampleRate, double f0, double f1, double seconds, float amplitude) {
    const int n = static_cast<int>(std::lround(seconds * sampleRate));
    std::vector<float> out(static_cast<size_t>(n));
    const double k = (f1 - f0) / seconds;  // Hz per second
    const int fade = std::max(1, n / 10);
    for (int i = 0; i < n; ++i) {
        const double t = static_cast<double>(i) / sampleRate;
        const double phase = 2.0 * kPi * (f0 * t + 0.5 * k * t * t);
        double w = 1.0;
        if (i < fade) w = 0.5 - 0.5 * std::cos(kPi * i / fade);
        else if (i >= n - fade) w = 0.5 - 0.5 * std::cos(kPi * (n - 1 - i) / fade);
        out[static_cast<size_t>(i)] = static_cast<float>(amplitude * w * std::sin(phase));
    }
    return out;
}

std::vector<float> makeClick(int sampleRate, double hz, double seconds, float amplitude) {
    const int n = static_cast<int>(std::lround(seconds * sampleRate));
    std::vector<float> out(static_cast<size_t>(n));
    const double tau = seconds / 5.0;
    const int attack = std::max(1, sampleRate / 2000);  // 0.5 ms
    for (int i = 0; i < n; ++i) {
        const double t = static_cast<double>(i) / sampleRate;
        const double a = i < attack ? static_cast<double>(i) / attack : 1.0;
        out[static_cast<size_t>(i)] = static_cast<float>(amplitude * a * std::exp(-t / tau) * std::sin(2.0 * kPi * hz * t));
    }
    return out;
}

}  // namespace mr
