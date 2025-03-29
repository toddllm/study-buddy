# StudyBuddy - On-device AI Learning Assistant

StudyBuddy is an Android application that leverages on-device AI models to provide an intelligent learning assistant. The app uses MLC-LLM (Machine Learning Compilation for LLMs) to run Gemma 2B-IT models directly on Android devices without requiring internet connectivity.

## Features

- **On-device LLM**: Run Gemma 2B-IT models directly on your Android device
- **OCR Integration**: Scan study materials and extract text
- **Image Classification**: Identify objects and content in images
- **Chat Interface**: Interact with the AI through a conversational interface
- **Offline Operation**: Works without an internet connection

## Requirements

- Android Studio Arctic Fox or newer
- Android SDK 24+
- Android device with ARM64 architecture
- At least 4GB of RAM on device
- NDK 26.1.10909125
- CMake 3.22+

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

If you encounter issues, please check the [MLC_LLM_TROUBLESHOOTING.md](MLC_LLM_TROUBLESHOOTING.md) file for common solutions.

## License

This project is licensed under the Apache 2.0 License - see the LICENSE file for details.

## Acknowledgements

- [MLC-LLM](https://github.com/mlc-ai/mlc-llm) - Machine Learning Compilation for LLMs
- [TVM](https://github.com/apache/tvm) - An open-source machine learning compiler framework
- [Gemma](https://blog.google/technology/developers/gemma-open-models/) - Google's open LLM 