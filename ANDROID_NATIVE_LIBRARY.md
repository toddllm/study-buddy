# Android Native Library for MLC-LLM Integration

## Overview
This document describes the solution developed for creating an Android-compatible native library that implements the MLC-LLM interface required by the StudyBuddy app. The library provides a simplified mock implementation of the Gemma 2 language model, focusing on proper JNI integration rather than actual ML inference.

## Key Components

### 1. Native Library (`libgemma-2-2b-it-q4f16_1.so`)
- Implements the required JNI functions for the MLC-LLM interface
- Properly packaged as an ARM64 ELF binary for Android
- Includes basic response generation functionality

### 2. Kotlin Interface Classes
- `SimpleMlcModel`: Direct interface to the native library using JNI
- `MlcLanguageModel`: Wrapper class maintaining backward compatibility with existing code

### 3. Build Scripts
- `build_android_model_lib.sh`: Compiles the C++ code into an Android-compatible shared library
- Handles proper ARM64 target configuration and library placement

## Implementation Details

### JNI Function Naming Convention
The JNI interface requires specific function naming conventions. For example, a native method in 
`com.example.studybuddy.ml.SimpleMlcModel` named `mlc_create_chat_module` must have a C++ implementation
named `Java_com_example_studybuddy_ml_SimpleMlcModel_mlc_1create_1chat_1module`.

### C++ Implementation
The implementation provides simple mock responses based on keywords in the input prompt.
Key functions include:
- `mlc_create_chat_module`: Initializes the model with a given path
- `generate`: Produces responses based on input prompts
- `reset_chat`: Resets the chat state
- `set_parameter`: Sets model parameters like temperature and top-p

### Kotlin Side
The Kotlin side loads the native library and calls the JNI functions:
```kotlin
companion object {
    init {
        System.loadLibrary("gemma-2-2b-it-q4f16_1")
    }
}

private external fun mlc_create_chat_module(modelPath: String): Any
private external fun generate(prompt: String): String
private external fun reset_chat()
private external fun set_parameter(key: String, value: Float)
```

## Compilation Process
1. The C++ code is compiled using the Android NDK's Clang compiler targeting ARM64 architecture
2. The resulting library is verified to be an ELF file compatible with Android
3. The library is placed in both the jniLibs directory and assets directory for Android packaging

## Usage in StudyBuddy
The library is integrated with StudyBuddy's existing language model architecture, allowing it to:
1. Initialize with a model path
2. Generate responses to user prompts
3. Modify generation parameters like temperature
4. Reset chat history

## Troubleshooting
Common issues include:
- JNI function name mismatches: Ensure C++ function names follow JNI conventions
- Wrong architecture: The library must be compiled specifically for ARM64 Android
- Loading errors: Verify the library is properly integrated in the Android app

## Future Improvements
- Integrate with a real MLC-LLM library rather than a mock implementation
- Implement Rust-based cross-compilation for HuggingFace tokenizers
- Add support for streaming responses 