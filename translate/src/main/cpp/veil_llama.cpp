// Thin JNI bridge to llama.cpp: load a GGUF model, run one chat turn, report timings.
// Calls are independent, but the KV cache keeps the tokens shared with the previous call (the
// chat template and instruction prefix), so only the new text is processed each time.

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <chrono>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

#define TAG "VeilLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;
    llama_batch_ext *batch = nullptr;
    const llama_vocab *vocab = nullptr;
    // Tokens currently in the KV cache (previous prompt + generated tokens), for prefix reuse.
    std::vector<llama_token> cached;
    // Stats of the last translate() call.
    int prompt_tokens = 0;
    int gen_tokens = 0;
    double prompt_ms = 0;
    double gen_ms = 0;
};

void log_callback(ggml_log_level level, const char *text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) {
        __android_log_print(level >= GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, TAG, "%s", text);
    }
}

double ms_since(std::chrono::steady_clock::time_point t) {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t).count();
}

void batch_set_tokens(llama_batch_ext *batch, const llama_token *tokens, int32_t n, llama_pos pos0) {
    llama_batch_ext_clear(batch);
    for (int32_t i = 0; i < n; ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos pos = pos0 + i;
        llama_batch_ext_set_pos(batch, idx, &pos);
    }
    llama_batch_ext_set_output_logits(batch, n - 1, true);
}

