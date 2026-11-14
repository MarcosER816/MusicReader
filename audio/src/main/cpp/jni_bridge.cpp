// JNI bridge between Kotlin (com.musicreader.audio.NativeAudio, AudioCore) and the C++ core.
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include <oboe/Oboe.h>

#include "Calibration.h"
#include "Renderer.h"
#include "engine/AudioEngine.h"

namespace {

mr::AudioEngine &engine() {
    static mr::AudioEngine *instance = new mr::AudioEngine();  // lives as long as the process
    return *instance;
}

jdoubleArray toJava(JNIEnv *env, const std::vector<double> &v) {
    jdoubleArray a = env->NewDoubleArray(static_cast<jsize>(v.size()));
    env->SetDoubleArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
    return a;
}

std::vector<double> resultToVector(const mr::LatencyResult &r) {
    return {r.latencyMs, r.confidence, static_cast<double>(r.found), static_cast<double>(r.expected), r.spreadMs};
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL Java_com_musicreader_audio_AudioCore_nativeOboeVersion(JNIEnv *env, jobject) {
    const std::string text = std::string("Oboe ") + oboe::getVersionText();
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jint JNICALL Java_com_musicreader_audio_NativeAudio_nativeStart(JNIEnv *, jobject, jboolean withInput) {
    return static_cast<jint>(engine().start(withInput == JNI_TRUE));
}

JNIEXPORT void JNICALL Java_com_musicreader_audio_NativeAudio_nativeStop(JNIEnv *, jobject) { engine().stop(); }

JNIEXPORT jint JNICALL Java_com_musicreader_audio_NativeAudio_nativeRestart(JNIEnv *, jobject) {
    return static_cast<jint>(engine().restart());
}

JNIEXPORT jboolean JNICALL Java_com_musicreader_audio_NativeAudio_nativeIsRunning(JNIEnv *, jobject) {
    return engine().isRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_musicreader_audio_NativeAudio_nativeRestartNeeded(JNIEnv *, jobject) {
    return engine().restartNeeded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_com_musicreader_audio_NativeAudio_nativeResultText(JNIEnv *env, jobject, jint code) {
    return env->NewStringUTF(oboe::convertToText(static_cast<oboe::Result>(code)));
}

JNIEXPORT jint JNICALL Java_com_musicreader_audio_NativeAudio_nativeSampleRate(JNIEnv *, jobject) {
    return mr::AudioEngine::kSampleRate;
}

JNIEXPORT jdouble JNICALL Java_com_musicreader_audio_NativeAudio_nativePresentedFrame(JNIEnv *, jobject) {
    return engine().presentedFrame();
}

JNIEXPORT jlong JNICALL Java_com_musicreader_audio_NativeAudio_nativeWrittenFrames(JNIEnv *, jobject) {
    return engine().writtenFrames();
}

JNIEXPORT jlong JNICALL Java_com_musicreader_audio_NativeAudio_nativeMetronomeStart(JNIEnv *, jobject, jdouble bpm,
                                                                                    jint beatsPerBar, jdouble leadMs) {
    return engine().metronomeStart(bpm, beatsPerBar, leadMs);
}

JNIEXPORT void JNICALL Java_com_musicreader_audio_NativeAudio_nativeMetronomeUpdate(JNIEnv *, jobject, jdouble bpm,
                                                                                   jint beatsPerBar, jdouble leadMs) {
    engine().metronomeUpdate(bpm, beatsPerBar, leadMs);
}

JNIEXPORT void JNICALL Java_com_musicreader_audio_NativeAudio_nativeMetronomeStop(JNIEnv *, jobject) {
    engine().metronomeStop();
}

/** [anchorFrame, anchorBeat, framesPerBeat, beatsPerBar, barOriginBeat] or null when off. */
JNIEXPORT jdoubleArray JNICALL Java_com_musicreader_audio_NativeAudio_nativeMetronomeGrid(JNIEnv *env, jobject) {
    mr::BeatGrid g;
    if (!engine().metronomeGrid(g)) return nullptr;
    return toJava(env, {static_cast<double>(g.anchorFrame), static_cast<double>(g.anchorBeat), g.framesPerBeat,
                        static_cast<double>(g.beatsPerBar), static_cast<double>(g.barOriginBeat)});
}

JNIEXPORT jlongArray JNICALL Java_com_musicreader_audio_NativeAudio_nativeScheduleSounds(JNIEnv *env, jobject,
                                                                                        jint sound, jint count,
                                                                                        jdouble intervalMs,
                                                                                        jdouble leadMs) {
    const std::vector<int64_t> frames = engine().scheduleSounds(sound, count, intervalMs, leadMs);
    jlongArray a = env->NewLongArray(static_cast<jsize>(frames.size()));
    std::vector<jlong> tmp(frames.begin(), frames.end());
    env->SetLongArrayRegion(a, 0, static_cast<jsize>(tmp.size()), tmp.data());
    return a;
}

JNIEXPORT jint JNICALL Java_com_musicreader_audio_NativeAudio_nativeReadInput(JNIEnv *env, jobject, jfloatArray dst,
                                                                             jlongArray firstFrame) {
    const jsize max = env->GetArrayLength(dst);
    std::vector<float> tmp(static_cast<size_t>(max));
    int64_t first = -1;
    const int32_t n = engine().readInput(tmp.data(), max, &first);
    if (n > 0) env->SetFloatArrayRegion(dst, 0, n, tmp.data());
    const jlong f = first;
    env->SetLongArrayRegion(firstFrame, 0, 1, &f);
    return n;
}

JNIEXPORT void JNICALL Java_com_musicreader_audio_NativeAudio_nativeDiscardInput(JNIEnv *, jobject) {
    engine().discardInput();
}

JNIEXPORT jdoubleArray JNICALL Java_com_musicreader_audio_NativeAudio_nativeDiagnostics(JNIEnv *env, jobject) {
    const mr::EngineDiagnostics d = engine().diagnostics();
    return toJava(env, {d.running ? 1.0 : 0.0, static_cast<double>(d.sampleRate), static_cast<double>(d.channelCount),
                        static_cast<double>(d.framesPerBurst), static_cast<double>(d.bufferSize),
                        static_cast<double>(d.bufferCapacity), static_cast<double>(d.sharingMode),
                        static_cast<double>(d.performanceMode), static_cast<double>(d.audioApi),
                        static_cast<double>(d.xRunCount), static_cast<double>(d.deviceId), d.inputOpen ? 1.0 : 0.0,
                        static_cast<double>(d.inputSharingMode), static_cast<double>(d.inputDeviceId),
                        static_cast<double>(d.inputFramesPerBurst), d.outputLatencyMs,
                        static_cast<double>(d.callbacks), static_cast<double>(d.inputShortReads),
                        static_cast<double>(d.inputOverflows), static_cast<double>(d.restarts)});
}

/** [latencyMs, confidence, found, expected, spreadMs] */
JNIEXPORT jdoubleArray JNICALL Java_com_musicreader_audio_NativeAudio_nativeAnalyzeChirps(JNIEnv *env, jobject,
                                                                                         jfloatArray recording,
                                                                                         jint length, jlong startFrame,
                                                                                         jlongArray chirpFrames) {
    std::vector<float> rec(static_cast<size_t>(length));
    env->GetFloatArrayRegion(recording, 0, length, rec.data());
    const jsize k = env->GetArrayLength(chirpFrames);
    std::vector<jlong> jframes(static_cast<size_t>(k));
    env->GetLongArrayRegion(chirpFrames, 0, k, jframes.data());
    std::vector<int64_t> frames(jframes.begin(), jframes.end());
    const auto &chirp = engine().sound(mr::kSoundChirp);
    const mr::LatencyResult r = mr::analyzeChirps(rec.data(), rec.size(), startFrame, frames.data(), frames.size(),
                                                  chirp.data(), chirp.size(), mr::AudioEngine::kSampleRate);
    return toJava(env, resultToVector(r));
}

JNIEXPORT jdoubleArray JNICALL Java_com_musicreader_audio_NativeAudio_nativeAnalyzeTaps(JNIEnv *env, jobject,
                                                                                       jfloatArray recording,
                                                                                       jint length, jlong startFrame,
                                                                                       jlongArray clickFrames) {
    std::vector<float> rec(static_cast<size_t>(length));
    env->GetFloatArrayRegion(recording, 0, length, rec.data());
    const jsize k = env->GetArrayLength(clickFrames);
    std::vector<jlong> jframes(static_cast<size_t>(k));
    env->GetLongArrayRegion(clickFrames, 0, k, jframes.data());
    std::vector<int64_t> frames(jframes.begin(), jframes.end());
    const mr::LatencyResult r =
        mr::analyzeTaps(rec.data(), rec.size(), startFrame, frames.data(), frames.size(), mr::AudioEngine::kSampleRate);
    return toJava(env, resultToVector(r));
}

}  // extern "C"
