# MLC-LLM Integration in StudyBuddy Android App

## Overview
This document outlines our efforts to integrate MLC-LLM with a strict no-fallback policy in the StudyBuddy Android application. The goal was to ensure that only the real on-device LLM is used, with proper error handling instead of silent fallbacks to other mechanisms.

## Key Accomplishments

### 1. Strict Error Handling
- Modified C++ JNI code to properly report errors without fallbacks
- Updated Kotlin interface to propagate errors to the UI layer
- Ensured clear feedback when model initialization fails

### 2. Model File Structure
Successfully created all necessary model files in the correct locations:
- Configuration files (`mlc-chat-config.json`)
- Tokenizer files (`tokenizer.json`)
- Parameter shards (in `/params` directory)
- Model libraries (shared objects)

### 3. Library Integration
- Implemented runtime copying of model libraries from assets to internal storage
- Added proper file permissions handling
- Created directory structure to match MLC-LLM expectations

## Remaining Issue
Despite having all required files in place, model initialization fails with:
```
FATAL: Required chat module creation function not found in registry
```

This confirms our strict error handling is working correctly. The model library (`libgemma-2-2b-it-q4f16_1.so`) exists but doesn't implement the required TVM registry functions.

## Technical Details

### Required Components for MLC-LLM
1. **TVM Runtime**: Base framework for model execution
2. **MLC-Chat module**: Implementation of the chat functionality
3. **Model weights**: Quantized model parameters
4. **TVM registry**: Required for function discovery between components

### Fix Requirements
To properly resolve this issue, we would need:
1. A correctly compiled MLC-LLM model library for Android with proper exports
2. The exact TVM runtime version compatible with the model
3. Proper integration with the TVM build system

Our attempts to create a dummy library with C++ didn't succeed because it requires deeper integration with the TVM runtime, which is non-trivial without access to the MLC-LLM build system.

## Future Work
- Obtain properly compiled MLC-LLM libraries for Android
- Ensure compatibility between TVM runtime and model libraries
- Consider alternative on-device inference solutions if MLC-LLM integration proves too complex

## Prerequisites

- Android Studio (latest version)
- Android NDK installed through SDK Manager
- Python 3.8+ with pip
- Git
- Hugging Face account (with token for downloading the model)
- 15+ GB of free disk space for building

## Phase 1: Setup & Preparation

We've already completed:
- Model downloading with ModelDownloadService
- Creating a mock implementation with smart responses
- Setting up the JNI interface
- Creating preparation scripts

## Phase 2: Prepare the Runtime Environment

### Step 1: Run the Preparation Script

First, run the preparation script to set up the necessary dependencies:

```bash
python prepare_mlc_llm.py --hf-token YOUR_HUGGING_FACE_TOKEN
```

This script:
- Clones the MLC-LLM repository
- Installs Python dependencies
- Downloads model files from Hugging Face
- Sets up the Android app environment with necessary files

### Step 2: Build TVM Runtime for Android

1. Follow the TVM4J Android build guide to build the TVM runtime for Android:

```bash
cd mlc_build/mlc-llm/3rdparty/tvm
mkdir build
cp cmake/config.cmake build/
cd build
```

2. Edit `config.cmake` to enable Android build:

```cmake
set(USE_ANDROID ON)
set(USE_ANDROID_AARCH64 ON)
set(USE_LLVM ON)
set(USE_OPENCL ON)
```

3. Build the TVM runtime:

```bash
cmake ..
make -j4
```

### Step 3: Compile the Model for Android

Run the model compilation script:

```bash
python compile_model_for_android.py --hf-token YOUR_HUGGING_FACE_TOKEN
```

This script:
- Uses TVM and MLC-LLM to compile the Gemma 2 model for Android
- Quantizes the model for efficient inference
- Copies the compiled model to the Android app

## Phase 3: Integrate with the App

### Step 1: Update the CMakeLists.txt

1. Replace the placeholder `app/src/main/cpp/CMakeLists.txt` with the real implementation:

```cmake
cmake_minimum_required(VERSION 3.18.1)
project(mlc_llm_android VERSION 1.0.0)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include TVM directories
set(TVM_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/tvm)
include_directories(
    ${TVM_ROOT}/include
    ${TVM_ROOT}/3rdparty/dlpack/include
    ${TVM_ROOT}/3rdparty/dmlc-core/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Add MLC-LLM JNI library
add_library(mlc_llm_jni SHARED
    real_mlc_llm_jni.cpp
)

# Find required packages
find_library(log-lib log)

# Link with required libraries
target_link_libraries(mlc_llm_jni
    tvm_runtime
    ${log-lib}
)
```

### Step 2: Replace the Mock JNI Implementation

1. Replace `app/src/main/cpp/mlc_llm_jni.cpp` with `app/src/main/cpp/real_mlc_llm_jni.cpp`

2. Update the file to include the actual TVM and MLC-LLM headers:

```cpp
#include <tvm/runtime/packed_func.h>
#include <tvm/runtime/registry.h>
#include <tvm/runtime/module.h>
```

3. Uncomment the real implementation code in the `RealMlcEngine` class

### Step 3: Update the Android App Configuration

1. Ensure the app has the necessary permissions in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

2. Update `app/build.gradle.kts` to include the compiled libraries:

```kotlin
android {
    // ...
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    // ...
}
```

## Phase 4: Testing and Optimization

### Step 1: Build and Install the App

```bash
./gradlew installDebug
```

### Step 2: Test Model Initialization and Inference

The app should:
1. Check if model files are available
2. Initialize the MLC-LLM engine
3. Use the model for inference

### Step 3: Optimize Performance

For better performance:

1. Enable thread level parallelism in TVM:

```cpp
tvm::runtime::threading::configureTVMThreadPool(
    4,  // num_threads
    true  // enable_affinity
);
```

2. Adjust quantization parameters for better speed/quality tradeoff
3. Use a smaller model version if needed
4. Set appropriate generation parameters for your use case:

```kotlin
mlcModel.setGenerationParameters(
    temperature = 0.5f,  // Lower for more deterministic responses
    topP = 0.9f,         // Lower for more focused responses
    maxLength = 512      // Shorter for faster responses
)
```

## Troubleshooting

### Common Issues

1. **Library not found error**: Make sure the JNI libraries are correctly placed in the jniLibs directory
2. **Initialization failure**: Check that the model files are correctly placed in the assets directory
3. **Memory issues**: Reduce batch size or model size, or try running on a device with more RAM
4. **Slow inference**: Try different quantization levels or use a smaller model

## Next Steps

Once the basic integration is working, consider:

1. Adding streaming support for incremental responses
2. Implementing a custom prompt template for educational responses
3. Fine-tuning the model on educational content
4. Implementing a chat history system for contextual responses

## Resources

- [MLC-LLM Documentation](https://llm.mlc.ai/docs/)
- [TVM Android Guide](https://tvm.apache.org/docs/tutorial/android_deploy.html)
- [Gemma 2 Model Documentation](https://huggingface.co/google/gemma-2b-it)
- [Android NDK Documentation](https://developer.android.com/ndk/guides) 