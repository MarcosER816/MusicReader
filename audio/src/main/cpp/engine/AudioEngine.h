// The native audio engine: Oboe output (and optional input) streams, the audio clock, the
// metronome and calibration sounds.
//
// Threads:
// - the audio callback (onAudioReady) renders sound and moves microphone samples into a ring
//   buffer. It never allocates, locks, logs or blocks;
// - control calls (start, stop, metronome, diagnostics) come from Kotlin threads and are
//   serialized by a mutex that the callback never touches;
// - commands reach the callback through a lock-free queue.
#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

#include "BeatGrid.h"
#include "ClockMath.h"
#include "Renderer.h"
#include "RingBuffer.h"

namespace mr {

struct EngineCommand {
    enum Type : int { kSetGrid, kStopMetronome, kOneShot, kClearOneShots };
    Type type = kSetGrid;
    BeatGrid grid{};
    int sound = 0;
    int64_t frame = 0;
};

struct EngineDiagnostics {
    bool running = false;
    int sampleRate = 0;
    int channelCount = 0;
    int framesPerBurst = 0;
    int bufferSize = 0;
    int bufferCapacity = 0;
    int sharingMode = 0;       // oboe::SharingMode
    int performanceMode = 0;   // oboe::PerformanceMode
    int audioApi = 0;          // oboe::AudioApi
    int xRunCount = -1;        // -1 if the API does not report it
    int deviceId = 0;
    bool inputOpen = false;
    int inputSharingMode = 0;
    int inputDeviceId = 0;
    int inputFramesPerBurst = 0;
    double outputLatencyMs = -1.0;  // from the timestamps; -1 until known
    int64_t callbacks = 0;
    int64_t inputShortReads = 0;
    int64_t inputOverflows = 0;
    int restarts = 0;
};

class AudioEngine {
public:
    static constexpr int kSampleRate = 48000;

    AudioEngine();
    ~AudioEngine();

    /** Opens and starts the streams; with [withInput] also the microphone (calibration). */
    oboe::Result start(bool withInput);
    void stop();
    /** Closes and reopens the streams (route change); the metronome keeps going. */
    oboe::Result restart();
    bool isRunning() const { return running_.load(std::memory_order_acquire); }
    /** True after a stream was disconnected (headphones plugged or unplugged). */
    bool restartNeeded() const { return restartNeeded_.load(std::memory_order_acquire); }

    // --- Audio clock ---
    /** Engine frame being presented at the speaker now; continuous across restarts. */
    double presentedFrame();
    /** Next engine frame the callback will render. */
    int64_t writtenFrames() const { return enginePos_.load(std::memory_order_acquire); }

    // --- Metronome ---
    /** Starts at [leadMs] from now; returns the frame of beat 0. */
    int64_t metronomeStart(double bpm, int beatsPerBar, double leadMs);
    /** New tempo or time signature from the first beat [leadMs] or more from now. */
    void metronomeUpdate(double bpm, int beatsPerBar, double leadMs);
    void metronomeStop();
    /** The current grid; false if the metronome is off. */
    bool metronomeGrid(BeatGrid &out);

    /** Schedules [count] sounds [intervalMs] apart, the first [leadMs] from now; returns their frames. */
    std::vector<int64_t> scheduleSounds(int sound, int count, double intervalMs, double leadMs);
    const std::vector<float> &sound(int id) const { return renderer_.sound(id); }

    // --- Microphone ---
    /** Reads up to [max] samples; [firstFrame] gets the engine frame of the first one. */
    int32_t readInput(float *dst, int32_t max, int64_t *firstFrame);
    void discardInput() { inputRing_.discardAll(); }

    EngineDiagnostics diagnostics();

    // Called by the Oboe callbacks.
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames);
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error);

private:
    class Callback;

    oboe::Result openAndStartLocked(bool withInput);
    void closeLocked();
    void pushCommand(const EngineCommand &c);
    void processInput(int32_t numFrames, int64_t firstFrame);
    static int64_t nowNanos();

    static constexpr int32_t kMaxBlock = 4096;

    std::mutex mutex_;  // control side only
    std::shared_ptr<Callback> callback_;
    std::shared_ptr<oboe::AudioStream> output_;
    std::shared_ptr<oboe::AudioStream> input_;
    std::unique_ptr<oboe::LatencyTuner> tuner_;
    bool withInput_ = false;
    int restarts_ = 0;
    bool metronomeOn_ = false;
    BeatGrid grid_{};

    // Shared with the callback.
    std::atomic<bool> running_{false};
    std::atomic<bool> restartNeeded_{false};
    std::atomic<oboe::AudioStream *> inputRaw_{nullptr};
    std::atomic<int64_t> enginePos_{0};
    std::atomic<int64_t> streamOffset_{0};  // engine frame of the output stream's frame 0
    std::atomic<int64_t> callbacks_{0};
    std::atomic<int64_t> inputShortReads_{0};
    std::atomic<int64_t> inputOverflows_{0};
    std::atomic<int64_t> inputStartFrame_{-1};
    std::atomic<int32_t> bufferSize_{0};
    int drainCallbacks_ = 0;  // callback only

    Renderer renderer_;
    SpscRingBuffer<EngineCommand> commands_{256};
    SpscRingBuffer<float> inputRing_{1u << 19};  // ~10.9 s at 48 kHz
    std::vector<float> mono_;
    std::vector<float> inBuf_;

    // Clock: timestamps of the presented frame (read on control threads) and, until those
    // exist, the written frame at each callback (published by the callback).
    ClockPublisher presented_;
    ClockPublisher written_;
    MonotonicFrames monotonic_;
    std::atomic<int64_t> lastTimestampQuery_{0};
};

}  // namespace mr
