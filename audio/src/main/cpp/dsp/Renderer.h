// Renders the metronome and scheduled one-shot sounds into a mono block, sample-exactly at
// the frames they are scheduled for. Used inside the audio callback: no allocation, no locks.
#pragma once

#include <array>
#include <cstdint>
#include <vector>

#include "BeatGrid.h"

namespace mr {

enum Sound : int { kSoundNormalClick = 0, kSoundAccentClick = 1, kSoundChirp = 2, kSoundCount = 3 };

class Renderer {
public:
    /** Builds the sounds; call outside the audio callback. */
    explicit Renderer(int sampleRate);

    /** The metronome follows [grid] from grid.anchorFrame on (earlier beats keep the old grid). */
    void setGrid(const BeatGrid &grid);

    /** Silences the metronome from [frame] on; a click already sounding finishes. */
    void stopMetronome(int64_t frame);

    /** Plays [sound] starting at [frame]. False if all slots are busy. */
    bool addOneShot(int sound, int64_t frame);

    void clearOneShots();

    /** Adds the sounds for frames [firstFrame, firstFrame + count) to [mono]. */
    void render(float *mono, int64_t firstFrame, int32_t count);

    bool metronomeRunning() const { return running_ || hasPending_; }
    const std::vector<float> &sound(int id) const { return sounds_[static_cast<size_t>(id)]; }

private:
    struct OneShot {
        int sound = -1;
        int64_t frame = 0;
    };
    static constexpr size_t kMaxOneShots = 64;

    void startVoice(int sound) {
        voiceSound_ = sound;
        voicePos_ = 0;
    }

    std::array<std::vector<float>, kSoundCount> sounds_;
    BeatGrid grid_{};
    BeatGrid pending_{};
    bool running_ = false;
    bool hasPending_ = false;
    int64_t stopFrame_ = INT64_MAX;
    int64_t nextBeat_ = 0;
    int64_t nextBeatFrame_ = 0;
    int voiceSound_ = -1;
    size_t voicePos_ = 0;
    std::array<OneShot, kMaxOneShots> oneShots_{};
};

}  // namespace mr
