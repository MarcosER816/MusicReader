#include "Renderer.h"

#include <algorithm>

#include "Sounds.h"

namespace mr {

Renderer::Renderer(int sampleRate) {
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
