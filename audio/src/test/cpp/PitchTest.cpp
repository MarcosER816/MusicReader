#include <gtest/gtest.h>

#include <cmath>
#include <random>
#include <vector>

#include "Fft.h"
#include "PitchDetector.h"
#include "PitchSmoother.h"
#include "PitchTracker.h"

using namespace mr;

namespace {
constexpr int kRate = 48000;
constexpr double kPi = 3.14159265358979323846;

double cents(double hz, double ref) { return 1200.0 * std::log2(hz / ref); }

/** Sum of harmonics with amplitudes [amps] (amps[0] = fundamental). */
std::vector<float> tone(double hz, const std::vector<double> &amps, size_t n, double phase0 = 0.3) {
    std::vector<float> x(n);
    for (size_t i = 0; i < n; ++i) {
        double v = 0.0;
        for (size_t h = 0; h < amps.size(); ++h)
            v += amps[h] * std::sin(2.0 * kPi * hz * static_cast<double>(h + 1) * static_cast<double>(i) / kRate + phase0 * (h + 1));
        x[i] = static_cast<float>(v);
    }
    return x;
}

const std::vector<double> kPure{0.5};
/** A voice-like / guitar-like spectrum: falling harmonics. */
const std::vector<double> kHarmonic{0.4, 0.3, 0.2, 0.12, 0.08, 0.05};

const double kFrequencies[] = {70.0, 80.0, 82.41, 98.0, 110.0, 146.83, 196.0, 246.94, 329.63, 440.0,
                               523.25, 659.26, 880.0, 1000.0, 1100.0};

void expectWithinCents(PitchDetector &d, double hz, const std::vector<double> &amps, double maxCents, const char *what) {
    const auto x = tone(hz, amps, PitchTracker::kWindow);
    const PitchEstimate e = d.detect(x.data());
    ASSERT_GT(e.hz, 0.0) << what << " " << hz;
    EXPECT_NEAR(0.0, cents(e.hz, hz), maxCents) << what << " " << hz << " Hz read as " << e.hz;
    EXPECT_GE(e.confidence, d.voicedConfidence()) << what << " " << hz;
}

}  // namespace

TEST(Fft, AutocorrelationMatchesTheDirectSum) {
    std::mt19937 rng(1);
    std::uniform_real_distribution<float> u(-1.f, 1.f);
    std::vector<float> x(256);
    for (float &v : x) v = u(rng);
    Autocorrelation acf(256);
    std::vector<float> r(256);
    acf.compute(x.data(), r.data());
    for (size_t tau : {0u, 1u, 7u, 100u, 255u}) {
        double direct = 0.0;
        for (size_t j = 0; j + tau < 256; ++j) direct += static_cast<double>(x[j]) * x[j + tau];
        EXPECT_NEAR(direct, r[tau], 1e-3 * (1.0 + std::fabs(direct))) << tau;
    }
}

TEST(Yin, PureAndHarmonicTonesWithinFiveCents) {
    YinDetector yin(kRate);
    for (double hz : kFrequencies) {
        expectWithinCents(yin, hz, kPure, 5.0, "YIN pure");
        expectWithinCents(yin, hz, kHarmonic, 5.0, "YIN harmonic");
    }
}

TEST(Mpm, PureAndHarmonicTonesWithinFiveCents) {
    MpmDetector mpm(kRate);
    for (double hz : kFrequencies) {
        expectWithinCents(mpm, hz, kPure, 5.0, "MPM pure");
        expectWithinCents(mpm, hz, kHarmonic, 5.0, "MPM harmonic");
    }
}

TEST(Pitch, StrongSecondHarmonicDoesNotJumpAnOctave) {
    YinDetector yin(kRate);
    MpmDetector mpm(kRate);
    // The 2nd harmonic three times as loud as the fundamental (9.5 dB).
    const std::vector<double> amps{0.15, 0.45, 0.1};
    for (double hz : {82.41, 110.0, 196.0, 261.63, 440.0, 659.26}) {
        expectWithinCents(yin, hz, amps, 5.0, "YIN strong 2nd");
        expectWithinCents(mpm, hz, amps, 5.0, "MPM strong 2nd");
    }
}

