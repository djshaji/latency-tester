#include <jni.h>
#include <android/log.h>
#include <oboe/Oboe.h>
#include "oboe/Utilities.h"

using namespace oboe;

static const char* TAG = "AudioCheckerNative";

static bool tryOpenStream(PerformanceMode perfMode, SharingMode sharingMode) {
    AudioStreamBuilder builder;
    builder.setDirection(Direction::Output);
    builder.setPerformanceMode(perfMode);
    builder.setSharingMode(sharingMode);
    builder.setChannelCount(1);
    // Prefer a typical sample rate; Oboe may adapt if necessary
    builder.setSampleRate(48000);

    AudioStream *stream = nullptr;
    Result r = builder.openStream(&stream);
    if (r != Result::OK || stream == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "openStream failed: %d", static_cast<int>(r));
        return false;
    }

    // Try to start and stop; this is a lightweight verification that the stream can run.
    Result startRes = stream->requestStart();
    if (startRes != Result::OK) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "requestStart failed: %d", static_cast<int>(startRes));
        stream->close();
        return false;
    }

    Result stopRes = stream->requestStop();
    if (stopRes != Result::OK) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "requestStop failed: %d", static_cast<int>(stopRes));
        stream->close();
        return false;
    }

    stream->close();
    return true;
}

static std::string makeResultJson(bool supported, int sampleRate, int framesPerBurst, int resultCode, const std::string &error) {
    char buf[512];
    // build a simple JSON object
    snprintf(buf, sizeof(buf), "{\"supported\":%s,\"sampleRate\":%d,\"framesPerBurst\":%d,\"resultCode\":%d,\"error\":\"%s\"}",
             supported ? "true" : "false", sampleRate, framesPerBurst, resultCode, error.c_str());
    return std::string(buf);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkLowLatencyJson(JNIEnv* env, jclass /*clazz*/) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "checkLowLatencyJson: probing with Oboe");
    AudioStreamBuilder builder;
    builder.setDirection(Direction::Output);
    builder.setPerformanceMode(PerformanceMode::LowLatency);
    builder.setSharingMode(SharingMode::Shared);
    builder.setChannelCount(1);
    builder.setSampleRate(48000);

    AudioStream *stream = nullptr;
    Result r = builder.openStream(&stream);
    if (r != Result::OK || stream == nullptr) {
        std::string json = makeResultJson(false, 0, 0, static_cast<int>(r), "openStream failed");
        return env->NewStringUTF(json.c_str());
    }
    int sampleRate = stream->getSampleRate();
    int frames = stream->getFramesPerBurst();
    Result startRes = stream->requestStart();
    if (startRes != Result::OK) {
        stream->close();
        std::string json = makeResultJson(false, sampleRate, frames, static_cast<int>(startRes), "requestStart failed");
        return env->NewStringUTF(json.c_str());
    }
    Result stopRes = stream->requestStop();
    stream->close();
    if (stopRes != Result::OK) {
        std::string json = makeResultJson(false, sampleRate, frames, static_cast<int>(stopRes), "requestStop failed");
        return env->NewStringUTF(json.c_str());
    }
    std::string json = makeResultJson(true, sampleRate, frames, static_cast<int>(Result::OK), "");
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkExclusiveJson(JNIEnv* env, jclass /*clazz*/) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "checkExclusiveJson: probing with Oboe");
    AudioStreamBuilder builder;
    builder.setDirection(Direction::Output);
    builder.setPerformanceMode(PerformanceMode::None);
    builder.setSharingMode(SharingMode::Exclusive);
    builder.setChannelCount(1);
    builder.setSampleRate(48000);

    AudioStream *stream = nullptr;
    Result r = builder.openStream(&stream);
    if (r != Result::OK || stream == nullptr) {
        std::string json = makeResultJson(false, 0, 0, static_cast<int>(r), "openStream failed");
        return env->NewStringUTF(json.c_str());
    }
    int sampleRate = stream->getSampleRate();
    int frames = stream->getFramesPerBurst();
    Result startRes = stream->requestStart();
    if (startRes != Result::OK) {
        stream->close();
        std::string json = makeResultJson(false, sampleRate, frames, static_cast<int>(startRes), "requestStart failed");
        return env->NewStringUTF(json.c_str());
    }
    Result stopRes = stream->requestStop();
    stream->close();
    if (stopRes != Result::OK) {
        std::string json = makeResultJson(false, sampleRate, frames, static_cast<int>(stopRes), "requestStop failed");
        return env->NewStringUTF(json.c_str());
    }
    std::string json = makeResultJson(true, sampleRate, frames, static_cast<int>(Result::OK), "");
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkProAudioJson(JNIEnv* env, jclass /*clazz*/) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "checkProAudioJson: probing with Oboe");
    // Run low-latency probe
    jstring lowJson = Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkLowLatencyJson(env, nullptr);
    const char* lowC = env->GetStringUTFChars(lowJson, nullptr);
    std::string lowStr(lowC);
    env->ReleaseStringUTFChars(lowJson, lowC);

    // Run exclusive probe
    jstring exclJson = Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkExclusiveJson(env, nullptr);
    const char* exclC = env->GetStringUTFChars(exclJson, nullptr);
    std::string exclStr(exclC);
    env->ReleaseStringUTFChars(exclJson, exclC);

    // Build combined JSON: {"supported":bool,"low":{...},"exclusive":{...}}
    std::string combined = std::string("{\"supported\":") + ((lowStr.find("\"supported\":true") != std::string::npos && exclStr.find("\"supported\":true") != std::string::npos) ? "true" : "false") + ",\"low\":" + lowStr + ",\"exclusive\":" + exclStr + "}";
    return env->NewStringUTF(combined.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_getDeviceReportJson(JNIEnv* env, jclass /*clazz*/) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "getDeviceReportJson: generating device report");

    // Prepare JSON parts
    std::string lowJson;
    {
        AudioStreamBuilder builder;
        builder.setDirection(Direction::Output);
        builder.setPerformanceMode(PerformanceMode::LowLatency);
        builder.setSharingMode(SharingMode::Shared);
        builder.setChannelCount(1);
        builder.setSampleRate(48000);

        AudioStream *stream = nullptr;
        Result r = builder.openStream(&stream);
        if (r != Result::OK || stream == nullptr) {
            lowJson = makeResultJson(false, 0, 0, static_cast<int>(r), "openStream failed");
        } else {
            int sampleRate = stream->getSampleRate();
            int frames = stream->getFramesPerBurst();
            Result startRes = stream->requestStart();
            if (startRes != Result::OK) {
                stream->close();
                lowJson = makeResultJson(false, sampleRate, frames, static_cast<int>(startRes), "requestStart failed");
            } else {
                Result stopRes = stream->requestStop();
                stream->close();
                if (stopRes != Result::OK) {
                    lowJson = makeResultJson(false, sampleRate, frames, static_cast<int>(stopRes), "requestStop failed");
                } else {
                    lowJson = makeResultJson(true, sampleRate, frames, static_cast<int>(Result::OK), "");
                }
            }
        }
    }

    // Read AAudio mmap policy via Oboe Utilities (reads system property aaudio.mmap_policy)
    std::string mmapPolicy = oboe::getPropertyString("aaudio.mmap_policy");

    // Build combined JSON
    std::string combined = std::string("{\"aaudio_mmap_policy\":\"") + (mmapPolicy.empty() ? "unknown" : mmapPolicy) + std::string("\",\"low_performance_mode\":") + lowJson + std::string("}");
    return env->NewStringUTF(combined.c_str());
}


