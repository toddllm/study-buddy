#include <jni.h>
#include <string>
#include <android/log.h>
#include <memory>
#include <vector>
#include <fstream>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <functional>
#include <queue>

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "MLC_LLM_JNI", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "MLC_LLM_JNI", __VA_ARGS__))
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, "MLC_LLM_JNI", __VA_ARGS__))

// Function signature for callback function
typedef std::function<void(const char*)> TokenCallback;

// Declare functions that will be implemented in the real JNI library
extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_initializeEngine(
            JNIEnv* env, jobject thiz, jstring model_path);
            
    JNIEXPORT jstring JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_generateResponse(
            JNIEnv* env, jobject thiz, jstring prompt);
            
    JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_streamResponse(
            JNIEnv* env, jobject thiz, jstring prompt, jobject callback);
            
    JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_setTemperature(
            JNIEnv* env, jobject thiz, jfloat temperature);
            
    JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_setTopP(
            JNIEnv* env, jobject thiz, jfloat top_p);
            
    JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_setMaxGenLen(
            JNIEnv* env, jobject thiz, jint max_gen_len);
            
    JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_resetChat(
            JNIEnv* env, jobject thiz);
            
    JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_closeEngine(
            JNIEnv* env, jobject thiz);
}

// Thread-safe queue for handling token generation in a separate thread
class TokenQueue {
private:
    std::queue<std::string> queue_;
    std::mutex mutex_;
    std::condition_variable cond_;
    bool done_ = false;

public:
    void push(const std::string& token) {
        std::lock_guard<std::mutex> lock(mutex_);
        queue_.push(token);
        cond_.notify_one();
    }

    bool pop(std::string& token) {
        std::unique_lock<std::mutex> lock(mutex_);
        cond_.wait(lock, [this] { return !queue_.empty() || done_; });
        
        if (done_ && queue_.empty()) {
            return false;
        }
        
        token = queue_.front();
        queue_.pop();
        return true;
    }

    void finish() {
        std::lock_guard<std::mutex> lock(mutex_);
        done_ = true;
        cond_.notify_all();
    }

    void reset() {
        std::lock_guard<std::mutex> lock(mutex_);
        while (!queue_.empty()) {
            queue_.pop();
        }
        done_ = false;
    }
};

// Enhanced engine implementation that simulates MLC-LLM behavior more accurately
class MlcEnhancedEngine {
private:
    bool initialized = false;
    std::string model_path;
    float temperature = 0.7f;
    float top_p = 0.95f;
    int max_gen_len = 1024;
    std::unique_ptr<TokenQueue> token_queue;
    
    // Verify model files exist
    bool verifyModelFiles() {
        std::vector<std::string> required_files = {
            "/mlc-chat-config.json",
            "/tokenizer.model",
            "/ndarray-cache.json"
        };
        
        for (const auto& file : required_files) {
            std::ifstream check_file(model_path + file);
            if (!check_file.good()) {
                LOGE("Required file not found: %s", (model_path + file).c_str());
                return false;
            }
            LOGI("Found required file: %s", (model_path + file).c_str());
        }
        
        return true;
    }
    
    // Simulate loading the actual LLM
    bool loadModel() {
        LOGI("Loading model from %s", model_path.c_str());
        // Check if there are tokenizer and config files
        if (!verifyModelFiles()) {
            LOGE("Model verification failed. Missing critical files.");
            return false;
        }
        
        // In a real implementation, this would load the actual model
        // Here we're simulating the success of that operation
        LOGI("Model loaded successfully");
        return true;
    }
    
