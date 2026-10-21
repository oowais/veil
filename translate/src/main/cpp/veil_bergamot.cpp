// JNI bridge to bergamot-translator (Mozilla's Firefox Translations engine, D-062). One model
// (one language pair) per handle, translated on the calling thread (BlockingService).

#include <jni.h>
#include <android/log.h>

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"

#define LOG_TAG "VeilBergamot"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using marian::bergamot::BlockingService;
using marian::bergamot::ConcatStrategy;
using marian::bergamot::Response;
using marian::bergamot::ResponseOptions;
using marian::bergamot::TranslationModel;

namespace {

struct Engine {
    std::unique_ptr<BlockingService> service;
    std::shared_ptr<TranslationModel> model;
};

std::string toString(JNIEnv *env, jstring s) {
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

}  // namespace

/** [config]: Marian YAML (model, vocab and shortlist paths, decoding options). 0 on failure. */
extern "C" JNIEXPORT jlong JNICALL
Java_com_owaisahmed_veil_translate_BergamotNative_load(JNIEnv *env, jobject, jstring config) {
    try {
        auto engine = std::make_unique<Engine>();
        auto options = marian::bergamot::parseOptionsFromString(toString(env, config), /*validate=*/false);
        engine->model = std::make_shared<TranslationModel>(options);
        engine->service = std::make_unique<BlockingService>(BlockingService::Config{});
        LOGI("model loaded");
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception &e) {
        LOGE("load failed: %s", e.what());
        return 0;
    }
}

/** Translates all [texts] in one call (Bergamot batches their sentences); null on failure. */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_owaisahmed_veil_translate_BergamotNative_translate(JNIEnv *env, jobject, jlong handle, jobjectArray texts) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    const jsize n = env->GetArrayLength(texts);
    std::vector<std::string> sources;
    sources.reserve(n);
    for (jsize i = 0; i < n; i++) {
        auto s = static_cast<jstring>(env->GetObjectArrayElement(texts, i));
        sources.push_back(toString(env, s));
        env->DeleteLocalRef(s);
    }
    ResponseOptions ro;
    ro.qualityScores = false;
    ro.alignment = false;
    ro.HTML = false;
    ro.sentenceMappings = false;
    ro.concatStrategy = ConcatStrategy::SPACE;
    std::vector<ResponseOptions> options(sources.size(), ro);
    try {
        std::vector<Response> responses = engine->service->translateMultiple(engine->model, std::move(sources), options);
        jobjectArray out = env->NewObjectArray(n, env->FindClass("java/lang/String"), nullptr);
        for (jsize i = 0; i < n && i < static_cast<jsize>(responses.size()); i++) {
            jstring t = env->NewStringUTF(responses[i].target.text.c_str());
            env->SetObjectArrayElement(out, i, t);
            env->DeleteLocalRef(t);
        }
        return out;
    } catch (const std::exception &e) {
        LOGE("translate failed: %s", e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owaisahmed_veil_translate_BergamotNative_unload(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Engine *>(handle);
}
