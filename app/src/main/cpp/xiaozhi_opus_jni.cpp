#include <jni.h>
#include <opus/opus.h>

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace {

void ThrowState(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) {
        env->ThrowNew(type, message.c_str());
    }
}

template <typename T>
T* FromHandle(jlong handle) {
    return reinterpret_cast<T*>(static_cast<intptr_t>(handle));
}

jlong ToHandle(void* pointer) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(pointer));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_byd_wjx_xiaozhi_audio_XiaozhiNativeOpus_createEncoder(
    JNIEnv* env,
    jobject,
    jint sample_rate,
    jint channels) {
    int error = OPUS_OK;
    OpusEncoder* encoder = opus_encoder_create(
        sample_rate,
        channels,
        OPUS_APPLICATION_VOIP,
        &error);
    if (encoder == nullptr || error != OPUS_OK) {
        ThrowState(env, "opus_encoder_create failed: " + std::to_string(error));
        return 0;
    }
    opus_encoder_ctl(encoder, OPUS_SET_BITRATE(24000));
    opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(5));
    opus_encoder_ctl(encoder, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
    return ToHandle(encoder);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_byd_wjx_xiaozhi_audio_XiaozhiNativeOpus_encode(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray pcm_array,
    jint frame_samples) {
    OpusEncoder* encoder = FromHandle<OpusEncoder>(handle);
    if (encoder == nullptr || pcm_array == nullptr || frame_samples <= 0) {
        ThrowState(env, "invalid Opus encoder input");
        return nullptr;
    }
    const jsize pcm_bytes = env->GetArrayLength(pcm_array);
    const size_t expected_bytes = static_cast<size_t>(frame_samples) * sizeof(opus_int16);
    if (static_cast<size_t>(pcm_bytes) < expected_bytes) {
        ThrowState(env, "PCM frame is shorter than the declared Opus frame size");
        return nullptr;
    }
    std::vector<jbyte> pcm_storage(static_cast<size_t>(pcm_bytes));
    env->GetByteArrayRegion(pcm_array, 0, pcm_bytes, pcm_storage.data());
    if (env->ExceptionCheck()) return nullptr;

    std::vector<opus_int16> pcm(static_cast<size_t>(frame_samples));
    std::memcpy(pcm.data(), pcm_storage.data(), expected_bytes);
    std::vector<unsigned char> packet(4000);
    const int encoded = opus_encode(
        encoder,
        pcm.data(),
        frame_samples,
        packet.data(),
        static_cast<opus_int32>(packet.size()));
    if (encoded < 0) {
        ThrowState(env, "opus_encode failed: " + std::to_string(encoded));
        return nullptr;
    }
    jbyteArray result = env->NewByteArray(encoded);
    if (result != nullptr && encoded > 0) {
        env->SetByteArrayRegion(
            result,
            0,
            encoded,
            reinterpret_cast<const jbyte*>(packet.data()));
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_wjx_xiaozhi_audio_XiaozhiNativeOpus_destroyEncoder(
    JNIEnv*,
    jobject,
    jlong handle) {
    OpusEncoder* encoder = FromHandle<OpusEncoder>(handle);
    if (encoder != nullptr) opus_encoder_destroy(encoder);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_byd_wjx_xiaozhi_audio_XiaozhiNativeOpus_createDecoder(
    JNIEnv* env,
    jobject,
    jint sample_rate,
    jint channels) {
    int error = OPUS_OK;
    OpusDecoder* decoder = opus_decoder_create(sample_rate, channels, &error);
    if (decoder == nullptr || error != OPUS_OK) {
        ThrowState(env, "opus_decoder_create failed: " + std::to_string(error));
        return 0;
    }
    return ToHandle(decoder);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_byd_wjx_xiaozhi_audio_XiaozhiNativeOpus_decode(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray packet_array,
    jint frame_samples) {
    OpusDecoder* decoder = FromHandle<OpusDecoder>(handle);
    if (decoder == nullptr || packet_array == nullptr || frame_samples <= 0) {
        ThrowState(env, "invalid Opus decoder input");
        return nullptr;
    }
    const jsize packet_size = env->GetArrayLength(packet_array);
    std::vector<unsigned char> packet(static_cast<size_t>(packet_size));
    env->GetByteArrayRegion(
        packet_array,
        0,
        packet_size,
        reinterpret_cast<jbyte*>(packet.data()));
    if (env->ExceptionCheck()) return nullptr;

    std::vector<opus_int16> pcm(static_cast<size_t>(frame_samples));
    const int decoded_samples = opus_decode(
        decoder,
        packet.data(),
        packet_size,
        pcm.data(),
        frame_samples,
        0);
    if (decoded_samples < 0) {
        ThrowState(env, "opus_decode failed: " + std::to_string(decoded_samples));
        return nullptr;
    }
    const jsize output_bytes = static_cast<jsize>(decoded_samples * sizeof(opus_int16));
    jbyteArray result = env->NewByteArray(output_bytes);
    if (result != nullptr && output_bytes > 0) {
        env->SetByteArrayRegion(
            result,
            0,
            output_bytes,
            reinterpret_cast<const jbyte*>(pcm.data()));
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_wjx_xiaozhi_audio_XiaozhiNativeOpus_destroyDecoder(
    JNIEnv*,
    jobject,
    jlong handle) {
    OpusDecoder* decoder = FromHandle<OpusDecoder>(handle);
    if (decoder != nullptr) opus_decoder_destroy(decoder);
}
