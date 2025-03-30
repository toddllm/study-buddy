#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "MLC_CHAT_MODULE", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "MLC_CHAT_MODULE", __VA_ARGS__))

// Keep a global pointer to our dummy module
static void* g_dummy_module = NULL;

// These functions will be called through dlsym later

extern "C" {
    // This is the function MLC-LLM is looking for: mlc_create_chat_module
    // It needs to return a non-null pointer that will be passed to other functions
    void* mlc_create_chat_module(const char* model_path) {
        LOGI("mlc_create_chat_module called with path: %s", model_path);
        
        // Create a dummy struct to hold our "module"
        if (g_dummy_module == NULL) {
            g_dummy_module = malloc(1024); // Allocate some memory
        }
        
        LOGI("Created dummy module: %p", g_dummy_module);
        return g_dummy_module;
    }
    
    // Alternative naming in case it's looking for this variant
    void* tvm_model_create_chat_module(const char* model_path) {
        LOGI("tvm_model_create_chat_module called with path: %s", model_path);
        return mlc_create_chat_module(model_path);
    }
    
    // Basic implementations of required model functions
    void load_model() {
        LOGI("load_model called");
        // No-op implementation
    }
    
    char* generate(const char* prompt) {
        LOGI("generate called with prompt: %s", prompt);
        
        // Return a static message
        const char* message = "I am Gemma, a lightweight language model. Since I'm running in compatibility mode with limited functionality, "
                             "I can only provide this response. In a real implementation, I would analyze your prompt and generate a helpful answer.";
        
        char* result = (char*)malloc(strlen(message) + 1);
        strcpy(result, message);
        return result;
    }
    
    void reset_chat() {
        LOGI("reset_chat called");
        // No-op implementation
    }
    
    void set_parameter(const char* key, float value) {
        LOGI("set_parameter called with %s=%f", key, value);
        // No-op implementation
    }
    
    // Make sure we clean up when the library is unloaded
    __attribute__((destructor)) void cleanup() {
        if (g_dummy_module != NULL) {
            free(g_dummy_module);
            g_dummy_module = NULL;
        }
    }
} 