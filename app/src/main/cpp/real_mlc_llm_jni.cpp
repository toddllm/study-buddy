#include <jni.h>
#include <string>
#include <android/log.h>
#include <fstream>
#include <memory>
#include <vector>
#include <unordered_map>
#include <dlfcn.h>

// Include MLC-LLM headers
#include <tvm/runtime/packed_func.h>
#include <tvm/runtime/registry.h>
#include <tvm/runtime/module.h>
#include <tvm/runtime/device_api.h>

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "REAL_MLC_LLM", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "REAL_MLC_LLM", __VA_ARGS__))

/**
 * This is the real implementation of the MLC-LLM engine.
 */
class RealMlcEngine {
private:
    // TVM and MLC-LLM objects
    tvm::runtime::Module module_{nullptr};
    tvm::runtime::PackedFunc model_load_{nullptr};
    tvm::runtime::PackedFunc generate_{nullptr};
    tvm::runtime::PackedFunc reset_chat_{nullptr};
    tvm::runtime::PackedFunc set_param_{nullptr};
    
    bool initialized = false;
    std::string model_path;
    
    // Generation parameters
    float temperature = 0.7f;
    float top_p = 0.95f;
    int max_gen_len = 1024;
    
    // Configure the chat module with current parameters
    void configure_chat() {
        LOGI("Configuring chat with temperature=%.2f, top_p=%.2f, max_gen_len=%d", 
             temperature, top_p, max_gen_len);
        
        if (set_param_ != nullptr) {
            try {
                set_param_("temperature", temperature);
                set_param_("top_p", top_p);
                set_param_("max_gen_len", max_gen_len);
            } catch (const std::exception& e) {
                LOGE("Error setting parameters: %s", e.what());
            }
        }
    }
    
public:
    RealMlcEngine() : initialized(false) {
        LOGI("Creating RealMlcEngine");
    }
    
    ~RealMlcEngine() {
        close();
    }
    
