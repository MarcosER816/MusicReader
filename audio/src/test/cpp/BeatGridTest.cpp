#include <gtest/gtest.h>

#include "BeatGrid.h"

using namespace mr;

namespace {
BeatGrid grid(int64_t anchor, double bpm, int beatsPerBar) {
    BeatGrid g;
    g.anchorFrame = anchor;
    g.framesPerBeat = framesPerBeat(48000.0, bpm);
    g.beatsPerBar = beatsPerBar;
    return g;
}
}  // namespace

TEST(BeatGrid, BeatFramesAreExactAndDoNotDrift) {
    const BeatGrid g = grid(1000, 120.0, 4);  // 24000 frames per beat
    EXPECT_EQ(1000, beatFrame(g, 0));
    EXPECT_EQ(25000, beatFrame(g, 1));
    EXPECT_EQ(1000 + 24000LL * 100000, beatFrame(g, 100000));
    // 7 / 7 BPM does not divide 48000*60 evenly: each beat is rounded on its own, so the
    // error never exceeds half a sample, even after an hour.
    const BeatGrid odd = grid(0, 97.0, 4);
    const double fpb = 48000.0 * 60.0 / 97.0;
    for (int64_t k : {1LL, 2LL, 3LL, 1000LL, 5820LL}) {
        EXPECT_LE(std::fabs(static_cast<double>(beatFrame(odd, k)) - k * fpb), 0.5) << k;
    }
}

TEST(BeatGrid, FirstBeatAtOrAfter) {
    const BeatGrid g = grid(1000, 120.0, 4);
    EXPECT_EQ(0, firstBeatAtOrAfter(g, 0));
    EXPECT_EQ(0, firstBeatAtOrAfter(g, 1000));
    EXPECT_EQ(1, firstBeatAtOrAfter(g, 1001));
    EXPECT_EQ(1, firstBeatAtOrAfter(g, 25000));
    EXPECT_EQ(2, firstBeatAtOrAfter(g, 25001));
    EXPECT_DOUBLE_EQ(1.5, beatAt(g, 37000.0));
}

TEST(BeatGrid, AccentOnBeatOneOfEachBar) {
    const BeatGrid g = grid(0, 120.0, 3);
    EXPECT_TRUE(isAccent(g, 0));
    EXPECT_FALSE(isAccent(g, 1));
    EXPECT_FALSE(isAccent(g, 2));
    EXPECT_TRUE(isAccent(g, 3));
    EXPECT_TRUE(isAccent(g, 6));
}

TEST(BeatGrid, TempoChangeTakesEffectOnTheNextBeat) {
    const BeatGrid g = grid(0, 120.0, 4);
    // Change requested at frame 30000 (between beat 1 at 24000 and beat 2 at 48000).
    const BeatGrid n = retempo(g, 30000, framesPerBeat(48000.0, 60.0), 4);
    EXPECT_EQ(2, n.anchorBeat);
    EXPECT_EQ(48000, n.anchorFrame);
    EXPECT_EQ(48000 + 48000, beatFrame(n, 3));
    EXPECT_EQ(0, n.barOriginBeat);       // same meter: bars continue
    EXPECT_FALSE(isAccent(n, 2));
    EXPECT_TRUE(isAccent(n, 4));
    // A new time signature starts a new bar on the switch beat.
    const BeatGrid m = retempo(g, 30000, g.framesPerBeat, 3);
    EXPECT_TRUE(isAccent(m, 2));
    EXPECT_TRUE(isAccent(m, 5));
}
