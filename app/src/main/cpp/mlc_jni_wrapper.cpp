#include <jni.h>
#include <string>
#include <android/log.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <errno.h>

#define TAG "MlcJniWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Function pointer types for Gemma functions
typedef void* (*CreateModuleFn)(const char*);
typedef char* (*GenerateFn)(const char*);
typedef void (*ResetChatFn)();
typedef void (*SetParameterFn)(const char*, float);

// Global function pointers and library handle
static void* g_lib_handle = nullptr;
static CreateModuleFn g_create_module_fn = nullptr;
static GenerateFn g_generate_fn = nullptr;
static ResetChatFn g_reset_chat_fn = nullptr;
static SetParameterFn g_set_parameter_fn = nullptr;

// Pre-allocated memory buffer to prevent fragmentation
static void* s_buffer = nullptr;

// Optimizes memory usage in a safer way that won't interfere with JIT
bool optimize_memory_usage() {
    // Don't use mlockall - it interferes with Android Runtime's JIT compiler
    // and causes "Failed to write jitted method info in log" errors
    
    // Instead, we'll just hint to the kernel about our memory usage patterns
    LOGI("Using memory optimization that's compatible with JIT compiler");
    
    // Note: mallopt with M_TRIM_THRESHOLD and M_MMAP_THRESHOLD aren't available in Android NDK
    // We'll use alternative approaches for memory optimization
    
    // Pre-allocate some memory to reduce fragmentation
    if (s_buffer == nullptr) {
        s_buffer = malloc(32 * 1024 * 1024);  // 32MB buffer
        if (s_buffer != nullptr) {
            // Touch the pages to ensure they're actually allocated
            memset(s_buffer, 0, 32 * 1024 * 1024);
            LOGI("Pre-allocated 32MB memory buffer to reduce fragmentation");
        }
    }
    
    return true;
}

// Loads the Gemma library and resolves all function pointers
bool initialize_gemma_library() {
    if (g_lib_handle != nullptr) {
        // Already initialized
        return true;
    }
    
    LOGI("Attempting to load Gemma library");
    
    // Optimize memory usage first
    optimize_memory_usage();
    
    // Try to load the library
    const char* lib_name = "libgemma-2-2b-it-q4f16_1.so";
    g_lib_handle = dlopen(lib_name, RTLD_NOW);
    
    if (g_lib_handle == nullptr) {
        const char* error = dlerror();
        LOGE("CRITICAL ERROR: Failed to load Gemma library: %s", error ? error : "unknown error");
        LOGE("CRITICAL ERROR: Real Gemma model is required. Implementation verification failed.");
        return false;
    }
    
    LOGI("Successfully loaded Gemma library");
    
    // Clear any existing errors
    dlerror();
    
    // Resolve function pointers
    g_create_module_fn = (CreateModuleFn)dlsym(g_lib_handle, "mlc_create_chat_module");
    if (g_create_module_fn == nullptr) {
        LOGE("CRITICAL ERROR: Function 'mlc_create_chat_module' not found in model library.");
        LOGE("CRITICAL ERROR: Real Gemma model is required. Implementation verification failed.");
        dlclose(g_lib_handle);
        g_lib_handle = nullptr;
        return false;
    }
    
    g_generate_fn = (GenerateFn)dlsym(g_lib_handle, "generate");
    if (g_generate_fn == nullptr) {
        LOGE("CRITICAL ERROR: Function 'generate' not found in model library.");
        LOGE("CRITICAL ERROR: Real Gemma model is required. Implementation verification failed.");
        dlclose(g_lib_handle);
        g_lib_handle = nullptr;
        return false;
    }
    
    g_reset_chat_fn = (ResetChatFn)dlsym(g_lib_handle, "reset_chat");
    if (g_reset_chat_fn == nullptr) {
        LOGE("CRITICAL ERROR: Function 'reset_chat' not found in model library.");
        LOGE("CRITICAL ERROR: Real Gemma model is required. Implementation verification failed.");
        dlclose(g_lib_handle);
        g_lib_handle = nullptr;
        return false;
    }
    
    g_set_parameter_fn = (SetParameterFn)dlsym(g_lib_handle, "set_parameter");
    if (g_set_parameter_fn == nullptr) {
        LOGE("CRITICAL ERROR: Function 'set_parameter' not found in model library.");
        LOGE("CRITICAL ERROR: Real Gemma model is required. Implementation verification failed.");
        dlclose(g_lib_handle);
        g_lib_handle = nullptr;
        return false;
    }
    
    LOGI("Successfully resolved all Gemma library functions");
    return true;
}