TEST(Pitch, SilenceAndNoiseGiveLowConfidence) {
    std::vector<float> silence(PitchTracker::kWindow, 0.f);
    YinDetector yin(kRate);
    MpmDetector mpm(kRate);
    EXPECT_EQ(0.0, yin.detect(silence.data()).hz);
    EXPECT_EQ(0.0, mpm.detect(silence.data()).hz);
    std::mt19937 rng(7);
    std::normal_distribution<float> g(0.f, 0.2f);
    int confident = 0;
    for (int k = 0; k < 50; ++k) {
        std::vector<float> noise(PitchTracker::kWindow);
        for (float &v : noise) v = g(rng);
        if (yin.detect(noise.data()).confidence >= yin.voicedConfidence()) ++confident;
        if (mpm.detect(noise.data()).confidence >= mpm.voicedConfidence()) ++confident;
    }
    EXPECT_EQ(0, confident);
}

namespace {

/** Runs a whole signal through a tracker in 192-sample blocks (like audio callbacks). */
std::vector<PitchFrame> track(PitchTracker &t, const std::vector<float> &x, int64_t firstFrame = 0) {
    std::vector<PitchFrame> frames;
    PitchFrame buf[8];
    for (size_t at = 0; at < x.size(); at += 192) {
        const size_t n = std::min<size_t>(192, x.size() - at);
        const size_t got = t.process(x.data() + at, n, firstFrame + static_cast<int64_t>(at), buf, 8);
        frames.insert(frames.end(), buf, buf + got);
    }
    return frames;
}

}  // namespace

TEST(Tracker, OneFrameEvery10Ms) {
    PitchTracker t(kRate);
    const auto x = tone(220.0, kHarmonic, kRate);  // 1 s
    const auto frames = track(t, x, 1000);
    // First frame when the window is full, then one per hop.
    EXPECT_EQ(1 + (kRate - PitchTracker::kWindow) / PitchTracker::kHop, frames.size());
    ASSERT_GE(frames.size(), 2u);
    EXPECT_EQ(static_cast<int64_t>(PitchTracker::kHop), frames[1].frame - frames[0].frame);
    // Window center minus the median's two-frame delay.
    EXPECT_EQ(1000 + 2048 - 1024 - 960, frames[0].frame);
}

TEST(Tracker, HarmonicTonesFromEightyToThousandHz) {
    for (PitchMethod m : {PitchMethod::kYin, PitchMethod::kMpm}) {
        for (double hz : {80.0, 110.0, 220.0, 440.0, 880.0, 1000.0}) {
            PitchTracker t(kRate);
            t.setMethod(m);
            const auto frames = track(t, tone(hz, kHarmonic, kRate / 2));
            int voiced = 0;
            for (size_t i = 5; i < frames.size(); ++i) {
                if (!frames[i].voiced) continue;
                ++voiced;
                EXPECT_NEAR(0.0, cents(frames[i].hz, hz), 5.0) << static_cast<int>(m) << " " << hz;
                EXPECT_NEAR(PitchTracker::hzToMidi(hz), frames[i].midi, 0.05);
            }
            EXPECT_EQ(static_cast<int>(frames.size()) - 5, voiced) << static_cast<int>(m) << " " << hz;
        }
    }
}

TEST(Tracker, VibratoIsFollowed) {
    // 220 Hz with ±50 cents vibrato at 5.5 Hz (a sung vibrato), 2 s.
    const size_t n = kRate * 2;
    std::vector<float> x(n);
    double phase = 0.0;
    auto freqAt = [](double t) { return 220.0 * std::pow(2.0, 0.5 / 12.0 * std::sin(2.0 * kPi * 5.5 * t)); };
    for (size_t i = 0; i < n; ++i) {
        const double f = freqAt(static_cast<double>(i) / kRate);
        phase += 2.0 * kPi * f / kRate;
        x[i] = static_cast<float>(0.4 * std::sin(phase) + 0.2 * std::sin(2 * phase) + 0.1 * std::sin(3 * phase));
    }
    for (PitchMethod m : {PitchMethod::kYin, PitchMethod::kMpm}) {
        PitchTracker t(kRate);
        t.setMethod(m);
        const auto frames = track(t, x);
        double worst = 0.0;
        int voiced = 0;
        for (size_t i = 5; i < frames.size(); ++i) {
            if (!frames[i].voiced) continue;
            ++voiced;
            const double truth = freqAt(static_cast<double>(frames[i].frame) / kRate);
            worst = std::max(worst, std::fabs(cents(frames[i].hz, truth)));
        }
        EXPECT_EQ(static_cast<int>(frames.size()) - 5, voiced);
        EXPECT_LT(worst, 15.0) << static_cast<int>(m);  // the vibrato itself spans 100 cents
    }
}