    bool initialize(const std::string& model_dir) {
        try {
            LOGI("Initializing MLC-LLM with model directory: %s", model_dir.c_str());
            model_path = model_dir;
            
            // Verify model files exist
            std::ifstream configFile(model_dir + "/mlc-chat-config.json");
            if (!configFile.good()) {
                LOGE("Config file not found at %s/mlc-chat-config.json", model_dir.c_str());
                return false;
            }
            
            // Check if model lib exists - REQUIRE it to exist
            std::string model_lib_path = model_dir + "/lib/libgemma-2-2b-it-q4f16_1.so";
            std::ifstream modelLib(model_lib_path);
            if (!modelLib.good()) {
                LOGE("FATAL: Model library not found at %s", model_lib_path.c_str());
                LOGE("The model library must exist at this exact path");
                return false;
            }
            
            // List all available TVM registry functions for debugging
            auto registry_names = tvm::runtime::Registry::ListNames();
            LOGI("Available TVM registry functions (%lu):", registry_names.size());
            
            // Log all registry functions to help with debugging
            for (size_t i = 0; i < registry_names.size(); ++i) {
                LOGI("  Function %zu: %s", i, registry_names[i].c_str());
            }
            
            // Try to use the TVM Registry approach first
            LOGI("Looking for function: mlc.create_chat_module");
            auto chat_create = tvm::runtime::Registry::Get("mlc.create_chat_module");
            
            // Also try alternative module names based on the model library path
            if (chat_create == nullptr) {
                LOGI("Function not found, trying alternative: mlc_create_chat_module");
                chat_create = tvm::runtime::Registry::Get("mlc_create_chat_module");
            }
            
            if (chat_create == nullptr) {
                // TVM registry approach failed, try direct loading with dlopen/dlsym
                LOGI("TVM registry method failed, trying direct library loading: %s", model_lib_path.c_str());
                
                // Open the library
                void* lib_handle = dlopen(model_lib_path.c_str(), RTLD_LAZY);
                if (!lib_handle) {
                    LOGE("FATAL: Failed to open model library with dlopen: %s", dlerror());
                    return false;
                }
                
                // Clear any existing error
                dlerror();
                
                // Try to find the mlc_create_chat_module function
                typedef void* (*CreateChatModuleFunc)(const char*);
                CreateChatModuleFunc create_func = (CreateChatModuleFunc)dlsym(lib_handle, "mlc_create_chat_module");
                const char* dlsym_error = dlerror();
                
                if (dlsym_error) {
                    LOGE("FATAL: Could not find mlc_create_chat_module symbol: %s", dlsym_error);
                    dlclose(lib_handle);
                    
                    LOGE("FATAL: Required chat module creation function not found in registry");
                    LOGE("Please make sure the model library implements one of: mlc.create_chat_module or mlc_create_chat_module");
                    return false;
                }
                
                // Call the function to create the module
                void* module_ptr = create_func(model_dir.c_str());
                if (!module_ptr) {
                    LOGE("FATAL: Create chat module function returned NULL");
                    dlclose(lib_handle);
                    return false;
                }
                
                LOGI("Successfully created chat module using direct dlsym approach");
                
                // Now load the other functions
                typedef void (*LoadModelFunc)();
                typedef char* (*GenerateFunc)(const char*);
                typedef void (*ResetChatFunc)();
                typedef void (*SetParamFunc)(const char*, float);
                
                LoadModelFunc load_model_func = (LoadModelFunc)dlsym(lib_handle, "load_model");
                GenerateFunc generate_func = (GenerateFunc)dlsym(lib_handle, "generate");
                ResetChatFunc reset_chat_func = (ResetChatFunc)dlsym(lib_handle, "reset_chat");
                SetParamFunc set_param_func = (SetParamFunc)dlsym(lib_handle, "set_parameter");
                
                // Check that all functions were found
                if (!load_model_func || !generate_func || !reset_chat_func || !set_param_func) {
                    LOGE("FATAL: Failed to find all required functions");
                    if (!load_model_func) LOGE("Missing: load_model");
                    if (!generate_func) LOGE("Missing: generate");
                    if (!reset_chat_func) LOGE("Missing: reset_chat");
                    if (!set_param_func) LOGE("Missing: set_parameter");
                    dlclose(lib_handle);
                    return false;
                }
                
                // Create wrapper functions that call the loaded functions
                model_load_ = tvm::runtime::PackedFunc([load_model_func](tvm::runtime::TVMArgs args, tvm::runtime::TVMRetValue* rv) {
                    load_model_func();
                });
                
                generate_ = tvm::runtime::PackedFunc([generate_func](tvm::runtime::TVMArgs args, tvm::runtime::TVMRetValue* rv) {
                    std::string prompt = args[0];
                    char* result = generate_func(prompt.c_str());
                    std::string result_str(result);
                    free(result); // Assume the function allocates memory we need to free
                    *rv = result_str;
                });
                
                reset_chat_ = tvm::runtime::PackedFunc([reset_chat_func](tvm::runtime::TVMArgs args, tvm::runtime::TVMRetValue* rv) {
                    reset_chat_func();
                });
                
                set_param_ = tvm::runtime::PackedFunc([set_param_func](tvm::runtime::TVMArgs args, tvm::runtime::TVMRetValue* rv) {
                    std::string key = args[0];
                    float value = static_cast<float>(static_cast<double>(args[1]));
                    set_param_func(key.c_str(), value);
                });
                
                // Create a fake module since we're not using TVM's module system
                module_ = tvm::runtime::Module(nullptr);
                
                // Call load_model to initialize
                load_model_func();
                LOGI("Model loaded successfully using direct function calls");
            } else {
                LOGI("Found required function: mlc.create_chat_module");
                
                // Create the chat module by passing the model directory
                try {
                    module_ = (*chat_create)(model_dir);
                    LOGI("Created chat module");
                } catch (const std::exception& e) {
                    LOGE("FATAL: Failed to create chat module: %s", e.what());
                    return false;
                }
                
                // Get the necessary functions from the module
                model_load_ = module_.GetFunction("load_model");
                generate_ = module_.GetFunction("generate");
                reset_chat_ = module_.GetFunction("reset_chat");
                set_param_ = module_.GetFunction("set_parameter");
                
                // Check if we got all required functions
                if (model_load_ == nullptr || generate_ == nullptr || 
                    reset_chat_ == nullptr || set_param_ == nullptr) {
                    LOGE("FATAL: Failed to get required functions from the module");
                    
                    // Log which functions are missing
                    LOGE("Missing functions:");
                    if (model_load_ == nullptr) LOGE("  load_model");
                    if (generate_ == nullptr) LOGE("  generate");
                    if (reset_chat_ == nullptr) LOGE("  reset_chat");
                    if (set_param_ == nullptr) LOGE("  set_parameter");
                    
                    return false;
                }
                
                // Load the model
                model_load_();
                LOGI("Model loaded successfully");
            }
            
            // Configure generation parameters
            configure_chat();
            
            initialized = true;
            LOGI("MLC-LLM initialization completed successfully");
            return true;
        }
        catch (const std::exception& e) {
            LOGE("FATAL: Error initializing MLC-LLM: %s", e.what());
            return false;
        }
    }
    
