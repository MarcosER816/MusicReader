#include "AudioEngine.h"

#include <ctime>

namespace mr {

class AudioEngine::Callback : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    explicit Callback(AudioEngine *engine) : engine_(engine) {}
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *s, void *data, int32_t n) override {
        return engine_->onAudioReady(s, data, n);
    }
    void onErrorAfterClose(oboe::AudioStream *s, oboe::Result e) override { engine_->onErrorAfterClose(s, e); }

private:
    AudioEngine *engine_;
};

AudioEngine::AudioEngine()
    : callback_(std::make_shared<Callback>(this)), renderer_(kSampleRate), mono_(kMaxBlock), inBuf_(kMaxBlock) {}

AudioEngine::~AudioEngine() { stop(); }

int64_t AudioEngine::nowNanos() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
}

oboe::Result AudioEngine::start(bool withInput) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (running_.load() && withInput == withInput_) return oboe::Result::OK;
    if (running_.load()) closeLocked();
    // A fresh start: the metronome and sounds begin silent.
    EngineCommand c;
    while (commands_.read(&c, 1) == 1) {
    }
    renderer_.stopMetronome(INT64_MIN);
    renderer_.clearOneShots();
    metronomeOn_ = false;
    return openAndStartLocked(withInput);
}

oboe::Result AudioEngine::restart() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!running_.load() && !restartNeeded_.load()) return oboe::Result::ErrorInvalidState;
    closeLocked();
    ++restarts_;
    const oboe::Result r = openAndStartLocked(withInput_);
    // The new device may not be ready yet: keep asking, the watchdog retries.
    if (r != oboe::Result::OK) restartNeeded_.store(true, std::memory_order_release);
    return r;
}

void AudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    closeLocked();
    metronomeOn_ = false;
    restartNeeded_.store(false);
}

