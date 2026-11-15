#include <gtest/gtest.h>

#include <thread>
#include <vector>

#include "RingBuffer.h"

using mr::SpscRingBuffer;

TEST(RingBuffer, RoundsCapacityAndWraps) {
    SpscRingBuffer<int> rb(5);
    EXPECT_EQ(8u, rb.capacity());
    int in[6] = {1, 2, 3, 4, 5, 6};
    EXPECT_EQ(6u, rb.write(in, 6));
    int out[4];
    EXPECT_EQ(4u, rb.read(out, 4));
    EXPECT_EQ(1, out[0]);
    EXPECT_EQ(4, out[3]);
    EXPECT_EQ(6u, rb.write(in, 6));  // wraps around
    EXPECT_EQ(0u, rb.write(in, 1));  // full
    EXPECT_EQ(8u, rb.availableToRead());
    int all[8];
    EXPECT_EQ(8u, rb.read(all, 8));
    EXPECT_EQ(5, all[0]);
    EXPECT_EQ(6, all[1]);
    EXPECT_EQ(1, all[2]);
    EXPECT_EQ(6, all[7]);
    EXPECT_EQ(12u, rb.totalRead());
}

TEST(RingBuffer, DiscardAllDropsWaitingItems) {
    SpscRingBuffer<float> rb(16);
    float in[10] = {};
    rb.write(in, 10);
    rb.discardAll();
    EXPECT_EQ(0u, rb.availableToRead());
    EXPECT_EQ(16u, rb.availableToWrite());
}

TEST(RingBuffer, TwoThreadsKeepEveryValueInOrder) {
    SpscRingBuffer<uint32_t> rb(1024);
    constexpr uint32_t total = 2'000'000;
    std::thread producer([&] {
        uint32_t next = 0, chunk[37];
        while (next < total) {
            uint32_t n = 0;
            while (n < 37 && next + n < total) {
                chunk[n] = next + n;
                ++n;
            }
            size_t done = 0;
            while (done < n) done += rb.write(chunk + done, n - done);
            next += n;
        }
    });
    uint32_t expected = 0, buf[53];
    bool ordered = true;
    while (expected < total) {
        const size_t n = rb.read(buf, 53);
        for (size_t i = 0; i < n; ++i) ordered &= buf[i] == expected++;
    }
    producer.join();
    EXPECT_TRUE(ordered);
}