// JNI wrapper functions with the proper naming convention
extern "C" {
    // Implementation of mlc_create_chat_module
    JNIEXPORT jobject JNICALL
    Java_com_example_studybuddy_ml_SimpleMlcModel_mlc_1create_1chat_1module(
            JNIEnv* env, jobject thiz, jstring model_path) {
        LOGI("JNI: mlc_create_chat_module called");
        
        // Initialize the library if not already done
        if (!initialize_gemma_library()) {
            LOGE("CRITICAL ERROR: Failed to initialize Gemma library");
            env->ThrowNew(
                env->FindClass("java/lang/RuntimeException"),
                "Failed to initialize Gemma library - required for real implementation"
            );
            return nullptr;
        }
        
        // Convert Java string to C string
        const char* path = env->GetStringUTFChars(model_path, 0);
        LOGI("Model path: %s", path);
        
        // Call the real Gemma library function through function pointer
        void* module_ptr = g_create_module_fn(path);
        
        // Release the string
        env->ReleaseStringUTFChars(model_path, path);
        
        // Check for errors
        if (module_ptr == nullptr) {
            LOGE("CRITICAL ERROR: Failed to create chat module - real implementation required");
            env->ThrowNew(
                env->FindClass("java/lang/RuntimeException"),
                "Failed to initialize real Gemma language model - proper implementation required"
            );
            return nullptr;
        }
        
        // Convert the result to a Java Long object
        jclass longClass = env->FindClass("java/lang/Long");
        jmethodID longConstructor = env->GetMethodID(longClass, "<init>", "(J)V");
        jobject longObject = env->NewObject(longClass, longConstructor, (jlong)module_ptr);
        
        LOGI("Successfully created chat module using real Gemma library");
        return longObject;
    }
    
    // Implementation of generate
    JNIEXPORT jstring JNICALL
    Java_com_example_studybuddy_ml_SimpleMlcModel_generate(
            JNIEnv* env, jobject thiz, jstring prompt) {
        LOGI("JNI: generate called");
        
        // Check if library is initialized
        if (g_generate_fn == nullptr) {
            LOGE("CRITICAL ERROR: Gemma library not initialized");
            env->ThrowNew(
                env->FindClass("java/lang/RuntimeException"),
                "Gemma library not initialized - required for real implementation"
            );
            return env->NewStringUTF("ERROR: Gemma library not initialized");
        }
        
        // Convert Java string to C string
        const char* promptStr = env->GetStringUTFChars(prompt, 0);
        LOGI("Prompt: %s", promptStr);
        
        // Call the real Gemma library function through function pointer
        char* result = g_generate_fn(promptStr);
        
        // Release the string
        env->ReleaseStringUTFChars(prompt, promptStr);
        
        // Check for errors
        if (result == nullptr) {
            LOGE("CRITICAL ERROR: Failed to generate response - real implementation required");
            env->ThrowNew(
                env->FindClass("java/lang/RuntimeException"),
                "Failed to generate response using real Gemma language model - check logs for details"
            );
            return env->NewStringUTF("ERROR: Failed to generate response from real LLM");
        }
        
        // Convert the result to a Java string
        LOGI("Response: %s", result);
        jstring javaResult = env->NewStringUTF(result);
        
        // Free memory allocated by the Gemma library
        free(result);
        
        LOGI("Successfully generated response using real Gemma library");
        return javaResult;
    }
    
    // Implementation of reset_chat
    JNIEXPORT void JNICALL
    Java_com_example_studybuddy_ml_SimpleMlcModel_reset_1chat(
            JNIEnv* env, jobject thiz) {
        LOGI("JNI: reset_chat called");
        
        // Check if library is initialized
        if (g_reset_chat_fn == nullptr) {
            LOGE("CRITICAL ERROR: Gemma library not initialized");
            env->ThrowNew(
                env->FindClass("java/lang/RuntimeException"),
                "Gemma library not initialized - required for real implementation"
            );
            return;
        }
        
        // Call the real Gemma library function through function pointer
        g_reset_chat_fn();
        
        LOGI("Successfully reset chat using real Gemma library");
    }
    
    // Implementation of set_parameter
    JNIEXPORT void JNICALL
    Java_com_example_studybuddy_ml_SimpleMlcModel_set_1parameter(
            JNIEnv* env, jobject thiz, jstring key, jfloat value) {
        LOGI("JNI: set_parameter called");
        
        // Check if library is initialized
        if (g_set_parameter_fn == nullptr) {
            LOGE("CRITICAL ERROR: Gemma library not initialized");
            env->ThrowNew(
                env->FindClass("java/lang/RuntimeException"),
                "Gemma library not initialized - required for real implementation"
            );
            return;
        }
        
        // Convert Java string to C string
        const char* keyStr = env->GetStringUTFChars(key, 0);
        LOGI("Parameter %s = %f", keyStr, value);
        
        // Call the real Gemma library function through function pointer
        g_set_parameter_fn(keyStr, value);
        
        // Release the string
        env->ReleaseStringUTFChars(key, keyStr);
        
        LOGI("Successfully set parameter using real Gemma library");
    }
    
    // Add a shutdown function to clean up resources
    JNIEXPORT void JNICALL
    Java_com_example_studybuddy_ml_SimpleMlcModel_shutdown_1native(
            JNIEnv* env, jobject thiz) {
        LOGI("JNI: shutdown_native called");
        
        // Free our pre-allocated buffer if it exists
        if (s_buffer != nullptr) {
            free(s_buffer);
            s_buffer = nullptr;
            LOGI("Freed pre-allocated memory buffer");
        }
        
        if (g_lib_handle != nullptr) {
            dlclose(g_lib_handle);
            g_lib_handle = nullptr;
            g_create_module_fn = nullptr;
            g_generate_fn = nullptr;
            g_reset_chat_fn = nullptr;
            g_set_parameter_fn = nullptr;
            LOGI("Successfully shut down Gemma library");
        }
    }
} 