#include <gtest/gtest.h>

#include <vector>

#include "InputTimeline.h"
#include "RingBuffer.h"

using namespace mr;

namespace {

/** One engine session as the engine runs it: reset and discard at open, begin on the first write. */
struct Session {
    SpscRingBuffer<float> &ring;
    InputTimeline &timeline;

    void open() {
        timeline.reset();
        ring.discardAll();
    }

    /** The callback writes [n] samples for engine frames starting at [frame]. */
    void callback(int64_t frame, size_t n) {
        if (!timeline.started()) timeline.begin(frame, ring.totalWritten());
        std::vector<float> block(n, 0.f);
        ring.write(block.data(), n);
    }

    /** The consumer reads up to [n]; returns the frame of the first sample read. */
    int64_t read(size_t n, size_t *got) {
        const int64_t first = timeline.frameAt(ring.totalRead());
        std::vector<float> out(n);
        *got = first < 0 ? 0 : ring.read(out.data(), n);
        return first;
    }
};

}  // namespace

TEST(InputTimeline, FramesFollowTheSessionsFirstSample) {
    SpscRingBuffer<float> ring(1u << 12);
    InputTimeline timeline;
    Session s{ring, timeline};
    s.open();
    size_t got = 0;
    EXPECT_EQ(-1, s.read(100, &got));  // nothing written yet
    EXPECT_EQ(0u, got);
    s.callback(5000, 192);
    s.callback(5192, 192);
    EXPECT_EQ(5000, s.read(100, &got));
    EXPECT_EQ(100u, got);
    EXPECT_EQ(5100, s.read(1000, &got));
    EXPECT_EQ(284u, got);
}

// Regression (Mark, Stage 7): a second calibration in the same app run got frame numbers that
// still counted the first session's samples, so its chirps were searched in the wrong place.
TEST(InputTimeline, ASecondSessionStartsAtItsOwnFrame) {
    SpscRingBuffer<float> ring(1u << 12);
    InputTimeline timeline;
    Session s{ring, timeline};
    size_t got = 0;

    s.open();
    for (int k = 0; k < 10; ++k) s.callback(1000 + k * 192, 192);
    while (s.read(500, &got), got > 0) {
    }
    s.callback(2920, 192);  // left unread when the session closes

    s.open();  // the second calibration
    EXPECT_EQ(-1, s.read(10, &got));
    s.callback(250000, 192);
    EXPECT_EQ(250000, s.read(50, &got));
    EXPECT_EQ(50u, got);
    EXPECT_EQ(250050, s.read(500, &got));
}
