# StudyBuddy - On-device AI Learning Assistant

An Android app that helps students with study assistance using the Gemma 2 LLM.

## Features

- **On-device LLM**: Run Gemma 2B-IT models directly on your Android device
- **OCR Integration**: Scan study materials and extract text
- **Image Classification**: Identify objects and content in images
- **Chat Interface**: Interact with the AI through a conversational interface
- **Offline Operation**: Works without an internet connection
- **Real LLM Implementation**: Integration with the real Gemma 2 2B-IT language model
- **Suggested topics**: Quick access to common study areas
- **100% on-device processing for privacy**: No data sent to external servers

## Real LLM Implementation

This project implements the Google Gemma 2 2B-IT model in the StudyBuddy Android app using Rust for cross-compilation.

## What's Fixed

1. **Updated Build Process**: Changed from using C/gcc to Rust for a more reliable cross-compilation workflow
2. **Better Symbol Management**: Using Rust's FFI capabilities to properly export the required symbols
3. **Fixed Verification**: Updated verification script to properly check both C and Rust-built libraries
4. **Improved Testing**: Enhanced the testing script with better error handling and device compatibility

## How to Build

```shell
./build_real_implementation.sh
```

This script will:
1. Check for the required tools (Rust)
2. Download model files if needed
3. Create a Rust project for the native library
4. Cross-compile the library for Android
5. Build the Android application

## How to Verify

```shell
./verify_real_implementation.sh
```

This will verify that:
1. No mock implementations are present in the code
2. The shared library contains all required symbols
3. Model files are present and complete

## How to Test

Connect an Android device and run:

```shell
./test_gemma_implementation.sh
```

This will:
1. Install the app on the connected device
2. Launch the app and check for proper initialization
3. Test a simple query to verify functionality

## Using the Optimized Implementation

After testing the base implementation, you can run:

```shell
./optimize_gemma_model.sh
```

This will create optimized configurations and the `ModelOptimizer` class that automatically adjusts parameters based on device capabilities.

## Requirements

- Android Studio Iguana or later
- Android SDK 24+
- NDK 26.1.10909125 or compatible
- At least 2GB of free space (for model files)
- Rust (install with `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`)
- Android device with at least 4GB RAM for testing

## Setup and Installation

1. Clone the repository:
   ```
   git clone https://github.com/toddllm/study-buddy.git
   ```

2. Open the project in Android Studio

3. Download the Gemma 2B-IT model:
   ```
   ./download_gemma_model.sh
   ```
   Note: You need to have a Google Cloud account and access to the Gemma model.

4. Build the application:
   ```
   ./gradlew assembleDebug
   ```

5. Install on your device:
   ```
   ./gradlew installDebug
   ```

## Project Structure

- `/app/src/main/java/com/example/studybuddy/`: Main application code
  - `/ml/`: Machine learning implementation
  - `/ui/`: User interface components
  - `/utils/`: Utility classes
- `/app/src/main/cpp/`: Native code for MLC-LLM integration
- `/app/src/main/assets/`: Model configuration files and resources

## JNI Integration

This project uses JNI (Java Native Interface) to bridge between Kotlin/Java and the native C++ code that runs the MLC-LLM model. Key files:

- `app/src/main/java/com/example/studybuddy/ml/MlcLlmBridge.kt`: JNI bridge in Kotlin
- `app/src/main/cpp/mlc_jni/mlc_llm_jni.cpp`: Native implementation
- `app/src/main/cpp/CMakeLists.txt`: Native build configuration

For more details on the JNI integration, see [JNI_INTEGRATION.md](JNI_INTEGRATION.md).

## MLC-LLM Integration

The app integrates [MLC-LLM](https://github.com/mlc-ai/mlc-llm), a framework for deploying and running LLMs across different platforms. Key components:

- TVM runtime for optimized execution
- Custom JNI bridges for Kotlin/C++ communication
- Model quantization (Q4F16) for efficient execution

For more details on the MLC-LLM integration, see [MLC_LLM_INTEGRATION.md](MLC_LLM_INTEGRATION.md).

## Troubleshooting

If you encounter issues:
1. Check that Android NDK is properly installed and available
2. Ensure Rust is installed with the Android target (`rustup target add aarch64-linux-android`)
3. Verify that the device is properly connected with USB debugging enabled

## License

This project is licensed under the terms of the MIT license.

## Acknowledgements

- [MLC-LLM](https://github.com/mlc-ai/mlc-llm) - Machine Learning Compilation for LLMs
- [TVM](https://github.com/apache/tvm) - An open-source machine learning compiler framework
- [Gemma](https://blog.google/technology/developers/gemma-open-models/) - Google's open LLM 