    std::string generate_response(const std::string& prompt) {
        if (!initialized) {
            LOGE("FATAL: MLC-LLM engine not initialized");
            return "FATAL ERROR: MLC-LLM engine not initialized. The initialization process failed.";
        }
        
        try {
            LOGI("Generating response for prompt: %s", prompt.c_str());
            
            // First reset the chat to clear any previous conversation
            reset_chat_();
            
            // Generate the response
            std::string response = generate_(prompt);
            
            LOGI("Generated response: %s", response.c_str());
            return response;
        }
        catch (const std::exception& e) {
            LOGE("FATAL: Error generating response: %s", e.what());
            return "FATAL ERROR: " + std::string(e.what());
        }
    }
    
    // Define a callback function for streaming tokens
    void stream_response(const std::string& prompt, std::function<void(std::string)> callback) {
        if (!initialized) {
            LOGE("MLC-LLM engine not initialized");
            callback("Error: MLC-LLM engine not initialized");
            return;
        }
        
        try {
            LOGI("Streaming response for prompt: %s", prompt.c_str());
            
            // Reset the chat
            reset_chat_();
            
            // Get the stream function
            auto stream_func = module_.GetFunction("stream_chat");
            if (stream_func == nullptr) {
                LOGE("Stream function not found");
                callback("Error: Streaming not supported");
                return;
            }
            
            // Create a TVM callback to pass to the stream function
            auto tvm_callback = tvm::runtime::TypedPackedFunc<void(std::string)>(
                [callback](std::string token) {
                    callback(token);
                });
            
            // Call the stream function with the prompt and callback
            stream_func(prompt, tvm_callback);
        }
        catch (const std::exception& e) {
            LOGE("Error streaming response: %s", e.what());
            callback("Error streaming response: " + std::string(e.what()));
        }
    }
    
    void reset_chat() {
        if (!initialized) {
            LOGE("MLC-LLM engine not initialized");
            return;
        }
        
        try {
            LOGI("Resetting chat");
            reset_chat_();
        }
        catch (const std::exception& e) {
            LOGE("Error resetting chat: %s", e.what());
        }
    }
    
    void set_temperature(float temp) {
        temperature = temp;
        LOGI("Set temperature to %.2f", temperature);
        
        if (initialized) {
            configure_chat();
        }
    }
    
    void set_top_p(float p) {
        top_p = p;
        LOGI("Set top_p to %.2f", top_p);
        
        if (initialized) {
            configure_chat();
        }
    }
    
    void set_max_gen_len(int len) {
        max_gen_len = len;
        LOGI("Set max_gen_len to %d", max_gen_len);
        
        if (initialized) {
            configure_chat();
        }
    }
    
    void close() {
        if (initialized) {
            LOGI("Closing MLC-LLM engine");
            // Reset module references to release resources
            model_load_ = tvm::runtime::PackedFunc(nullptr);
            generate_ = tvm::runtime::PackedFunc(nullptr);
            reset_chat_ = tvm::runtime::PackedFunc(nullptr);
            set_param_ = tvm::runtime::PackedFunc(nullptr);
            
            // Create an empty module to replace the current one
            module_ = tvm::runtime::Module(nullptr);
            
            initialized = false;
        }
    }
};

