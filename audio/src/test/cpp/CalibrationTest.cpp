#include <gtest/gtest.h>

#include <random>
#include <vector>

#include "Calibration.h"
#include "Onsets.h"
#include "Sounds.h"
#include "TestSignals.h"

using namespace mr;

namespace {
constexpr int kRate = 48000;

/** A clap: a 30 ms noise burst with a fast decay. */
std::vector<float> clap(uint32_t seed) {
    std::mt19937 rng(seed);
    std::normal_distribution<float> d(0.f, 0.4f);
    std::vector<float> c(1440);
    for (size_t i = 0; i < c.size(); ++i) c[i] = d(rng) * std::exp(-static_cast<float>(i) / 300.f);
    return c;
}
}  // namespace

TEST(Calibration, FiveChirpsGiveTheRoundTrip) {
    const auto chirp = calibrationChirp(kRate);
    const int64_t recStart = 5000;  // recording starts at output frame 5000
    const double latencyMs = 87.3;
    const auto latency = static_cast<size_t>(latencyMs * kRate / 1000.0);
    std::vector<int64_t> emits;
    std::vector<float> rec(kRate * 6, 0.f);
    for (int k = 0; k < 5; ++k) {
        const int64_t emit = 20000 + k * kRate;  // one per second
        emits.push_back(emit);
        test::mixInto(rec, chirp, static_cast<size_t>(emit - recStart) + latency, 0.1f);
        test::mixInto(rec, chirp, static_cast<size_t>(emit - recStart) + latency + 900, 0.04f);  // echo
    }
    test::addNoise(rec, 0.01f, 11);
    const LatencyResult r = analyzeChirps(rec.data(), rec.size(), recStart, emits.data(), emits.size(), chirp.data(),
                                          chirp.size(), kRate);
    EXPECT_EQ(5, r.found);
    EXPECT_NEAR(latencyMs, r.latencyMs, 1.0);
    EXPECT_GT(r.confidence, 0.8);
}

TEST(Calibration, OneMissedChirpLowersConfidenceButNotTheResult) {
    const auto chirp = calibrationChirp(kRate);
    const size_t latency = 2400;  // 50 ms
    std::vector<int64_t> emits;
    std::vector<float> rec(kRate * 6, 0.f);
    for (int k = 0; k < 5; ++k) {
        const int64_t emit = 1000 + k * kRate;
        emits.push_back(emit);
        if (k != 2) test::mixInto(rec, chirp, static_cast<size_t>(emit) + latency, 0.2f);  // chirp 3 drowned out
    }
    test::addNoise(rec, 0.01f, 5);
    // A loud bang instead of chirp 3.
    test::mixInto(rec, clap(1), static_cast<size_t>(emits[2]) + 10000, 1.0f);
    const LatencyResult r = analyzeChirps(rec.data(), rec.size(), 0, emits.data(), emits.size(), chirp.data(),
                                          chirp.size(), kRate);
    EXPECT_NEAR(50.0, r.latencyMs, 1.0);
    EXPECT_LE(r.confidence, 0.81);
    EXPECT_GE(r.confidence, 0.5);
}

TEST(Calibration, SilenceGivesNoResult) {
    const auto chirp = calibrationChirp(kRate);
    std::vector<float> rec(kRate * 6, 0.f);
    test::addNoise(rec, 0.001f, 2);
    const int64_t emits[5] = {0, kRate, 2 * kRate, 3 * kRate, 4 * kRate};
    const LatencyResult r = analyzeChirps(rec.data(), rec.size(), 0, emits, 5, chirp.data(), chirp.size(), kRate);
    EXPECT_LT(r.latencyMs, 0.0);
    EXPECT_EQ(0.0, r.confidence);
}

TEST(Onsets, FindsClapsAtTheirStart) {
    std::vector<float> rec(kRate * 3, 0.f);
    test::addNoise(rec, 0.003f, 9);
    const size_t at[3] = {10000, 40000, 100000};
    for (size_t i = 0; i < 3; ++i) test::mixInto(rec, clap(static_cast<uint32_t>(i)), at[i], 1.0f);
    const auto onsets = detectOnsets(rec.data(), rec.size(), kRate);
    ASSERT_EQ(3u, onsets.size());
    for (size_t i = 0; i < 3; ++i) EXPECT_NEAR(static_cast<double>(at[i]), static_cast<double>(onsets[i].sample), 48.0);
}

TEST(Calibration, SixteenClapsGiveTheMedianOffset) {
    const int64_t interval = kRate * 3 / 4;  // 80 BPM
    std::vector<int64_t> clicks;
    std::vector<float> rec(static_cast<size_t>(interval * 18), 0.f);
    test::addNoise(rec, 0.003f, 4);
    std::mt19937 rng(42);
    std::normal_distribution<double> jitterMs(0.0, 12.0);  // human timing
    const double offsetMs = 140.0;
    for (int k = 0; k < 16; ++k) {
        const int64_t click = 3000 + k * interval;
        clicks.push_back(click);
        double ms = offsetMs + jitterMs(rng);
        if (k == 5) ms = offsetMs + 200.0;  // one clearly late clap
        if (k == 9) continue;               // one missed clap
        test::mixInto(rec, clap(static_cast<uint32_t>(k)), static_cast<size_t>(click + std::llround(ms * kRate / 1000.0)), 0.8f);
    }
    const LatencyResult r = analyzeTaps(rec.data(), rec.size(), 0, clicks.data(), clicks.size(), kRate);
    EXPECT_EQ(15, r.found);
    EXPECT_NEAR(offsetMs, r.latencyMs, 8.0);
    EXPECT_GT(r.confidence, 0.5);
}

TEST(Calibration, MedianOfOddAndEven) {
    std::vector<double> a{3, 1, 2};
    EXPECT_DOUBLE_EQ(2.0, median(a));
    std::vector<double> b{4, 1, 3, 2};
    EXPECT_DOUBLE_EQ(2.5, median(b));
    std::vector<double> c;
    EXPECT_DOUBLE_EQ(0.0, median(c));
}
