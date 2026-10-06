#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <memory>
#include <mutex>
#include <set>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
std::mutex inference_mutex;
std::atomic<bool> cancelled{false};
using Clock = std::chrono::steady_clock;
struct Deadline { Clock::time_point end; };
bool abort_decode(void * data) {
    return cancelled.load() || Clock::now() >= static_cast<Deadline *>(data)->end;
}
std::string read(JNIEnv * env, jstring value) {
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("JNI_STRING");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text, bool special) {
    int n = -llama_tokenize(vocab, text.c_str(), text.size(), nullptr, 0, false, special);
    if (n <= 0) throw std::runtime_error("TOKENIZE");
    std::vector<llama_token> tokens(n);
    if (llama_tokenize(vocab, text.c_str(), text.size(), tokens.data(), n, false, special) != n)
        throw std::runtime_error("TOKENIZE");
    return tokens;
}

// Code supplies only the fixed tool syntax and valid choices. Every ambiguous
// material/focus token is selected from the trained model's logits.
class Decoder {
    llama_context * ctx;
    const llama_vocab * vocab;
    Deadline * deadline;
    std::vector<llama_token> pending;
    int total = 0;
public:
    int decisions = 0;
    Decoder(llama_context * c, const llama_vocab * v, Deadline * d, const std::string & prompt)
        : ctx(c), vocab(v), deadline(d), pending(tokenize(v, prompt, true)) {}
    void flush() {
        if (abort_decode(deadline)) throw std::runtime_error(cancelled ? "CANCELLED" : "TIMEOUT");
        if (total + pending.size() > 4096) throw std::runtime_error("CONTEXT_LIMIT");
        for (size_t offset = 0; offset < pending.size(); offset += 256) {
            int count = std::min<size_t>(256, pending.size() - offset);
            if (llama_decode(ctx, llama_batch_get_one(pending.data() + offset, count)) != 0)
                throw std::runtime_error(abort_decode(deadline) ? "TIMEOUT_OR_CANCELLED" : "DECODE_FAILED");
        }
        total += pending.size();
        pending.clear();
    }
    std::string choose(const std::vector<std::string> & choices) {
        if (choices.empty()) throw std::runtime_error("EMPTY_CHOICES");
        std::vector<std::pair<std::string, std::vector<llama_token>>> candidates;
        for (const auto & c : choices) candidates.emplace_back(c, tokenize(vocab, c, false));
        for (size_t pos = 0; ; ++pos) {
            std::set<llama_token> allowed;
            for (const auto & c : candidates) {
                if (c.second.size() <= pos) {
                    if (candidates.size() != 1) throw std::runtime_error("AMBIGUOUS_TOKENIZATION");
                    return c.first;
                }
                allowed.insert(c.second[pos]);
            }
            llama_token selected = *allowed.begin();
            if (allowed.size() > 1) {
                flush();
                const float * logits = llama_get_logits_ith(ctx, -1);
                if (!logits) throw std::runtime_error("NO_LOGITS");
                for (auto token : allowed) {
                    if (!std::isfinite(logits[token])) throw std::runtime_error("INVALID_LOGITS");
                    if (logits[token] > logits[selected]) selected = token;
                }
                ++decisions;
            }
            pending.push_back(selected);
            candidates.erase(std::remove_if(candidates.begin(), candidates.end(),
                [&](const auto & c) { return c.second[pos] != selected; }), candidates.end());
        }
    }
    void fixed(const std::string & text) { choose({text}); }
};
std::vector<std::string> catalog(int type) {
    std::vector<std::string> result;
    for (int i = (type == 2 ? 10 : 0); i < (type == 2 ? 100 : 16); ++i) {
        std::string id = std::string(1, type == 0 ? 'C' : type == 1 ? 'P' : 'N');
        if (i < 10) id += '0';
        result.push_back("\"" + id + std::to_string(i) + "\"");
    }
    return result;
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_memorysteps_ai_author_NativeQuestionAuthor_cancelNative(JNIEnv *, jobject) { cancelled.store(true); }

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_memorysteps_ai_author_NativeQuestionAuthor_generate(
    JNIEnv * env, jobject, jstring path, jstring prompt, jintArray slots, jint option_count,
    jobjectArray recent, jint timeout_ms) {
    std::lock_guard<std::mutex> lock(inference_mutex);
    cancelled.store(false);
    try {
        if (env->GetArrayLength(slots) != 10 || env->GetArrayLength(recent) > 12 || timeout_ms <= 0 ||
            (option_count != 4 && option_count != 6 && option_count != 9 && option_count != 12 && option_count != 16))
            throw std::runtime_error("INVALID_REQUEST");
        jint kinds[10]; env->GetIntArrayRegion(slots, 0, 10, kinds);
        for (int type : kinds) if (type < 0 || type > 2) throw std::runtime_error("INVALID_TYPE");
        std::set<std::string> excluded;
        for (int i = 0; i < env->GetArrayLength(recent); ++i) {
            auto item = static_cast<jstring>(env->GetObjectArrayElement(recent, i));
            excluded.insert("\"" + read(env, item) + "\""); env->DeleteLocalRef(item);
        }
        static std::once_flag initialized;
        std::call_once(initialized, [] { llama_backend_init(); });
        Deadline deadline{Clock::now() + std::chrono::milliseconds(timeout_ms)};
        auto mp = llama_model_default_params(); mp.n_gpu_layers = 0;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
            llama_model_load_from_file(read(env, path).c_str(), mp), llama_model_free);
        if (!model) throw std::runtime_error("MODEL_LOAD_FAILED");
        auto cp = llama_context_default_params();
        cp.n_ctx = 4096; cp.n_batch = 256; cp.n_ubatch = 128;
        cp.n_threads = 4; cp.n_threads_batch = 4;
        cp.abort_callback = abort_decode; cp.abort_callback_data = &deadline;
        std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model.get(), cp), llama_free);
        if (!ctx) throw std::runtime_error("CONTEXT_FAILED");
        Decoder decoder(ctx.get(), llama_model_get_vocab(model.get()), &deadline, read(env, prompt));
        std::string output = "<tool_call>\n{\"name\":\"submit_question_plan\",\"arguments\":{\"focus\":";
        decoder.fixed(output);
        output += decoder.choose({"\"COLOR\"", "\"PICTURE\"", "\"NUMBER\"", "\"BALANCED\"", "\"INSUFFICIENT_DATA\""});
        decoder.fixed(",\"questions\":["); output += ",\"questions\":[";
        std::set<std::string> used;
        for (int slot = 0; slot < 10; ++slot) {
            std::string opening = slot == 0 ? "[" : ",["; decoder.fixed(opening); output += opening;
            std::set<std::string> chosen;
            for (int index = 0; index < option_count; ++index) {
                if (index) { decoder.fixed(","); output += ','; }
                std::vector<std::string> allowed;
                for (const auto & id : catalog(kinds[slot])) {
                    if (!chosen.count(id) && (index != 0 || (!used.count(id) && !excluded.count(id)))) allowed.push_back(id);
                }
                auto id = decoder.choose(allowed); chosen.insert(id); output += id;
                if (index == 0) used.insert(id);
            }
            decoder.fixed("]"); output += ']';
        }
        output += "]}}\n</tool_call>";
        if (decoder.decisions < 41) throw std::runtime_error("NO_MODEL_DECISIONS");
        return env->NewStringUTF(output.c_str());
    } catch (const std::exception & error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}