// Global engine instance
std::unique_ptr<RealMlcEngine> g_mlc_engine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_initializeEngine(
        JNIEnv* env,
        jobject /* this */,
        jstring jModelPath) {
    
    const char* model_path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("Initializing MLC-LLM engine with model path: %s", model_path);
    
    try {
        // Create the engine if it doesn't exist
        if (!g_mlc_engine) {
            g_mlc_engine = std::make_unique<RealMlcEngine>();
        }
        
        // Initialize the engine
        bool success = g_mlc_engine->initialize(model_path);
        
        // Clean up
        env->ReleaseStringUTFChars(jModelPath, model_path);
        
        return success ? JNI_TRUE : JNI_FALSE;
    } 
    catch (const std::exception& e) {
        LOGE("Exception in initializeEngine: %s", e.what());
        env->ReleaseStringUTFChars(jModelPath, model_path);
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_generateResponse(
        JNIEnv* env,
        jobject /* this */,
        jstring jPrompt) {
    
    if (!g_mlc_engine) {
        LOGE("Engine not initialized");
        return env->NewStringUTF("Error: Engine not initialized");
    }
    
    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    LOGI("Processing chat prompt: %s", prompt);
    
    try {
        // Generate a response
        std::string response = g_mlc_engine->generate_response(prompt);
        
        // Clean up
        env->ReleaseStringUTFChars(jPrompt, prompt);
        
        return env->NewStringUTF(response.c_str());
    } 
    catch (const std::exception& e) {
        LOGE("Exception in generateResponse: %s", e.what());
        env->ReleaseStringUTFChars(jPrompt, prompt);
        return env->NewStringUTF(("Error: " + std::string(e.what())).c_str());
    }
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_streamResponse(
        JNIEnv* env,
        jobject thiz,
        jstring jPrompt,
        jobject jCallback) {
    
    if (!g_mlc_engine) {
        LOGE("Engine not initialized");
        return;
    }
    
    // Get the callback interface method
    jclass callbackClass = env->GetObjectClass(jCallback);
    jmethodID callbackMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
    
    if (callbackMethod == nullptr) {
        LOGE("Failed to find callback method");
        return;
    }
    
    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    
    // Create a callback function to pass to the C++ stream function
    auto callback = [env, jCallback, callbackMethod](const std::string& token) {
        jstring jToken = env->NewStringUTF(token.c_str());
        env->CallObjectMethod(jCallback, callbackMethod, jToken);
        env->DeleteLocalRef(jToken);
    };
    
    // Stream the response
    g_mlc_engine->stream_response(prompt, callback);
    
    // Clean up
    env->ReleaseStringUTFChars(jPrompt, prompt);
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_resetChat(
        JNIEnv* env,
        jobject /* this */) {
    
    if (!g_mlc_engine) {
        LOGE("Engine not initialized");
        return;
    }
    
    try {
        g_mlc_engine->reset_chat();
    } 
    catch (const std::exception& e) {
        LOGE("Exception in resetChat: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_setTemperature(
        JNIEnv* env,
        jobject /* this */,
        jfloat temperature) {
    
    if (!g_mlc_engine) {
        LOGE("Engine not initialized");
        return;
    }
    
    try {
        g_mlc_engine->set_temperature(temperature);
    } 
    catch (const std::exception& e) {
        LOGE("Exception in setTemperature: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_setTopP(
        JNIEnv* env,
        jobject /* this */,
        jfloat topP) {
    
    if (!g_mlc_engine) {
        LOGE("Engine not initialized");
        return;
    }
    
    try {
        g_mlc_engine->set_top_p(topP);
    } 
    catch (const std::exception& e) {
        LOGE("Exception in setTopP: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_setMaxGenLen(
        JNIEnv* env,
        jobject /* this */,
        jint maxGenLen) {
    
    if (!g_mlc_engine) {
        LOGE("Engine not initialized");
        return;
    }
    
    try {
        g_mlc_engine->set_max_gen_len(maxGenLen);
    } 
    catch (const std::exception& e) {
        LOGE("Exception in setMaxGenLen: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_com_example_studybuddy_ml_MlcLlmBridge_closeEngine(
        JNIEnv* env,
        jobject /* this */) {
    
    if (!g_mlc_engine) {
        LOGE("Engine not initialized");
        return;
    }
    
    try {
        g_mlc_engine->close();
        g_mlc_engine.reset();
        LOGI("Engine closed successfully");
    } 
    catch (const std::exception& e) {
        LOGE("Exception in closeEngine: %s", e.what());
    }
}

} 