#include <gtest/gtest.h>

#include <vector>

#include "Correlation.h"
#include "Sounds.h"
#include "TestSignals.h"

using namespace mr;

TEST(Correlation, FindsADelayedChirpWithinOneMillisecond) {
    const int sr = 48000;
    const auto chirp = calibrationChirp(sr);
    for (size_t delay : {0u, 1u, 480u, 4321u, 9600u, 30000u}) {
        std::vector<float> rec(48000, 0.f);
        test::mixInto(rec, chirp, delay, 0.05f);         // quiet: speaker far from mic
        test::mixInto(rec, chirp, delay + 700, 0.02f);   // a wall echo 15 ms later
        test::addNoise(rec, 0.01f, static_cast<uint32_t>(delay));
        const DelayEstimate d = findDelay(rec.data(), rec.size(), chirp.data(), chirp.size(), 0, 40000);
        EXPECT_NEAR(static_cast<double>(delay), d.lag, 48.0) << delay;  // 1 ms = 48 samples
        EXPECT_NEAR(static_cast<double>(delay), d.lag, 1.0) << delay;   // in fact within a sample
        EXPECT_GT(d.peak, 0.3);
    }
}

TEST(Correlation, InvertedAndFilteredStillFound) {
    const int sr = 48000;
    const auto chirp = calibrationChirp(sr);
    std::vector<float> rec(24000, 0.f);
    test::mixInto(rec, chirp, 6000, -0.3f);
    test::lowPass(rec, 0.5f);  // dull speaker; shifts the peak by a fraction of a sample
    test::addNoise(rec, 0.02f, 7);
    const DelayEstimate d = findDelay(rec.data(), rec.size(), chirp.data(), chirp.size(), 0, 20000);
    EXPECT_NEAR(6000.0, d.lag, 48.0);
}

TEST(Correlation, NothingThereGivesALowPeak) {
    const auto chirp = calibrationChirp(48000);
    std::vector<float> rec(24000, 0.f);
    test::addNoise(rec, 0.05f, 3);
    const DelayEstimate d = findDelay(rec.data(), rec.size(), chirp.data(), chirp.size(), 0, 20000);
    EXPECT_LT(d.peak, 0.15);
}

TEST(Correlation, RejectsBadInput) {
    const float x[4] = {1, 2, 3, 4};
    EXPECT_LT(findDelay(x, 4, x, 8, 0, 10).lag, 0.0);   // reference longer than the signal
    EXPECT_LT(findDelay(x, 4, x, 2, 5, 10).lag, 0.0);   // minLag beyond the end
}
