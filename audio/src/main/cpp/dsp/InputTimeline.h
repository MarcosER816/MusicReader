// Which engine frame each microphone sample in the input ring buffer belongs to.
//
// The ring's read and write counts keep growing for the whole life of the engine, across
// stream sessions. Each session records the frame of its first sample together with the ring's
// write count at that moment, so a sample's frame is always relative to its own session.
#pragma once

#include <atomic>
#include <cstdint>

namespace mr {

class InputTimeline {
public:
    /** Control side, while no callback runs: a new session starts. */
    void reset() { frame_.store(-1, std::memory_order_release); }

    /** Producer (audio callback), first write of a session: item [writtenIndex] has [frame]. */
    void begin(int64_t frame, uint64_t writtenIndex) {
        index_.store(writtenIndex, std::memory_order_relaxed);
        frame_.store(frame, std::memory_order_release);
    }

    bool started() const { return frame_.load(std::memory_order_acquire) >= 0; }

    /** Consumer: frame of the item with ring read index [readIndex]; -1 before the session began. */
    int64_t frameAt(uint64_t readIndex) const {
        const int64_t f = frame_.load(std::memory_order_acquire);
        if (f < 0) return -1;
        return f + static_cast<int64_t>(readIndex - index_.load(std::memory_order_relaxed));
    }

private:
    std::atomic<int64_t> frame_{-1};
    std::atomic<uint64_t> index_{0};
};

}  // namespace mr
