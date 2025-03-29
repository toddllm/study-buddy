# MLC-LLM JNI Integration

This document explains how to integrate MLC-LLM with Android through JNI (Java Native Interface).

## Issues and Solutions

### Issue: Header Files Not Found

**Error:**
```
fatal error: 'tvm/runtime/c_runtime_api.h' file not found
```

**Solution:**

1. Copy the necessary header files from the TVM source to your project's include directory:

```bash
# Create the include directories
mkdir -p app/src/main/cpp/include/tvm
mkdir -p app/src/main/cpp/include/dlpack
mkdir -p app/src/main/cpp/include/dmlc

# Copy TVM headers
cp -r mlc_llm_temp/mlc-llm/3rdparty/tvm/include/tvm/* app/src/main/cpp/include/tvm/

# Copy dlpack headers
cp -r mlc_llm_temp/mlc-llm/3rdparty/tvm/3rdparty/dlpack/include/dlpack/* app/src/main/cpp/include/dlpack/

# Copy dmlc headers
cp -r mlc_llm_temp/mlc-llm/3rdparty/tvm/3rdparty/dmlc-core/include/dmlc/* app/src/main/cpp/include/dmlc/
```

2. Use the build_app.sh script to automate this process:

```bash
#!/bin/bash
set -e

echo "Building StudyBuddy Android App with MLC-LLM Integration"

# Step 1: Make sure the include directory is properly set up
INCLUDE_DIR="app/src/main/cpp/include"
TVM_HEADERS_SRC="/Users/tdeshane/AndroidStudioProjects/StudyBuddy/mlc_llm_temp/mlc-llm/3rdparty/tvm/include"
DLPACK_HEADERS_SRC="/Users/tdeshane/AndroidStudioProjects/StudyBuddy/mlc_llm_temp/mlc-llm/3rdparty/tvm/3rdparty/dlpack/include"
DMLC_HEADERS_SRC="/Users/tdeshane/AndroidStudioProjects/StudyBuddy/mlc_llm_temp/mlc-llm/3rdparty/tvm/3rdparty/dmlc-core/include"

echo "Ensuring TVM headers are available in $INCLUDE_DIR"
mkdir -p "$INCLUDE_DIR"
if [ ! -d "$INCLUDE_DIR/tvm" ]; then
    echo "Copying TVM headers from $TVM_HEADERS_SRC"
    cp -r "$TVM_HEADERS_SRC/tvm" "$INCLUDE_DIR/"
fi

echo "Ensuring dlpack headers are available in $INCLUDE_DIR"
if [ ! -d "$INCLUDE_DIR/dlpack" ]; then
    echo "Copying dlpack headers from $DLPACK_HEADERS_SRC"
    cp -r "$DLPACK_HEADERS_SRC/dlpack" "$INCLUDE_DIR/"
fi

echo "Ensuring dmlc headers are available in $INCLUDE_DIR"
if [ ! -d "$INCLUDE_DIR/dmlc" ]; then
    echo "Copying dmlc headers from $DMLC_HEADERS_SRC"
    cp -r "$DMLC_HEADERS_SRC/dmlc" "$INCLUDE_DIR/"
fi

# Step 2: Build the app using Gradle
echo "Building Android app with Gradle..."
./gradlew build

echo "Build completed successfully!"
```

### Issue: Linking TVM Libraries

After header files are fixed, you may encounter linking errors when the linker can't find the TVM libraries.

**Error:**
```
ld.lld: error: unable to find library -lmlc_llm
ld.lld: error: unable to find library -ltvm
ld.lld: error: unable to find library -ltvm_runtime
```

**Solution:**

Update the CMakeLists.txt file to correctly specify the path to the prebuilt libraries:

```cmake
cmake_minimum_required(VERSION 3.4.1)
project(tvm_bridge)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define paths to the prebuilt libraries
set(PREBUILT_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a)

# Include directories for TVM headers
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Add the JNI bridge library
add_library(tvm_bridge SHARED
    tvm_bridge.cpp
)

# Find the log library for Android logging
find_library(log-lib log)

# Specify the prebuilt libraries explicitly
add_library(tvm SHARED IMPORTED)
set_target_properties(tvm PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIB_DIR}/libtvm.so)

add_library(tvm_runtime SHARED IMPORTED)
set_target_properties(tvm_runtime PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIB_DIR}/libtvm_runtime.so)

add_library(mlc_llm SHARED IMPORTED)
set_target_properties(mlc_llm PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIB_DIR}/libmlc_llm.so)

# Link the JNI bridge with libraries
target_link_libraries(tvm_bridge
    ${log-lib}
    tvm_runtime
    tvm
    mlc_llm
)
```

### Issue: Architecture Compatibility

If your build includes multiple CPU architectures (e.g., arm64-v8a and x86_64), but your TVM libraries are built for only one architecture, you'll get errors like:

```
ld.lld: error: /path/to/lib/arm64-v8a/libtvm.so is incompatible with elf_x86_64
```

**Solution:**

Update your app's build.gradle.kts to only include the supported architecture:

