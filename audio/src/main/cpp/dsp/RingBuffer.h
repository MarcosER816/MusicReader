// Single-producer single-consumer lock-free ring buffer.
// The audio callback is one side, one other thread is the other side. Memory is allocated
// once in the constructor; read() and write() never allocate, lock or block.
#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace mr {

template <typename T>
class SpscRingBuffer {
public:
    /** [capacity] is rounded up to a power of two. */
    explicit SpscRingBuffer(size_t capacity) {
        size_t c = 1;
        while (c < capacity) c <<= 1;
        buffer_.resize(c);
        mask_ = c - 1;
    }

    size_t capacity() const { return buffer_.size(); }

    /** Producer: copies up to [count] items; returns how many fit. */
    size_t write(const T *items, size_t count) {
        const uint64_t head = head_.load(std::memory_order_relaxed);
        const uint64_t tail = tail_.load(std::memory_order_acquire);
        const size_t free = buffer_.size() - static_cast<size_t>(head - tail);
        const size_t n = std::min(count, free);
        for (size_t i = 0; i < n; ++i) buffer_[(head + i) & mask_] = items[i];
        head_.store(head + n, std::memory_order_release);
        return n;
    }

    /** Producer: space left. */
    size_t availableToWrite() const {
        return buffer_.size() - static_cast<size_t>(head_.load(std::memory_order_relaxed) -
                                                    tail_.load(std::memory_order_acquire));
    }

    /** Consumer: copies up to [count] items out; returns how many were read. */
    size_t read(T *out, size_t count) {
        const uint64_t tail = tail_.load(std::memory_order_relaxed);
        const uint64_t head = head_.load(std::memory_order_acquire);
        const size_t n = std::min(count, static_cast<size_t>(head - tail));
        for (size_t i = 0; i < n; ++i) out[i] = buffer_[(tail + i) & mask_];
        tail_.store(tail + n, std::memory_order_release);
        return n;
    }

    /** Consumer: items waiting. */
    size_t availableToRead() const {
        return static_cast<size_t>(head_.load(std::memory_order_acquire) - tail_.load(std::memory_order_relaxed));
    }

    /** Consumer: drops everything written so far. */
    void discardAll() { tail_.store(head_.load(std::memory_order_acquire), std::memory_order_release); }

    /** Total items ever read by the consumer. */
    uint64_t totalRead() const { return tail_.load(std::memory_order_relaxed); }

private:
    std::vector<T> buffer_;
    size_t mask_ = 0;
    alignas(64) std::atomic<uint64_t> head_{0};
    alignas(64) std::atomic<uint64_t> tail_{0};
};

}  // namespace mr