TEST(Tracker, NoiseIsUnvoiced) {
    std::mt19937 rng(3);
    std::normal_distribution<float> g(0.f, 0.2f);  // loud: well above the gate
    std::vector<float> x(kRate * 2);
    for (float &v : x) v = g(rng);
    for (PitchMethod m : {PitchMethod::kYin, PitchMethod::kMpm}) {
        PitchTracker t(kRate);
        t.setMethod(m);
        int voiced = 0;
        for (const PitchFrame &f : track(t, x)) voiced += f.voiced ? 1 : 0;
        EXPECT_EQ(0, voiced) << static_cast<int>(m);
    }
}

TEST(Tracker, QuietSoundsBelowTheGateAreUnvoiced) {
    PitchTracker t(kRate);
    t.setGateDb(-40.0);
    const auto quiet = tone(220.0, {0.003}, kRate / 2);  // about −53 dBFS
    for (const PitchFrame &f : track(t, quiet)) EXPECT_FALSE(f.voiced);
    PitchTracker t2(kRate);
    t2.setGateDb(-60.0);
    int voiced = 0;
    for (const PitchFrame &f : track(t2, quiet)) voiced += f.voiced ? 1 : 0;
    EXPECT_GT(voiced, 30);
}

TEST(Tracker, StrongSecondHarmonicStaysInItsOctave) {
    const std::vector<double> amps{0.12, 0.45, 0.1};
    for (PitchMethod m : {PitchMethod::kYin, PitchMethod::kMpm}) {
        PitchTracker t(kRate);
        t.setMethod(m);
        for (const PitchFrame &f : track(t, tone(146.83, amps, kRate))) {
            if (f.voiced) {
                EXPECT_NEAR(0.0, cents(f.hz, 146.83), 5.0) << static_cast<int>(m);
            }
        }
    }
}

TEST(Tracker, PluckedLowEString) {
    // A plucked low E (82.41 Hz): ten harmonics decaying at different rates, like a guitar.
    const size_t n = kRate * 2;
    std::vector<float> x(n);
    for (size_t i = 0; i < n; ++i) {
        const double t = static_cast<double>(i) / kRate;
        double v = 0.0;
        for (int h = 1; h <= 10; ++h) v += (0.5 / h) * std::exp(-t * (1.0 + 0.6 * h)) * std::sin(2.0 * kPi * 82.41 * h * t + h);
        x[i] = static_cast<float>(v);
    }
    for (PitchMethod m : {PitchMethod::kYin, PitchMethod::kMpm}) {
        PitchTracker t(kRate);
        t.setMethod(m);
        const auto frames = track(t, x);
        int voiced = 0;
        for (const PitchFrame &f : frames) {
            if (f.frame > kRate) break;  // the first second
            if (!f.voiced) continue;
            ++voiced;
            EXPECT_NEAR(0.0, cents(f.hz, 82.41), 5.0) << static_cast<int>(m);
        }
        EXPECT_GT(voiced, 80) << static_cast<int>(m);
    }
}

TEST(Tracker, AGapInTheInputRestartsTheWindow) {
    PitchTracker t(kRate);
    const auto x = tone(220.0, kHarmonic, 4000);
    PitchFrame buf[16];
    EXPECT_EQ(5u, t.process(x.data(), 4000, 0, buf, 16));  // at 2048, 2528, 3008, 3488, 3968
    // Next block does not follow on: nothing until a new full window.
    EXPECT_EQ(0u, t.process(x.data(), 2000, 100000, buf, 16));
    EXPECT_EQ(1u, t.process(x.data(), 48, 102000, buf, 16));
}