```kotlin
defaultConfig {
    // ... existing code ...
    ndk {
        abiFilters.clear() // Clear existing filters
        abiFilters.add("arm64-v8a") // Only include arm64-v8a
    }
    
    externalNativeBuild {
        cmake {
            arguments("-DANDROID_STL=c++_shared")
            abiFilters("arm64-v8a") // Only build for arm64-v8a
        }
    }
}
```

### Issue: Missing TVM Java API

If your project references TVM Java API classes (like org.apache.tvm.*), you'll need to either:

1. Include the TVM Java API JAR file, or
2. Create stub implementations of the required classes

**Option 2 Example:**

```java
// Device.java
package org.apache.tvm;

public class Device {
    public int deviceType = 0;
    public int deviceId = 0;
    
    public static Device opencl() {
        return new Device();
    }
}

// Function.java
package org.apache.tvm;

public class Function {
    public interface Callback {
        Object invoke(TVMValue... args);
    }

    public static Function getFunction(String name) {
        return new Function();
    }
    
    public Function pushArg(Object arg) {
        return this;
    }
    
    public TVMValue invoke() {
        return new TVMValue();
    }
    
    // Other methods as needed
}

// More stub classes as needed
```

### Issue: Conflicting Macros in Header Files

When using the C++ API, there may be conflicts between the TVM runtime headers and the DMLC headers.

**Solution:**

Use a simplified approach with the C API only, avoiding complex C++ features:

```cpp
#include <jni.h>
#include <string>
#include <android/log.h>

// Only include the TVM C Runtime API
#include <tvm/runtime/c_runtime_api.h>

// ... rest of the code
```

## JNI Implementation

For initial testing, you can use a simplified JNI implementation:

```cpp
#include <jni.h>
#include <string>
#include <android/log.h>

// Only include the TVM C Runtime API
#include <tvm/runtime/c_runtime_api.h>

#define LOG_TAG "TVMBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global variables for configuration
static float temperature = 0.8f;
static float top_p = 0.95f;
static float repetition_penalty = 1.0f;

// Simplified implementations
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_studybuddy_mlc_TVMBridge_initRuntime(JNIEnv* env, jclass, jstring jModelPath) {
    const char* path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("Initializing MLC-LLM runtime with model at: %s", path);
    
    // Placeholder for actual implementation
    
    env->ReleaseStringUTFChars(jModelPath, path);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_example_studybuddy_mlc_TVMBridge_generateText(JNIEnv* env, jclass, jstring jPrompt, jint maxTokens) {
    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    LOGI("Generate text for prompt: %s (max tokens: %d)", prompt, maxTokens);
    
    // Placeholder implementation
    std::string response = "Placeholder response"; 
    
    env->ReleaseStringUTFChars(jPrompt, prompt);
    return env->NewStringUTF(response.c_str());
}

// ... other methods ...

} // extern "C"
```

## Testing Compilation

You can use the test_compile.sh script to verify your JNI implementation compiles correctly:

```bash
#!/bin/bash
set -e

echo "Testing compilation of tvm_bridge.cpp"

# Paths
SRC_FILE="app/src/main/cpp/tvm_bridge.cpp"
INCLUDE_DIR="app/src/main/cpp/include"
NDK_DIR="/Users/tdeshane/Library/Android/sdk/ndk/27.0.12077973"
CLANG="${NDK_DIR}/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++"

# Ensure headers exist
if [ ! -d "$INCLUDE_DIR/tvm/runtime" ]; then
    echo "Error: TVM headers not found in $INCLUDE_DIR/tvm/runtime"
    echo "Please run ./build_app.sh first to set up the headers"
    exit 1
fi

# Run the compiler directly to test compilation
echo "Running clang++ compiler..."
$CLANG --target=aarch64-none-linux-android24 \
    --sysroot=${NDK_DIR}/toolchains/llvm/prebuilt/darwin-x86_64/sysroot \
    -I${INCLUDE_DIR} \
    -c ${SRC_FILE} -o /tmp/tvm_bridge.o \
    -std=gnu++17 -fPIC

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    rm /tmp/tvm_bridge.o
else
    echo "❌ Compilation failed!"
    exit 1
fi
```

## Complete Integration Process

1. Build MLC-LLM and TVM for Android
2. Copy the built libraries to the app's jniLibs directory
3. Set up the header files in the app's include directory
4. Implement the JNI bridge code using the C API
5. Create stub implementations for any required TVM Java API classes
6. Configure the build system to only build for supported architectures
7. Build and test the app

## Next Steps

Once basic compilation works:

1. Implement the actual MLC-LLM integration using the TVM C API
2. Test the JNI bridge with a small model
3. Implement proper error handling and resource management
4. Add support for advanced features like streaming generation

## Troubleshooting

- If you receive "file not found" errors, ensure all necessary header files are correctly copied
- When using complex C++ features, consider using a simpler C API approach
- Check for macro redefinitions between TVM and other libraries
- Use the test_compile.sh script to quickly verify compilation fixes
- If you have architecture compatibility issues, make sure to only build for the supported architectures
- If you have Java API compatibility issues, create stub implementations of the required classes 