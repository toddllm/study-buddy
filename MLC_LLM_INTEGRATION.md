# MLC-LLM Integration in StudyBuddy

This document provides details on how StudyBuddy integrates the MLC-LLM framework for on-device inference.

## Overview

[MLC-LLM](https://github.com/mlc-ai/mlc-llm) is an open-source framework that enables the deployment of Large Language Models (LLMs) across diverse hardware backends and device targets, including Android devices. StudyBuddy uses MLC-LLM to run the Gemma 2B-IT model directly on-device.

## Key Components

### 1. Model Compilation

The Gemma 2B-IT model is compiled with TVM and MLC-LLM to optimize it for mobile devices. Key optimizations include:

- Quantization to 4-bit for reduced memory footprint
- TVM graph-level optimizations
- Hardware-specific acceleration where available

### 2. Native Libraries

The integration relies on several native libraries:

- `libtvm_runtime.so` - TVM runtime library
- `libtvm.so` - Core TVM library
- `libmlc_llm.so` - MLC-LLM implementation
- `libmlc_llm_jni.so` - JNI bridge

### 3. Model Directory Structure

The model is stored in the app's assets directory with the following structure:

```
app/src/main/assets/models/gemma2_2b_it/
├── params/
│   ├── params_shard_0.bin
│   ├── params_shard_1.bin
│   └── ...
├── configs.json
├── model.bin
└── tokenizer.model
```

### 4. Runtime Flow

1. The model is loaded from assets into device storage
2. MLC-LLM engine is initialized with the model path
3. TVM runtime compiles and optimizes for the specific device
4. Inference requests are processed through the JNI bridge
5. Responses are streamed back to the UI

## Implementation Details

### Model Configuration

The model configuration is managed via JSON:

```json
{
  "model_type": "gemma",
  "quantization": "q4f16_0",
  "context_length": 8192,
  "vocab_size": 256000,
  "model_layer_count": 18
}
```

### Performance Considerations

- Model initialization may take several seconds
- First inference typically takes longer than subsequent ones
- Memory usage peaks during initialization
- Battery consumption varies by device capability

### Error Handling

The integration includes robust error handling for:

- Model loading failures
- Out-of-memory conditions
- Invalid inputs
- Timeout conditions

## Future Improvements

1. Support for more models (Phi-2, Llama, etc.)
2. Improved memory management
3. Multi-language support
4. More configuration options for inference
5. Enhanced quantization strategies

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