oboe::Result AudioEngine::openAndStartLocked(bool withInput) {
    withInput_ = withInput;
    restartNeeded_.store(false);

    oboe::AudioStreamBuilder out;
    out.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)  // falls back to Shared when not available
        ->setFormat(oboe::AudioFormat::Float)
        ->setFormatConversionAllowed(true)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setChannelConversionAllowed(true)
        ->setSampleRate(kSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(callback_)
        ->setErrorCallback(callback_);
    oboe::Result r = out.openStream(output_);
    if (r != oboe::Result::OK) {
        output_.reset();
        return r;
    }
    // Two bursts: low latency; the tuner raises it if underruns happen.
    output_->setBufferSizeInFrames(output_->getFramesPerBurst() * 2);
    bufferSize_.store(output_->getBufferSizeInFrames());
    tuner_ = std::make_unique<oboe::LatencyTuner>(*output_);

    if (withInput) {
        oboe::AudioStreamBuilder in;
        in.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setFormatConversionAllowed(true)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setChannelConversionAllowed(true)
            ->setSampleRate(kSampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setInputPreset(oboe::InputPreset::VoiceRecognition)
            ->setBufferCapacityInFrames(kMaxBlock * 2);
        r = in.openStream(input_);
        if (r != oboe::Result::OK) {
            input_.reset();
            output_->close();
            output_.reset();
            tuner_.reset();
            return r;
        }
    }

    // Engine frames continue across restarts, so the metronome and the clock do not jump.
    streamOffset_.store(enginePos_.load());
    presented_.reset();
    written_.reset();
    lastTimestampQuery_.store(0);
    inputStartFrame_.store(-1);
    inputRing_.discardAll();
    drainCallbacks_ = 8;

    if (input_) {
        r = input_->requestStart();
        if (r != oboe::Result::OK) {
            closeLocked();
            return r;
        }
        inputRaw_.store(input_.get(), std::memory_order_release);
    }
    r = output_->requestStart();
    if (r != oboe::Result::OK) {
        closeLocked();
        return r;
    }
    running_.store(true, std::memory_order_release);
    return oboe::Result::OK;
}

void AudioEngine::closeLocked() {
    running_.store(false, std::memory_order_release);
    if (output_) {
        output_->stop();  // waits until the callback has returned
        output_->close();
        output_.reset();
    }
    tuner_.reset();
    inputRaw_.store(nullptr, std::memory_order_release);
    if (input_) {
        input_->stop();
        input_->close();
        input_.reset();
    }
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    // Runs on Oboe's own thread after the stream was closed. Reopening is done by the control
    // side (Kotlin polls restartNeeded()), so this thread never takes the control mutex.
    if (error == oboe::Result::ErrorDisconnected || running_.load()) restartNeeded_.store(true, std::memory_order_release);
}

void AudioEngine::pushCommand(const EngineCommand &c) {
    if (!running_.load()) {
        // No callback running: apply directly (the control mutex is held by the caller).
        switch (c.type) {
            case EngineCommand::kSetGrid: renderer_.setGrid(c.grid); break;
            case EngineCommand::kStopMetronome: renderer_.stopMetronome(c.frame); break;
            case EngineCommand::kOneShot: renderer_.addOneShot(c.sound, c.frame); break;
            case EngineCommand::kClearOneShots: renderer_.clearOneShots(); break;
        }
        return;
    }
    commands_.write(&c, 1);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    // Commands first, so a sound scheduled for this block starts on its exact frame.
    EngineCommand c;
    while (commands_.read(&c, 1) == 1) {
        switch (c.type) {
            case EngineCommand::kSetGrid: renderer_.setGrid(c.grid); break;
            case EngineCommand::kStopMetronome: renderer_.stopMetronome(c.frame); break;
            case EngineCommand::kOneShot: renderer_.addOneShot(c.sound, c.frame); break;
            case EngineCommand::kClearOneShots: renderer_.clearOneShots(); break;
        }
    }

    const int64_t pos = enginePos_.load(std::memory_order_relaxed);
    written_.publish(pos, nowNanos());
    auto *out = static_cast<float *>(audioData);
    const int channels = stream->getChannelCount();
    int32_t done = 0;
    while (done < numFrames) {
        const int32_t n = std::min(numFrames - done, kMaxBlock);
        processInput(n, pos + done);
        std::fill(mono_.begin(), mono_.begin() + n, 0.f);
        renderer_.render(mono_.data(), pos + done, n);
        float *o = out + static_cast<size_t>(done) * channels;
        for (int32_t i = 0; i < n; ++i) {
            const float v = mono_[static_cast<size_t>(i)];
            for (int ch = 0; ch < channels; ++ch) o[i * channels + ch] = v;
        }
        done += n;
    }
    enginePos_.store(pos + numFrames, std::memory_order_release);
    callbacks_.fetch_add(1, std::memory_order_relaxed);
    if (tuner_) {
        tuner_->tune();
        bufferSize_.store(stream->getBufferSizeInFrames(), std::memory_order_relaxed);
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::processInput(int32_t numFrames, int64_t firstFrame) {
    oboe::AudioStream *in = inputRaw_.load(std::memory_order_acquire);
    if (in == nullptr) return;
    if (drainCallbacks_ > 0) {
        // Drop what piled up while the streams started, so the input stays aligned.
        --drainCallbacks_;
        for (int i = 0; i < 16; ++i) {
            auto r = in->read(inBuf_.data(), kMaxBlock, 0);
            if (!r || r.value() < kMaxBlock) break;
        }
        return;
    }
    auto r = in->read(inBuf_.data(), numFrames, 0);  // non-blocking
    int32_t got = 0;
    if (r) {
        got = r.value();
    } else if (r.error() == oboe::Result::ErrorDisconnected) {
        restartNeeded_.store(true, std::memory_order_release);
    }
    if (got < numFrames) {
        std::fill(inBuf_.begin() + got, inBuf_.begin() + numFrames, 0.f);
        inputShortReads_.fetch_add(1, std::memory_order_relaxed);
    }
    if (inputStartFrame_.load(std::memory_order_relaxed) < 0) {
        inputStartFrame_.store(firstFrame, std::memory_order_release);
    }
    if (inputRing_.availableToWrite() >= static_cast<size_t>(numFrames)) {
        inputRing_.write(inBuf_.data(), static_cast<size_t>(numFrames));
    } else {
        inputOverflows_.fetch_add(1, std::memory_order_relaxed);
    }
}

double AudioEngine::presentedFrame() {
    const int64_t now = nowNanos();
    // Refresh the hardware timestamp at most every 20 ms; never block the caller on the mutex.
    if (now - lastTimestampQuery_.load(std::memory_order_relaxed) > 20'000'000) {
        std::unique_lock<std::mutex> lock(mutex_, std::try_to_lock);
        if (lock.owns_lock() && output_ && running_.load()) {
            lastTimestampQuery_.store(now, std::memory_order_relaxed);
            auto ts = output_->getTimestamp(CLOCK_MONOTONIC);
            if (ts) presented_.publish(streamOffset_.load() + ts.value().position, ts.value().timestamp);
        }
    }
    ClockSample s;
    double estimate;
    if (presented_.read(s)) {
        estimate = framesAt(s, now, kSampleRate);
    } else if (written_.read(s)) {
        // No timestamp yet: assume the buffer's worth of frames is still waiting to be played.
        s.frame -= bufferSize_.load(std::memory_order_relaxed);
        estimate = framesAt(s, now, kSampleRate);
    } else {
        estimate = static_cast<double>(enginePos_.load());
    }
    // Never ahead of what was rendered, never backwards.
    estimate = std::min(estimate, static_cast<double>(enginePos_.load(std::memory_order_acquire)));
    return monotonic_.next(estimate);
}

int64_t AudioEngine::metronomeStart(double bpm, int beatsPerBar, double leadMs) {
    std::lock_guard<std::mutex> lock(mutex_);
    BeatGrid g;
    g.anchorFrame = enginePos_.load() + static_cast<int64_t>(leadMs * kSampleRate / 1000.0);
    g.anchorBeat = 0;
    g.framesPerBeat = framesPerBeat(kSampleRate, bpm);
    g.beatsPerBar = beatsPerBar < 1 ? 1 : beatsPerBar;
    g.barOriginBeat = 0;
    EngineCommand c;
    c.type = EngineCommand::kSetGrid;
    c.grid = g;
    pushCommand(c);
    grid_ = g;
    metronomeOn_ = true;
    return g.anchorFrame;
}

void AudioEngine::metronomeUpdate(double bpm, int beatsPerBar, double leadMs) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!metronomeOn_) return;
    const int64_t notBefore = enginePos_.load() + static_cast<int64_t>(leadMs * kSampleRate / 1000.0);
    const BeatGrid g = retempo(grid_, notBefore, framesPerBeat(kSampleRate, bpm), beatsPerBar < 1 ? 1 : beatsPerBar);
    EngineCommand c;
    c.type = EngineCommand::kSetGrid;
    c.grid = g;
    pushCommand(c);
    grid_ = g;
}

void AudioEngine::metronomeStop() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!metronomeOn_) return;
    EngineCommand c;
    c.type = EngineCommand::kStopMetronome;
    c.frame = enginePos_.load();
    pushCommand(c);
    metronomeOn_ = false;
}