std::string jstring_to_std(JNIEnv *env, jstring s) {
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

// NewStringUTF expects modified UTF-8; build from real UTF-8 bytes instead.
jstring std_to_jstring(JNIEnv *env, const std::string &s) {
    jbyteArray bytes = env->NewByteArray((jsize) s.size());
    env->SetByteArrayRegion(bytes, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
    jclass strCls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(strCls, "<init>", "([BLjava/lang/String;)V");
    jstring out = (jstring) env->NewObject(strCls, ctor, bytes, env->NewStringUTF("UTF-8"));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(strCls);
    return out;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_owaisahmed_veil_translate_LlamaNative_init(JNIEnv *env, jobject, jstring native_lib_dir) {
    llama_log_set(log_callback, nullptr);
    const std::string dir = jstring_to_std(env, native_lib_dir);
    // Picks the best libggml-cpu-*.so variant for this CPU (dotprod / i8mm / sve ...).
    ggml_backend_load_all_from_path(dir.c_str());
    llama_backend_init();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_owaisahmed_veil_translate_LlamaNative_load(JNIEnv *env, jobject, jstring jpath, jint n_ctx, jint n_threads,
                                                    jint n_threads_batch) {
    const std::string path = jstring_to_std(env, jpath);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) {
        LOGE("failed to load %s", path.c_str());
        return 0;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx;
    cp.n_batch = std::min(n_ctx, 512);
    cp.n_ubatch = cp.n_batch;
    cp.n_threads = n_threads;
    // Generation is memory-bound, prompt processing compute-bound: they may want different counts (D-033).
    cp.n_threads_batch = n_threads_batch;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        LOGE("failed to create context");
        llama_model_free(model);
        return 0;
    }

    auto *s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->vocab = llama_model_get_vocab(model);
    s->batch = llama_batch_ext_init(ctx);

    // Sampling recommended by the Hy-MT2 model card.
    s->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(s->sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(s->vocab), 64, 1.05f, 0.0f, 0.0f));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(0.6f, 1));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("loaded %s (%.0f MB), ctx=%d threads=%d/%d", path.c_str(), llama_model_size(model) / 1e6, n_ctx, n_threads,
         n_threads_batch);
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_owaisahmed_veil_translate_LlamaNative_complete(JNIEnv *env, jobject, jlong handle, jstring juser, jint max_tokens) {
    auto *s = reinterpret_cast<Session *>(handle);
    const std::string user = jstring_to_std(env, juser);

    // Format with the chat template embedded in the GGUF.
    const char *tmpl = llama_model_chat_template(s->model, nullptr);
    llama_chat_message msg{"user", user.c_str()};
    std::vector<char> buf(user.size() * 2 + 256);
    int n = llama_chat_apply_template(tmpl, &msg, 1, true, buf.data(), (int32_t) buf.size());
    if (n > (int) buf.size()) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl, &msg, 1, true, buf.data(), (int32_t) buf.size());
    }
    if (n < 0) {
        LOGE("chat template failed");
        return nullptr;
    }
    const std::string prompt(buf.data(), n);

    llama_sampler_reset(s->sampler);

    const int n_prompt = -llama_tokenize(s->vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(s->vocab, prompt.c_str(), (int32_t) prompt.size(), tokens.data(), n_prompt, true, true) < 0) {
        LOGE("tokenize failed");
        return nullptr;
    }
    const int n_ctx = (int) llama_n_ctx(s->ctx);
    if (n_prompt + 1 >= n_ctx) {
        LOGE("prompt too long: %d tokens, ctx %d", n_prompt, n_ctx);
        return nullptr;
    }

    // Keep the longest shared prefix with what is already in the KV cache; at least the last prompt
    // token is decoded again so there are logits to sample from.
    llama_memory_t mem = llama_get_memory(s->ctx);
    int keep = 0;
    const int n_cached = (int) s->cached.size();
    while (keep < n_cached && keep < n_prompt - 1 && s->cached[keep] == tokens[keep]) keep++;
    if (!llama_memory_seq_rm(mem, 0, keep, -1)) {
        llama_memory_clear(mem, true);
        keep = 0;
    }
    s->cached.assign(tokens.begin(), tokens.begin() + keep);

    const int n_batch = (int) llama_n_batch(s->ctx);
    auto t0 = std::chrono::steady_clock::now();
    for (int i = keep; i < n_prompt; i += n_batch) {
        const int chunk = std::min(n_batch, n_prompt - i);
        batch_set_tokens(s->batch, tokens.data() + i, chunk, i);
        if (llama_process(s->ctx, LLAMA_PROCESS_TYPE_DECODE, s->batch) != 0) {
            LOGE("prompt decode failed");
            llama_memory_clear(mem, true);
            s->cached.clear();
            return nullptr;
        }
        s->cached.insert(s->cached.end(), tokens.begin() + i, tokens.begin() + i + chunk);
    }
    // Stats count only the tokens actually processed.
    s->prompt_tokens = n_prompt - keep;
    s->prompt_ms = ms_since(t0);

    std::string out;
    int pos = n_prompt;
    int generated = 0;
    auto t1 = std::chrono::steady_clock::now();
    while (generated < max_tokens && pos < n_ctx) {
        llama_token tok = llama_sampler_sample(s->sampler, s->ctx, -1);
        if (llama_vocab_is_eog(s->vocab, tok)) break;
        char piece[256];
        const int len = llama_token_to_piece(s->vocab, tok, piece, sizeof(piece), 0, false);
        if (len > 0) out.append(piece, len);
        generated++;

        batch_set_tokens(s->batch, &tok, 1, pos++);
        if (llama_process(s->ctx, LLAMA_PROCESS_TYPE_DECODE, s->batch) != 0) {
            LOGE("decode failed at pos %d", pos);
            llama_memory_clear(mem, true);
            s->cached.clear();
            break;
        }
        s->cached.push_back(tok);
    }
    s->gen_tokens = generated;
    s->gen_ms = ms_since(t1);

    return std_to_jstring(env, out);
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_owaisahmed_veil_translate_LlamaNative_lastStats(JNIEnv *env, jobject, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    const jdouble v[4] = {(double) s->prompt_tokens, s->prompt_ms, (double) s->gen_tokens, s->gen_ms};
    jdoubleArray arr = env->NewDoubleArray(4);
    env->SetDoubleArrayRegion(arr, 0, 4, v);
    return arr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_owaisahmed_veil_translate_LlamaNative_unload(JNIEnv *, jobject, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (!s) return;
    llama_batch_ext_free(s->batch);
    llama_sampler_free(s->sampler);
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_owaisahmed_veil_translate_LlamaNative_systemInfo(JNIEnv *env, jobject) {
    std::string info = llama_print_system_info();
    for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
        info += "\ndevice: ";
        info += ggml_backend_dev_name(ggml_backend_dev_get(i));
    }
    return std_to_jstring(env, info);
}
