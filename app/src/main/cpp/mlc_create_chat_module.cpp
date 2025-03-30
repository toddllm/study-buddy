#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <dlfcn.h>

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "MLC_CHAT_MODULE", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "MLC_CHAT_MODULE", __VA_ARGS__))

// Add MLC-LLM integration headers
#include "tvm/runtime/packed_func.h"
#include "tvm/runtime/registry.h"

// Pointer to the real module created by MLC-LLM
static void* g_module = NULL;

// Additional functions needed for MLC-LLM integration
extern "C" {
    // This is the primary function that MLC-LLM needs: mlc_create_chat_module
    // It creates a chat module using the provided model path
    void* mlc_create_chat_module(const char* model_path) {
        LOGI("Creating real chat module for path: %s", model_path);
        
        if (g_module != NULL) {
            LOGI("Module already created, returning existing instance");
            return g_module;
        }
        
        try {
            // Create a proper module path
            std::string full_path(model_path);
            
            LOGI("Initializing MLC-LLM with model path: %s", full_path.c_str());
            
            // Use TVM's registry to get the proper create function
            // This is how MLC-LLM loads models
            auto* create_func = tvm::runtime::Registry::Get("mlc.llm_chat_create");
            if (create_func == nullptr) {
                LOGE("Failed to find mlc.llm_chat_create in registry");
                return nullptr;
            }
            
            // Create the real chat module
            g_module = (*create_func)(full_path);
            LOGI("Created real chat module: %p", g_module);
            
            return g_module;
        } catch (const std::exception& e) {
            LOGE("Exception creating chat module: %s", e.what());
            return nullptr;
        } catch (...) {
            LOGE("Unknown exception creating chat module");
            return nullptr;
        }
    }
    
    // Generate text from the model based on the provided prompt
    char* generate(const char* prompt) {
        LOGI("generate called with prompt: %s", prompt);
        
        if (g_module == nullptr) {
            LOGE("Module not initialized");
            const char* error_msg = "ERROR: Module not initialized";
            char* result = (char*)malloc(strlen(error_msg) + 1);
            strcpy(result, error_msg);
            return result;
        }
        
        try {
            // Get the generate function from the module
            auto* generate_func = tvm::runtime::Registry::Get("mlc.llm_chat_generate");
            if (generate_func == nullptr) {
                LOGE("Failed to find mlc.llm_chat_generate in registry");
                const char* error_msg = "ERROR: Failed to find generate function";
                char* result = (char*)malloc(strlen(error_msg) + 1);
                strcpy(result, error_msg);
                return result;
            }
            
            // Call the real generate function
            std::string response = (*generate_func)(g_module, std::string(prompt));
            LOGI("Generated response: %s", response.c_str());
            
            // Return the response as a C string
            char* result = (char*)malloc(response.size() + 1);
            strcpy(result, response.c_str());
            return result;
        } catch (const std::exception& e) {
            LOGE("Exception generating text: %s", e.what());
            const char* error_msg = "ERROR: Exception generating text";
            char* result = (char*)malloc(strlen(error_msg) + 1);
            strcpy(result, error_msg);
            return result;
        } catch (...) {
            LOGE("Unknown exception generating text");
            const char* error_msg = "ERROR: Unknown exception generating text";
            char* result = (char*)malloc(strlen(error_msg) + 1);
            strcpy(result, error_msg);
            return result;
        }
    }
    
    // Reset the chat history
    void reset_chat() {
        LOGI("reset_chat called");
        
        if (g_module == nullptr) {
            LOGE("Module not initialized");
            return;
        }
        
        try {
            // Get the reset function from the module
            auto* reset_func = tvm::runtime::Registry::Get("mlc.llm_chat_reset");
            if (reset_func == nullptr) {
                LOGE("Failed to find mlc.llm_chat_reset in registry");
                return;
            }
            
            // Call the real reset function
            (*reset_func)(g_module);
            LOGI("Chat reset successfully");
        } catch (const std::exception& e) {
            LOGE("Exception resetting chat: %s", e.what());
        } catch (...) {
            LOGE("Unknown exception resetting chat");
        }
    }
    
    // Set parameters for the model
    void set_parameter(const char* key, float value) {
        LOGI("set_parameter called with %s=%f", key, value);
        
        if (g_module == nullptr) {
            LOGE("Module not initialized");
            return;
        }
        
        try {
            // Get the set parameter function from the module
            auto* param_func = tvm::runtime::Registry::Get("mlc.llm_chat_set_parameter");
            if (param_func == nullptr) {
                LOGE("Failed to find mlc.llm_chat_set_parameter in registry");
                return;
            }
            
            // Call the real set parameter function
            (*param_func)(g_module, std::string(key), value);
            LOGI("Parameter %s set to %f", key, value);
        } catch (const std::exception& e) {
            LOGE("Exception setting parameter: %s", e.what());
        } catch (...) {
            LOGE("Unknown exception setting parameter");
        }
    }
    
    // Clean up when the library is unloaded
    __attribute__((destructor)) void cleanup() {
        LOGI("Cleaning up module");
        g_module = NULL;
    }
} 