bool AudioEngine::metronomeGrid(BeatGrid &out) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!metronomeOn_) return false;
    out = grid_;
    return true;
}

std::vector<int64_t> AudioEngine::scheduleSounds(int sound, int count, double intervalMs, double leadMs) {
    std::lock_guard<std::mutex> lock(mutex_);
    std::vector<int64_t> frames;
    const int64_t first = enginePos_.load() + static_cast<int64_t>(leadMs * kSampleRate / 1000.0);
    for (int k = 0; k < count; ++k) {
        EngineCommand c;
        c.type = EngineCommand::kOneShot;
        c.sound = sound;
        c.frame = first + static_cast<int64_t>(std::llround(k * intervalMs * kSampleRate / 1000.0));
        pushCommand(c);
        frames.push_back(c.frame);
    }
    return frames;
}

int32_t AudioEngine::readInput(float *dst, int32_t max, int64_t *firstFrame) {
    const int64_t start = inputStartFrame_.load(std::memory_order_acquire);
    if (start < 0) {
        *firstFrame = -1;
        return 0;
    }
    *firstFrame = start + static_cast<int64_t>(inputRing_.totalRead());
    return static_cast<int32_t>(inputRing_.read(dst, static_cast<size_t>(max)));
}

EngineDiagnostics AudioEngine::diagnostics() {
    std::lock_guard<std::mutex> lock(mutex_);
    EngineDiagnostics d;
    d.running = running_.load();
    d.restarts = restarts_;
    d.callbacks = callbacks_.load();
    d.inputShortReads = inputShortReads_.load();
    d.inputOverflows = inputOverflows_.load();
    if (output_) {
        d.sampleRate = output_->getSampleRate();
        d.channelCount = output_->getChannelCount();
        d.framesPerBurst = output_->getFramesPerBurst();
        d.bufferSize = output_->getBufferSizeInFrames();
        d.bufferCapacity = output_->getBufferCapacityInFrames();
        d.sharingMode = static_cast<int>(output_->getSharingMode());
        d.performanceMode = static_cast<int>(output_->getPerformanceMode());
        d.audioApi = static_cast<int>(output_->getAudioApi());
        auto x = output_->getXRunCount();
        d.xRunCount = x ? x.value() : -1;
        d.deviceId = output_->getDeviceId();
        auto l = output_->calculateLatencyMillis();
        d.outputLatencyMs = l ? l.value() : -1.0;
    }
    if (input_) {
        d.inputOpen = true;
        d.inputSharingMode = static_cast<int>(input_->getSharingMode());
        d.inputDeviceId = input_->getDeviceId();
        d.inputFramesPerBurst = input_->getFramesPerBurst();
    }
    return d;
}

}  // namespace mr