    // Simulated text generation
    void generateText(const std::string& prompt, std::function<void(const std::string&)> token_callback) {
        LOGI("Generating text with prompt: %s", prompt.c_str());
        
        // Simulate model processing time
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
        
        // Extract relevant context from the prompt
        std::string topic;
        if (prompt.find("math") != std::string::npos || 
            prompt.find("equation") != std::string::npos ||
            prompt.find("algebra") != std::string::npos ||
            prompt.find("calculus") != std::string::npos ||
            prompt.find("geometry") != std::string::npos ||
            prompt.find("arithmetic") != std::string::npos ||
            prompt.find("trigonometry") != std::string::npos) {
            topic = "mathematics";
        } else if (prompt.find("physics") != std::string::npos || 
                  prompt.find("force") != std::string::npos ||
                  prompt.find("gravity") != std::string::npos ||
                  prompt.find("motion") != std::string::npos) {
            topic = "physics";
        } else if (prompt.find("history") != std::string::npos || 
                  prompt.find("world war") != std::string::npos ||
                  prompt.find("ancient") != std::string::npos ||
                  prompt.find("civilization") != std::string::npos) {
            topic = "history";
        } else if (prompt.find("programming") != std::string::npos || 
                  prompt.find("code") != std::string::npos ||
                  prompt.find("algorithm") != std::string::npos ||
                  prompt.find("software") != std::string::npos) {
            topic = "programming";
        } else if (prompt.find("literature") != std::string::npos || 
                  prompt.find("book") != std::string::npos ||
                  prompt.find("novel") != std::string::npos ||
                  prompt.find("poetry") != std::string::npos) {
            topic = "literature";
        } else {
            topic = "general knowledge";
        }
        
        LOGI("Detected topic: %s", topic.c_str());
        
        // Generate a more detailed response based on the topic
        std::vector<std::string> tokens;
        
        if (topic == "mathematics") {
            if (prompt.find("algebra") != std::string::npos) {
                tokens = {
                    "Algebra ", "is ", "a ", "branch ", "of ", "mathematics ", "that ", "deals ", "with ", "symbols ", 
                    "and ", "the ", "rules ", "for ", "manipulating ", "these ", "symbols", ". ",
                    "It ", "forms ", "the ", "foundation ", "for ", "advanced ", "mathematics ", "and ", "is ", "used ", "to ", 
                    "solve ", "equations ", "and ", "find ", "unknown ", "values", ". ",
                    "In ", "algebra", ", ", "we ", "use ", "variables ", "(usually ", "letters ", "like ", "x ", "or ", "y) ", 
                    "to ", "represent ", "unknown ", "quantities ", "and ", "formulate ", "general ", "rules ", "about ", "numbers", "."
                };
            } else {
                tokens = {
                    "Mathematics ", "is ", "the ", "study ", "of ", "numbers", ", ", "quantities", ", ", "and ", "shapes", ". ",
                    "It ", "includes ", "various ", "branches ", "like ", "algebra", ", ", "calculus", ", ", "geometry", ", ", 
                    "and ", "statistics", ". ",
                    "It's ", "a ", "precise ", "discipline ", "that ", "requires ", "careful ", "attention ", "to ", "detail", ". ",
                    "Mathematical ", "concepts ", "help ", "us ", "understand ", "patterns ", "and ", "solve ", "complex ", "problems ", 
                    "in ", "the ", "real ", "world", "."
                };
            }
        } else if (topic == "physics") {
            tokens = {
                "Physics ", "is ", "the ", "natural ", "science ", "that ", "studies ", "matter", ", ", "its ", "motion", ", ", 
                "and ", "behavior ", "through ", "space ", "and ", "time", ". ",
                "It ", "also ", "studies ", "the ", "related ", "entities ", "of ", "energy ", "and ", "force", ". ",
                "Physics ", "is ", "one ", "of ", "the ", "most ", "fundamental ", "scientific ", "disciplines", ", ", 
                "with ", "its ", "main ", "goal ", "being ", "to ", "understand ", "how ", "the ", "universe ", "behaves", "."
            };
        } else if (topic == "programming") {
            tokens = {
                "Programming ", "is ", "the ", "process ", "of ", "creating ", "a ", "set ", "of ", "instructions ", 
                "that ", "tell ", "a ", "computer ", "how ", "to ", "perform ", "a ", "task", ". ",
                "It ", "involves ", "designing ", "algorithms", ", ", "debugging", ", ", "maintaining ", "code", ", ", 
                "and ", "solving ", "problems ", "systematically", ". ",
                "Popular ", "programming ", "languages ", "include ", "Python", ", ", "JavaScript", ", ", "Java", ", ", 
                "and ", "C++", ", ", "each ", "with ", "its ", "own ", "strengths ", "and ", "applications", "."
            };
        } else if (topic == "history") {
            tokens = {
                "History ", "is ", "the ", "study ", "of ", "past ", "events", ", ", "particularly ", "human ", "affairs", ". ",
                "It ", "encompasses ", "the ", "examination ", "of ", "civilizations", ", ", "cultures", ", ", "and ", "societal ", 
                "changes ", "over ", "time", ". ",
                "Historians ", "use ", "various ", "sources ", "like ", "documents", ", ", "artifacts", ", ", "and ", "archaeological ", 
                "evidence ", "to ", "reconstruct ", "and ", "interpret ", "what ", "happened ", "in ", "the ", "past", "."
            };
        } else {
            tokens = {
                "I ", "can ", "help ", "you ", "with ", "many ", "subjects ", "including ", "mathematics", ", ", 
                "physics", ", ", "history", ", ", "programming", ", ", "and ", "literature", ". ",
                "I'm ", "designed ", "to ", "assist ", "with ", "your ", "studies ", "and ", "learning", ". ",
                "Please ", "feel ", "free ", "to ", "ask ", "specific ", "questions ", "about ", "any ", "topic ", 
                "you're ", "interested ", "in ", "or ", "need ", "help ", "with", "."
            };
        }
        
        // Send tokens with a realistic delay
        for (const auto& token : tokens) {
            token_callback(token);
            
            // Add a small random delay between tokens to simulate real generation
            int delay = 50 + (rand() % 100);  // 50-150ms delay
            std::this_thread::sleep_for(std::chrono::milliseconds(delay));
        }
    }
    
public:
    MlcEnhancedEngine() : token_queue(std::make_unique<TokenQueue>()) {
        LOGI("MlcEnhancedEngine created");
    }
    
