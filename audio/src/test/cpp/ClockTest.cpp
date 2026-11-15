#include <gtest/gtest.h>

#include <atomic>
#include <thread>

#include "ClockMath.h"

using namespace mr;

TEST(Clock, InterpolatesWithTheMonotonicClock) {
    ClockSample s{48000, 1'000'000'000};
    EXPECT_DOUBLE_EQ(48000.0, framesAt(s, 1'000'000'000, 48000.0));
    EXPECT_DOUBLE_EQ(48000.0 + 4800.0, framesAt(s, 1'100'000'000, 48000.0));  // +100 ms
    EXPECT_DOUBLE_EQ(48000.0 - 48.0, framesAt(s, 999'000'000, 48000.0));     // -1 ms
}

TEST(Clock, PublisherHasNoSampleUntilTheFirstPublish) {
    ClockPublisher p;
    ClockSample s;
    EXPECT_FALSE(p.read(s));
    p.publish(123, 456);
    ASSERT_TRUE(p.read(s));
    EXPECT_EQ(123, s.frame);
    EXPECT_EQ(456, s.nanos);
    p.reset();
    EXPECT_FALSE(p.read(s));
}

TEST(Clock, ReadersNeverSeeAHalfWrittenSample) {
    ClockPublisher p;
    std::atomic<bool> stop{false};
    // Every published pair has nanos == frame * 1000; a torn read would break that.
    std::thread writer([&] {
        for (int64_t f = 1; !stop.load(); ++f) p.publish(f, f * 1000);
    });
    bool consistent = true;
    for (int i = 0; i < 500000; ++i) {
        ClockSample s;
        if (p.read(s)) consistent &= s.nanos == s.frame * 1000;
    }
    stop = true;
    writer.join();
    EXPECT_TRUE(consistent);
}

TEST(Clock, NeverGoesBackwards) {
    MonotonicFrames m;
    EXPECT_DOUBLE_EQ(100.0, m.next(100.0));
    EXPECT_DOUBLE_EQ(100.0, m.next(99.5));  // a new timestamp landed slightly behind
    EXPECT_DOUBLE_EQ(101.0, m.next(101.0));
    m.reset();
    EXPECT_DOUBLE_EQ(5.0, m.next(5.0));
}
