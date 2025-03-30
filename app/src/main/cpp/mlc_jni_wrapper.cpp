#include <jni.h>
#include <string>
#include <android/log.h>
#include <stdlib.h>

#define TAG "MlcJniWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Forward declarations for the actual Gemma library functions
extern "C" {
    void* mlc_create_chat_module(const char* model_path);
    char* generate(const char* prompt);
    void reset_chat();
    void set_parameter(const char* key, float value);
}

// JNI wrapper functions with the proper naming convention
extern "C" {
    // Implementation of mlc_create_chat_module
    JNIEXPORT jobject JNICALL
    Java_com_example_studybuddy_ml_SimpleMlcModel_mlc_1create_1chat_1module(
            JNIEnv* env, jobject thiz, jstring model_path) {
        LOGI("JNI: mlc_create_chat_module called");
        
        // Convert Java string to C string
        const char* path = env->GetStringUTFChars(model_path, 0);
        LOGI("Model path: %s", path);
        
        // Call the real Gemma library function
        void* module_ptr = mlc_create_chat_module(path);
        
        // Release the string
        env->ReleaseStringUTFChars(model_path, path);
        
        // Check for errors
        if (module_ptr == nullptr) {
            LOGE("CRITICAL ERROR: Failed to create chat module - real implementation required");
            // Throw a Java exception to indicate failure
            env->ThrowNew(
                env->FindClass("java/lang/RuntimeException"),
                "Failed to initialize real Gemma language model - no mock implementation allowed"
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
        
        // Convert Java string to C string
        const char* promptStr = env->GetStringUTFChars(prompt, 0);
        LOGI("Prompt: %s", promptStr);
        
        // Call the real Gemma library function
        char* result = generate(promptStr);
        
        // Release the string
        env->ReleaseStringUTFChars(prompt, promptStr);
        
        // Check for errors
        if (result == nullptr) {
            LOGE("CRITICAL ERROR: Failed to generate response - real implementation required");
            // Throw a Java exception to indicate failure
            env->ThrowNew(
                env->FindClass("java/lang/RuntimeException"),
                "Failed to generate response using real Gemma language model - no mock implementation allowed"
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
        
        // Call the real Gemma library function
        reset_chat();
        
        LOGI("Successfully reset chat using real Gemma library");
    }
    
    // Implementation of set_parameter
    JNIEXPORT void JNICALL
    Java_com_example_studybuddy_ml_SimpleMlcModel_set_1parameter(
            JNIEnv* env, jobject thiz, jstring key, jfloat value) {
        LOGI("JNI: set_parameter called");
        
        // Convert Java string to C string
        const char* keyStr = env->GetStringUTFChars(key, 0);
        LOGI("Parameter %s = %f", keyStr, value);
        
        // Call the real Gemma library function
        set_parameter(keyStr, value);
        
        // Release the string
        env->ReleaseStringUTFChars(key, keyStr);
        
        LOGI("Successfully set parameter using real Gemma library");
    }
} 