    ~MlcEnhancedEngine() {
        LOGI("MlcEnhancedEngine destroyed");
    }
    
    bool initialize(const std::string& path) {
        LOGI("Initializing MlcEnhancedEngine with model path: %s", path.c_str());
        model_path = path;
        initialized = loadModel();
        return initialized;
    }
    
    std::string generate(const std::string& prompt) {
        LOGI("Generating response for prompt: %s", prompt.c_str());
        if (!initialized) {
            return "ERROR: Engine not initialized";
        }
        
        std::string full_response;
        
        // Use the token generation function to build a complete response
        generateText(prompt, [&full_response](const std::string& token) {
            full_response += token;
        });
        
        return full_response;
    }
    
    void stream(const std::string& prompt, TokenCallback callback) {
        LOGI("Streaming response for prompt: %s", prompt.c_str());
        if (!initialized) {
            callback("ERROR: Engine not initialized");
            return;
        }
        
        token_queue->reset();
        
        // Start a background thread for generation
        std::thread gen_thread([this, prompt, callback]() {
            generateText(prompt, [this](const std::string& token) {
                token_queue->push(token);
            });
            token_queue->finish();
        });
        gen_thread.detach();
        
        // Process tokens from the queue
        std::string token;
        while (token_queue->pop(token)) {
            callback(token.c_str());
        }
    }
    
    void set_temperature(float temp) {
        temperature = temp;
        LOGI("Temperature set to %.2f", temperature);
    }
    
    void set_top_p(float p) {
        top_p = p;
        LOGI("Top_p set to %.2f", top_p);
    }
    
    void set_max_gen_len(int len) {
        max_gen_len = len;
        LOGI("Max generation length set to %d", max_gen_len);
    }
    
    void reset() {
        LOGI("Resetting chat session");
        token_queue->reset();
    }
    