TEST(Smoother, MedianOfFiveAndVoicedWhenThreeOfFive) {
    PitchSmoother s;
    EXPECT_FALSE(s.push(true, 60.0).voiced);
    EXPECT_FALSE(s.push(true, 60.2).voiced);
    auto o = s.push(true, 59.9);
    EXPECT_TRUE(o.voiced);
    EXPECT_DOUBLE_EQ(60.0, o.midi);
    s.push(true, 70.0);  // a single wild frame
    o = s.push(true, 60.1);
    EXPECT_DOUBLE_EQ(60.1, o.midi);
    // One dropout is bridged, three are not.
    EXPECT_TRUE(s.push(false, 0.0).voiced);
    EXPECT_TRUE(s.push(false, 0.0).voiced);
    EXPECT_FALSE(s.push(false, 0.0).voiced);
}

TEST(Smoother, OctaveJumpsAreFoldedUnlessTheyLast) {
    PitchSmoother s;
    for (int i = 0; i < 5; ++i) s.push(true, 57.0);
    // Three frames an octave up are folded back...
    for (int i = 0; i < PitchSmoother::kMaxFolds; ++i) {
        const auto o = s.push(true, 69.02);
        EXPECT_TRUE(o.folded);
        EXPECT_NEAR(57.0, o.midi, 0.05);
    }
    // ...after that it is a real leap and the output follows it.
    PitchSmoother::Output o;
    for (int i = 0; i < 5; ++i) o = s.push(true, 69.0);
    EXPECT_NEAR(69.0, o.midi, 0.05);
    // A fifth is not an octave: never folded.
    PitchSmoother s2;
    for (int i = 0; i < 5; ++i) s2.push(true, 57.0);
    EXPECT_FALSE(s2.push(true, 64.0).folded);
}

TEST(Tracker, MidiConversions) {
    EXPECT_DOUBLE_EQ(69.0, PitchTracker::hzToMidi(440.0));
    EXPECT_NEAR(40.0, PitchTracker::hzToMidi(82.4069), 1e-3);
    EXPECT_NEAR(261.6256, PitchTracker::midiToHz(60.0), 1e-3);
}

namespace {

/**
 * A realistic plucked string as a phone hears it: inharmonic partials, the fundamental fading
 * first, beating between the string's two vibration planes, room echoes and noise at −50 dBFS.
 */
std::vector<float> realisticPluck(double f0, double seconds) {
    const size_t n = static_cast<size_t>(seconds * kRate);
    std::vector<float> x(n, 0.f);
    for (size_t i = 0; i < n; ++i) {
        const double t = static_cast<double>(i) / kRate;
        double v = 0.0;
        for (int h = 1; h <= 15; ++h) {
            const double tau = h == 1 ? 0.5 : 1.6 / std::sqrt(h);
            const double fh = f0 * h * std::sqrt(1 + 0.0004 * h * h);
            const double beat = 1.0 + 0.6 * std::sin(2 * kPi * (0.7 + 0.3 * h) * t);
            v += (0.25 / h) * beat * std::exp(-t / tau) * std::sin(2 * kPi * fh * t + h);
        }
        x[i] = static_cast<float>(v);
    }
    std::vector<float> y(x);
    for (size_t d : {331u, 797u, 1601u, 2903u})
        for (size_t i = d; i < n; ++i) y[i] += 0.25f * x[i - d];
    std::mt19937 rng(1);
    std::normal_distribution<float> g(0.f, 0.003f);
    for (float &v : y) v += g(rng);
    return y;
}

}  // namespace

