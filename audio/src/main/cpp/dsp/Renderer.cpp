#include "Renderer.h"

#include <algorithm>
#include <cmath>

#include "Sounds.h"

namespace mr {

Renderer::Renderer(int sampleRate) : sampleRate_(sampleRate) {
    sounds_[kSoundNormalClick] = normalClick(sampleRate);
    sounds_[kSoundAccentClick] = accentClick(sampleRate);
    sounds_[kSoundChirp] = calibrationChirp(sampleRate);
}

void Renderer::setGrid(const BeatGrid &grid) {
    pending_ = grid;
    hasPending_ = true;
    stopFrame_ = INT64_MAX;
}

void Renderer::stopMetronome(int64_t frame) {
    stopFrame_ = frame;
    hasPending_ = false;
}

bool Renderer::addOneShot(int sound, int64_t frame) {
    if (sound < 0 || sound >= kSoundCount) return false;
    for (auto &s : oneShots_) {
        if (s.sound < 0) {
            s.sound = sound;
            s.frame = frame;
            return true;
        }
    }
    return false;
}

bool Renderer::addMelodyNote(const MelodyNote &note) {
    if (melodyCount_ >= kMaxMelody || note.end <= note.start) return false;
    melody_[melodyCount_++] = note;
    return true;
}

void Renderer::clearMelody() {
    melodyCount_ = 0;
    melodyIndex_ = 0;
}

void Renderer::clearOneShots() {
    for (auto &s : oneShots_) s.sound = -1;
}

void Renderer::render(float *mono, int64_t firstFrame, int32_t count) {
    // Metronome, sample by sample so every click starts on its exact frame.
    for (int32_t i = 0; i < count; ++i) {
        const int64_t f = firstFrame + i;
        if (hasPending_ && f >= pending_.anchorFrame) {
            grid_ = pending_;
            hasPending_ = false;
            running_ = true;
            nextBeat_ = firstBeatAtOrAfter(grid_, f);
            nextBeatFrame_ = beatFrame(grid_, nextBeat_);
        }
        if (running_ && f >= stopFrame_) {
            running_ = false;
            stopFrame_ = INT64_MAX;
        }
        if (running_) {
            if (f > nextBeatFrame_) {  // started late: skip beats already past, never play late
                nextBeat_ = firstBeatAtOrAfter(grid_, f);
                nextBeatFrame_ = beatFrame(grid_, nextBeat_);
            }
            if (f == nextBeatFrame_) {
                startVoice(isAccent(grid_, nextBeat_) ? kSoundAccentClick : kSoundNormalClick);
                ++nextBeat_;
                nextBeatFrame_ = beatFrame(grid_, nextBeat_);
            }
        }
        if (voiceSound_ >= 0) {
            const auto &snd = sounds_[static_cast<size_t>(voiceSound_)];
            mono[i] += snd[voicePos_];
            if (++voicePos_ >= snd.size()) voiceSound_ = -1;
        }
    }

    // The preview melody: one soft voice (a sine with a little 2nd harmonic), each note
    // starting on its exact frame, gliding in pitch from its start to its end note, with 5 ms
    // fades so notes do not click.
    if (melodyIndex_ < melodyCount_) {
        constexpr double kTwoPi = 6.283185307179586;
        const int64_t fade = sampleRate_ / 200;
        for (int32_t i = 0; i < count; ++i) {
            const int64_t f = firstFrame + i;
            while (melodyIndex_ < melodyCount_ && melody_[melodyIndex_].end <= f) ++melodyIndex_;
            if (melodyIndex_ >= melodyCount_) break;
            const MelodyNote &n = melody_[melodyIndex_];
            if (f < n.start) continue;
            const int64_t len = n.end - n.start;
            const double pos = static_cast<double>(f - n.start) / static_cast<double>(len);
            const double hz = n.startHz * std::pow(static_cast<double>(n.endHz) / n.startHz, pos);
            if (f == n.start) melodyPhase_ = 0.0;
            melodyPhase_ += kTwoPi * hz / sampleRate_;
            if (melodyPhase_ > kTwoPi) melodyPhase_ -= kTwoPi;
            const int64_t fromStart = f - n.start, toEnd = n.end - 1 - f;
            double env = 1.0;
            if (fromStart < fade) env = static_cast<double>(fromStart) / fade;
            if (toEnd < fade) env = std::min(env, static_cast<double>(toEnd) / fade);
            mono[i] += static_cast<float>(kMelodyGain * env * (std::sin(melodyPhase_) + 0.25 * std::sin(2.0 * melodyPhase_)));
        }
    }

    // One-shots, block by block.
    const int64_t end = firstFrame + count;
    for (auto &s : oneShots_) {
        if (s.sound < 0) continue;
        const auto &snd = sounds_[static_cast<size_t>(s.sound)];
        const int64_t soundEnd = s.frame + static_cast<int64_t>(snd.size());
        const int64_t from = std::max(firstFrame, s.frame);
        const int64_t to = std::min(end, soundEnd);
        for (int64_t f = from; f < to; ++f) mono[f - firstFrame] += snd[static_cast<size_t>(f - s.frame)];
        if (soundEnd <= end) s.sound = -1;
    }
}

}  // namespace mr
