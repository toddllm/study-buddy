# JNI Integration in StudyBuddy

This document provides details on how StudyBuddy integrates native C++ code with the Kotlin/Java application layer using the Java Native Interface (JNI).

## Overview

The JNI integration enables StudyBuddy to:

1. Run Gemma 2B-IT models efficiently using MLC-LLM's native implementation
2. Provide optimized performance for on-device inference
3. Access TVM runtime features from Kotlin

## Key Components

### 1. Kotlin Bridge Class

The primary JNI bridge is `MlcLlmBridge.kt`, which declares native methods to be implemented in C++:

```kotlin
class MlcLlmBridge {
    companion object {
        init {
            // Load native libraries in correct order
            System.loadLibrary("c++_shared")
            System.loadLibrary("tvm_runtime")
            System.loadLibrary("tvm")
            System.loadLibrary("mlc_llm")
            System.loadLibrary("mlc_llm_jni")
        }
    }
    
    // Native method declarations
    external fun initializeEngine(modelPath: String): Boolean
    external fun generateResponse(prompt: String): String
    external fun streamResponse(prompt: String, callback: (String) -> Unit)
    external fun setTemperature(temperature: Float)
    external fun setTopP(topP: Float)
    external fun setMaxGenLen(maxGenLen: Int)
    external fun resetChat()
    external fun closeEngine()
}
```

### 2. Native Implementation

The C++ implementation resides in `mlc_jni/mlc_llm_jni.cpp` and provides the native side of the JNI bridge:

```cpp
JNIEXPORT jboolean JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_initializeEngine(
        JNIEnv* env, jobject thiz, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing engine with model path: %s", path);
    
    if (!g_engine) {
        g_engine = std::make_unique<SimpleEngine>();
    }
    
    bool result = g_engine->initialize(path);
    env->ReleaseStringUTFChars(model_path, path);
    return result;
}

// Other implementations...
```

### 3. CMake Configuration

The native code is built using CMake (`CMakeLists.txt`):

```cmake
# Add the mlc_llm_jni library using our implementation
add_library(mlc_llm_jni SHARED
    mlc_jni/mlc_llm_jni.cpp
)

# Include headers for the MLC JNI library
target_include_directories(mlc_llm_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Configure mlc_llm_jni - link with log library and other required libraries
target_link_libraries(mlc_llm_jni
    ${log-lib}
    android
    tvm_runtime
    tvm
    mlc_llm
)
```

## Callback Handling

One of the more complex aspects of the JNI integration is handling callbacks for token streaming:

```cpp
// Callback structure for streaming
struct CallbackData {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

// Function to call from C++ to Java
void streamCallback(const char* token) {
    // Implementation calls back to Java
}

JNIEXPORT void JNICALL Java_com_example_studybuddy_ml_MlcLlmBridge_streamResponse(
        JNIEnv* env, jobject thiz, jstring prompt, jobject callback) {
    // Implementation sets up callbacks to Java
}
```

## Native Library Loading

To ensure proper loading of dependencies, the native libraries must be loaded in the correct order:

1. `c++_shared` - C++ shared library
2. `tvm_runtime` - TVM runtime
3. `tvm` - TVM core
4. `mlc_llm` - MLC-LLM implementation
5. `mlc_llm_jni` - Our JNI bridge

## Error Handling

Error handling is a critical part of the JNI integration:

1. C++ exceptions are caught and converted to Java exceptions
2. Memory management is handled carefully to avoid leaks
3. Null checks are performed to prevent crashes

## Debugging Tips

1. Use `LOGI` and `LOGE` macros for logging from native code
2. Check `adb logcat` for native logs
3. Use Android Studio's native debugger to step through C++ code
4. Test native methods individually before integrating

## Performance Considerations

1. Minimize JNI crossings - each call has overhead
2. Use bulk data transfers where possible
3. Keep callbacks to a reasonable frequency
4. Consider using direct ByteBuffer for large data transfers

## Future Improvements

1. Add support for multiple model loading
2. Improve error reporting and recovery
3. Enhance callback performance
4. Add more configuration options 