// Mark, Stage 8 round 1: a guitar string was recognized "only for a short moment".
TEST(Tracker, AFadingStringKeepsItsNote) {
    PitchTracker t(kRate);
    t.setMethod(PitchMethod::kMpm);
    t.setGateDb(-45.0);
    const auto frames = track(t, realisticPluck(82.41, 5.0));
    double lastRight = 0.0;
    int wrong = 0;
    for (const PitchFrame &f : frames) {
        if (!f.voiced) continue;
        if (std::fabs(cents(f.hz, 82.41)) < 30.0) lastRight = f.frame / static_cast<double>(kRate);
        else ++wrong;
    }
    EXPECT_GE(lastRight, 3.5);  // until the string is only a few dB above the room noise
    EXPECT_EQ(0, wrong);        // and never on another octave in the tail
}

TEST(Tracker, AHeldNoteSurvivesWhatCouldNotStartANote) {
    // 220 Hz: clean for 0.5 s, then with noise that drops the confidence under the "new note"
    // threshold but above the "held note" one.
    const size_t n = kRate * 2;
    auto x = tone(220.0, kHarmonic, n);
    std::mt19937 rng(9);
    std::normal_distribution<float> g(0.f, 0.25f);  // MPM clarity about 0.72: between 0.6 and 0.8
    for (size_t i = kRate / 2; i < n; ++i) x[i] += g(rng);
    std::vector<float> noisyOnly(x.begin() + kRate / 2, x.end());

    auto voicedShare = [](const std::vector<PitchFrame> &frames, double fromSeconds) {
        int v = 0, all = 0;
        for (const PitchFrame &f : frames) {
            if (f.frame / static_cast<double>(kRate) < fromSeconds) continue;
            ++all;
            v += f.voiced ? 1 : 0;
        }
        return all ? static_cast<double>(v) / all : 0.0;
    };
    PitchTracker held(kRate);
    held.setMethod(PitchMethod::kMpm);
    const double heldShare = voicedShare(track(held, x), 0.6);
    PitchTracker fresh(kRate);
    fresh.setMethod(PitchMethod::kMpm);
    const double freshShare = voicedShare(track(fresh, noisyOnly), 0.0);
    EXPECT_GT(heldShare, 0.9) << "the held note keeps sounding";
    EXPECT_LT(freshShare, 0.5) << "the same noisy sound alone does not start a note (" << freshShare << ")";
}

TEST(Detector, EstimateNearFindsTheHeldPeriod) {
    YinDetector yin(kRate);
    MpmDetector mpm(kRate);
    const auto x = tone(196.0, kHarmonic, PitchTracker::kWindow);
    yin.detect(x.data());
    mpm.detect(x.data());
    EXPECT_NEAR(0.0, cents(yin.estimateNear(198.0).hz, 196.0), 5.0);
    EXPECT_NEAR(0.0, cents(mpm.estimateNear(194.0).hz, 196.0), 5.0);
    EXPECT_EQ(0.0, mpm.estimateNear(150.0).hz);  // nothing periodic there
}

// Mark, Stage 8 round 2: "a sudden drop and a sudden spike" while singing. A legato leap up an
// octave, sung softer than the note before, must not be folded back down.
TEST(Tracker, ASofterLegatoOctaveLeapIsFollowed) {
    for (PitchMethod m : {PitchMethod::kYin, PitchMethod::kMpm}) {
        const size_t n = kRate * 2;
        std::vector<float> x(n);
        double phase = 0.0;
        for (size_t i = 0; i < n; ++i) {
            const bool second = i >= n / 2;
            const double f = second ? 440.0 : 220.0;
            const double a = second ? 0.4 * 0.3 : 0.4;  // 10 dB softer after the leap, no gap
            phase += 2 * kPi * f / kRate;
            x[i] = static_cast<float>(a * (std::sin(phase) + 0.5 * std::sin(2 * phase) + 0.25 * std::sin(3 * phase)));
        }
        PitchTracker t(kRate);
        t.setMethod(m);
        double reached = -1.0;
        int wrongAfter = 0;
        for (const PitchFrame &f : track(t, x)) {
            const double ts = f.frame / static_cast<double>(kRate);
            if (ts < 1.0 || !f.voiced) continue;
            if (std::fabs(cents(f.hz, 440.0)) < 30.0) {
                if (reached < 0) reached = ts;
            } else if (reached >= 0) {
                ++wrongAfter;
            }
        }
        EXPECT_GE(reached, 1.0) << static_cast<int>(m);
        EXPECT_LT(reached, 1.12) << static_cast<int>(m) << " reached 440 Hz at " << reached;
        EXPECT_EQ(0, wrongAfter) << static_cast<int>(m);
    }
}

