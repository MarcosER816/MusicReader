// The audio clock: the output stream's presented frame at a monotonic time, published by the
// audio callback and read by any thread, then interpolated with the monotonic clock.
#pragma once

#include <atomic>
#include <cstdint>

namespace mr {

/** One timestamp: [frame] was (or will be) presented at [nanos] on CLOCK_MONOTONIC. */
struct ClockSample {
    int64_t frame = 0;
    int64_t nanos = 0;
};

/** Frame position at [nowNanos], extrapolated from [s] at [sampleRate]. */
inline double framesAt(const ClockSample &s, int64_t nowNanos, double sampleRate) {
    return static_cast<double>(s.frame) + static_cast<double>(nowNanos - s.nanos) * sampleRate * 1e-9;
}

/**
 * A seqlock holding the latest [ClockSample]. One writer (the audio callback) never blocks or
 * allocates; readers retry if they caught a write half done.
 */
class ClockPublisher {
public:
    void publish(int64_t frame, int64_t nanos) {
        const uint32_t s = seq_.load(std::memory_order_relaxed);
        seq_.store(s + 1, std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_release);
        frame_.store(frame, std::memory_order_relaxed);
        nanos_.store(nanos, std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_release);
        seq_.store(s + 2, std::memory_order_relaxed);
        valid_.store(true, std::memory_order_release);
    }

    /** False until the first sample. */
    bool read(ClockSample &out) const {
        if (!valid_.load(std::memory_order_acquire)) return false;
        for (;;) {
            const uint32_t s1 = seq_.load(std::memory_order_acquire);
            if (s1 & 1u) continue;
            const int64_t f = frame_.load(std::memory_order_relaxed);
            const int64_t n = nanos_.load(std::memory_order_relaxed);
            std::atomic_thread_fence(std::memory_order_acquire);
            if (seq_.load(std::memory_order_relaxed) == s1) {
                out.frame = f;
                out.nanos = n;
                return true;
            }
        }
    }

    void reset() { valid_.store(false, std::memory_order_release); }

private:
    std::atomic<uint32_t> seq_{0};
    std::atomic<int64_t> frame_{0};
    std::atomic<int64_t> nanos_{0};
    std::atomic<bool> valid_{false};
};

/**
 * Keeps the clock from going backwards when a new timestamp lands slightly behind the
 * extrapolation. Thread safe for many readers.
 */
class MonotonicFrames {
public:
    double next(double estimate) {
        double last = last_.load(std::memory_order_relaxed);
        while (estimate > last && !last_.compare_exchange_weak(last, estimate, std::memory_order_relaxed)) {
        }
        return estimate > last ? estimate : last;
    }

    void reset(double value = 0.0) { last_.store(value, std::memory_order_relaxed); }

private:
    std::atomic<double> last_{0.0};
};

}  // namespace mr