    void close() {
        LOGI("Closing engine");
        token_queue->finish();
        initialized = false;
    }
};

// Global engine instance
static std::unique_ptr<MlcEnhancedEngine> g_engine;

// Implementation of JNI methods
extern "C" {

JNIEXPORT jboolean JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_initializeEngine(
        JNIEnv* env, jobject thiz, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing engine with model path: %s", path);
    
    if (!g_engine) {
        g_engine = std::make_unique<MlcEnhancedEngine>();
    }
    
    bool result = g_engine->initialize(path);
    env->ReleaseStringUTFChars(model_path, path);
    return result;
}

JNIEXPORT jstring JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_generateResponse(
        JNIEnv* env, jobject thiz, jstring prompt) {
    if (!g_engine) {
        LOGE("Engine not initialized");
        return env->NewStringUTF("ERROR: Engine not initialized");
    }
    
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string response = g_engine->generate(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    return env->NewStringUTF(response.c_str());
}

// Structure to handle callback from C++ to Java
struct StreamCallbackContext {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
    jclass stringClass;
};

// Function to handle callback from C++ to Java
void invokeJavaCallback(const char* token, void* context) {
    StreamCallbackContext* ctx = static_cast<StreamCallbackContext*>(context);
    
    // Create a Java string from the token
    jstring jToken = ctx->env->NewStringUTF(token);
    
    // Call the invoke method with the token
    ctx->env->CallVoidMethod(ctx->callback, ctx->method, jToken);
    
    // Clean up the local reference
    ctx->env->DeleteLocalRef(jToken);
}

JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_streamResponse(
        JNIEnv* env, jobject thiz, jstring prompt, jobject callback) {
    if (!g_engine) {
        LOGE("Engine not initialized");
        
        // Set up Java callback even for error case
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)V");
        jstring errorMsg = env->NewStringUTF("ERROR: Engine not initialized");
        env->CallVoidMethod(callback, invokeMethod, errorMsg);
        env->DeleteLocalRef(errorMsg);
        env->DeleteLocalRef(callbackClass);
        
        return;
    }
    
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    // Set up callback context
    StreamCallbackContext context;
    context.env = env;
    context.callback = env->NewGlobalRef(callback);  // Create global reference to keep it alive
    jclass callbackClass = env->GetObjectClass(callback);
    context.method = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)V");
    context.stringClass = env->FindClass("java/lang/String");
    env->DeleteLocalRef(callbackClass);
    
    // Create a wrapper for the callback
    auto callback_wrapper = [&context](const char* token) {
        invokeJavaCallback(token, &context);
    };
    
    // Start streaming
    g_engine->stream(prompt_str, callback_wrapper);
    
    // Clean up
    env->ReleaseStringUTFChars(prompt, prompt_str);
    env->DeleteGlobalRef(context.callback);
}

JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_setTemperature(
        JNIEnv* env, jobject thiz, jfloat temperature) {
    if (!g_engine) {
        LOGE("Engine not initialized");
        return;
    }
    g_engine->set_temperature(temperature);
}

JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_setTopP(
        JNIEnv* env, jobject thiz, jfloat top_p) {
    if (!g_engine) {
        LOGE("Engine not initialized");
        return;
    }
    g_engine->set_top_p(top_p);
}

JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_setMaxGenLen(
        JNIEnv* env, jobject thiz, jint max_gen_len) {
    if (!g_engine) {
        LOGE("Engine not initialized");
        return;
    }
    g_engine->set_max_gen_len(max_gen_len);
}

JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_resetChat(
        JNIEnv* env, jobject thiz) {
    if (!g_engine) {
        LOGE("Engine not initialized");
        return;
    }
    g_engine->reset();
}

JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_closeEngine(
        JNIEnv* env, jobject thiz) {
    if (!g_engine) {
        LOGE("Engine already closed or not initialized");
        return;
    }
    g_engine->close();
    g_engine.reset();
}

} // extern "C" 