// The Unprocessed microphone has no built-in high-pass: DC offset and rumble reach the detector.
TEST(Tracker, DcOffsetAndRumbleDoNotDisturb) {
    for (PitchMethod m : {PitchMethod::kYin, PitchMethod::kMpm}) {
        for (double hz : {82.41, 196.0, 440.0}) {
            auto x = tone(hz, kHarmonic, kRate);
            for (size_t i = 0; i < x.size(); ++i)
                x[i] += static_cast<float>(0.15 + 0.3 * std::sin(2 * kPi * 12.0 * i / kRate));  // DC + 12 Hz rumble
            PitchTracker t(kRate);
            t.setMethod(m);
            const auto frames = track(t, x);
            int voiced = 0;
            for (size_t i = 10; i < frames.size(); ++i) {
                if (!frames[i].voiced) continue;
                ++voiced;
                EXPECT_NEAR(0.0, cents(frames[i].hz, hz), 5.0) << static_cast<int>(m) << " " << hz;
            }
            EXPECT_GT(voiced, static_cast<int>(frames.size()) - 15) << static_cast<int>(m) << " " << hz;
        }
    }
}

// A sung phrase: steps, an octave leap up and back, vibrato, crescendo and decrescendo, breath
// noise, short gaps between some notes, DC offset and rumble. No spikes or drops: every voiced
// frame is within a semitone of the sung note, except in the first 60 ms after a note change.
TEST(Tracker, ASungPhraseHasNoSpikesOrDrops) {
    const double notes[] = {57, 60, 64, 69, 67, 64, 60, 57, 69, 57};  // MIDI
    const double noteSeconds = 0.4;
    const size_t perNote = static_cast<size_t>(noteSeconds * kRate);
    const size_t n = perNote * std::size(notes);
    std::vector<float> x(n);
    std::mt19937 rng(21);
    std::normal_distribution<float> breath(0.f, 0.01f);
    double phase = 0.0;
    for (size_t i = 0; i < n; ++i) {
        const size_t k = i / perNote;
        const double t = static_cast<double>(i) / kRate;
        const double local = static_cast<double>(i % perNote) / kRate;
        const double midi = notes[k] + 0.3 * std::sin(2 * kPi * 5.5 * t);
        phase += 2 * kPi * PitchTracker::midiToHz(midi) / kRate;
        double a = 0.11 + 0.07 * std::sin(kPi * t / 2.0);       // slow crescendo/decrescendo (12 dB)
        if (k % 3 == 2 && local < 0.03) a = 0.0;                 // a short gap (a breath)
        x[i] = static_cast<float>(a * (std::sin(phase) + 0.6 * std::sin(2 * phase) + 0.3 * std::sin(3 * phase))) +
               breath(rng) + 0.05f + 0.05f * static_cast<float>(std::sin(2 * kPi * 15.0 * t));
    }
    for (PitchMethod m : {PitchMethod::kYin, PitchMethod::kMpm}) {
        PitchTracker t(kRate);
        t.setMethod(m);
        int bad = 0, voiced = 0;
        for (const PitchFrame &f : track(t, x)) {
            if (!f.voiced || f.frame < 0) continue;
            const size_t k = static_cast<size_t>(f.frame) / perNote;
            if (k >= std::size(notes)) continue;
            ++voiced;
            const double sinceChange = static_cast<double>(static_cast<size_t>(f.frame) % perNote) / kRate;
            if (sinceChange < 0.06) continue;
            if (std::fabs(f.midi - notes[k]) > 1.0) {
                ++bad;
                ADD_FAILURE() << (m == PitchMethod::kYin ? "YIN" : "MPM") << " at " << f.frame / static_cast<double>(kRate)
                              << " s: " << f.midi << " instead of " << notes[k];
            }
        }
        EXPECT_EQ(0, bad);
        EXPECT_GT(voiced, 300);
    }
}
