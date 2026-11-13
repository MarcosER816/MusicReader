// JNI bridge between Kotlin (com.musicreader.audio.AudioCore) and the C++ audio core.
#include <jni.h>
#include <string>

#include <oboe/Oboe.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_musicreader_audio_AudioCore_nativeOboeVersion(JNIEnv *env, jobject /* this */) {
    const std::string text = std::string("Oboe ") + oboe::getVersionText();
    return env->NewStringUTF(text.c_str());
}
