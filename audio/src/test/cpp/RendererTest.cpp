#include <gtest/gtest.h>

#include <cmath>
#include <vector>

#include "BeatGrid.h"
#include "Renderer.h"

using namespace mr;

namespace {

/** Renders [total] frames in callback-sized blocks of [block] frames. */
std::vector<float> renderAll(Renderer &r, int64_t total, int block) {
    std::vector<float> out(static_cast<size_t>(total), 0.f);
    for (int64_t f = 0; f < total; f += block) {
        const int n = static_cast<int>(std::min<int64_t>(block, total - f));
        r.render(out.data() + f, f, n);
    }
    return out;
}

/** Start frames of sounds: a nonzero sample after at least [gap] silent samples. */
std::vector<int64_t> soundStarts(const std::vector<float> &x, size_t gap = 1000) {
    std::vector<int64_t> starts;
    size_t silent = gap;
    for (size_t i = 0; i < x.size(); ++i) {
        if (x[i] != 0.f) {
            if (silent >= gap) starts.push_back(static_cast<int64_t>(i));
            silent = 0;
        } else {
            ++silent;
        }
    }
    return starts;
}

BeatGrid grid(int64_t anchor, double bpm, int beatsPerBar) {
    BeatGrid g;
    g.anchorFrame = anchor;
    g.framesPerBeat = framesPerBeat(48000.0, bpm);
    g.beatsPerBar = beatsPerBar;
    return g;
}

}  // namespace

TEST(Renderer, ClicksStartOnTheExactSample) {
    // The click sound starts with a zero sample (attack from 0), so the first audible sample is
    // the beat frame + 1; the scheduled frame is where the sound buffer starts.
    for (int block : {1, 96, 192, 241, 4096}) {
        Renderer r(48000);
        r.setGrid(grid(777, 97.0, 4));
        const auto out = renderAll(r, 48000 * 6, block);
        const auto starts = soundStarts(out);
        ASSERT_GE(starts.size(), 9u) << block;
        const BeatGrid g = grid(777, 97.0, 4);
        for (size_t k = 0; k < starts.size(); ++k) {
            EXPECT_EQ(beatFrame(g, static_cast<int64_t>(k)) + 1, starts[k]) << "block " << block << " beat " << k;
        }
    }
}

TEST(Renderer, AccentIsTheBarsFirstBeat) {
    Renderer r(48000);
    r.setGrid(grid(0, 120.0, 3));
    const auto out = renderAll(r, 48000 * 3, 192);
    const auto &accent = r.sound(kSoundAccentClick);
    const auto &normal = r.sound(kSoundNormalClick);
    auto same = [&](int64_t at, const std::vector<float> &s) {
        for (size_t i = 0; i < 200; ++i)
            if (out[static_cast<size_t>(at) + i] != s[i]) return false;
        return true;
    };
    EXPECT_TRUE(same(0, accent));
    EXPECT_TRUE(same(24000, normal));
    EXPECT_TRUE(same(48000, normal));
    EXPECT_TRUE(same(72000, accent));
}

TEST(Renderer, TempoChangeKeepsEarlierBeats) {
    Renderer r(48000);
    const BeatGrid g = grid(0, 120.0, 4);
    r.setGrid(g);
    std::vector<float> out(48000 * 4, 0.f);
    // First 30000 frames at 120 BPM, then switch to 60 BPM from the next beat.
    r.render(out.data(), 0, 30000);
    r.setGrid(retempo(g, 30000, framesPerBeat(48000.0, 60.0), 4));
    r.render(out.data() + 30000, 30000, static_cast<int32_t>(out.size()) - 30000);
    const auto starts = soundStarts(out);
    ASSERT_EQ(5u, starts.size());
    EXPECT_EQ(1, starts[0]);
    EXPECT_EQ(24001, starts[1]);
    EXPECT_EQ(48001, starts[2]);   // first beat of the new tempo
    EXPECT_EQ(96001, starts[3]);   // one second later
    EXPECT_EQ(144001, starts[4]);
}

TEST(Renderer, StopSilencesFollowingBeats) {
    Renderer r(48000);
    r.setGrid(grid(0, 120.0, 4));
    std::vector<float> out(48000 * 3, 0.f);
    r.render(out.data(), 0, 30000);
    r.stopMetronome(30000);
    r.render(out.data() + 30000, 30000, static_cast<int32_t>(out.size()) - 30000);
    EXPECT_EQ(2u, soundStarts(out).size());
    EXPECT_FALSE(r.metronomeRunning());
}

TEST(Renderer, LateStartSkipsBeatsInsteadOfPlayingThemLate) {
    Renderer r(48000);
    r.setGrid(grid(0, 120.0, 4));
    std::vector<float> out(48000, 0.f);
    // The first block rendered is already past beat 0 (frame 0): beat 1 at 24000 is next.
    r.render(out.data(), 100, static_cast<int32_t>(out.size()) - 100);
    const auto starts = soundStarts(out);
    ASSERT_EQ(1u, starts.size());
    EXPECT_EQ(24001 - 100, starts[0]);
}

TEST(Renderer, OneShotsPlayAtTheirFrameAcrossBlocks) {
    Renderer r(48000);
    ASSERT_TRUE(r.addOneShot(kSoundChirp, 1000));
    ASSERT_TRUE(r.addOneShot(kSoundChirp, 50000));
    const auto out = renderAll(r, 100000, 192);
    const auto &chirp = r.sound(kSoundChirp);
    for (size_t i = 0; i < chirp.size(); ++i) {
        ASSERT_EQ(chirp[i], out[1000 + i]) << i;
        ASSERT_EQ(chirp[i], out[50000 + i]) << i;
    }
    EXPECT_EQ(0.f, out[999]);
    EXPECT_EQ(0.f, out[1000 + chirp.size()]);
}
