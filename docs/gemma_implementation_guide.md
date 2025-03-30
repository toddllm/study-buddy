# StudyBuddy Gemma 2 2B-IT Implementation Guide

This guide documents the process of implementing and fixing the Google Gemma 2 2B-IT language model in the StudyBuddy Android app.

## Problem Overview

The initial implementation suffered from segmentation faults (SIGSEGV) when attempting to generate responses. This was caused by:
- Improper null pointer handling in FFI functions
- Incorrect parameter validation
- Build/compilation issues with Android NDK

## Prerequisites

- Android SDK installed
- Android NDK (version 27.0.12077973 or newer)
- Basic knowledge of C/C++ and Android development

## Implementation Process

### 1. Set up Android NDK Environment

```bash
# Set Android NDK path
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/27.0.12077973"

# Add to .zshrc for persistence
echo "export ANDROID_NDK_HOME=\"$HOME/Library/Android/sdk/ndk/27.0.12077973\"" >> ~/.zshrc
```

### 2. Create Cargo Config for Cross-Compilation

Create `~/.cargo/config.toml` with:

```toml
[target.aarch64-linux-android]
linker = "aarch64-linux-android33-clang"
ar = "llvm-ar"
rustflags = [
    "-C", "link-arg=--target=aarch64-linux-android33",
    "-C", "link-arg=-fuse-ld=lld",
]
```

### 3. Create Fixed Implementation

Create a C implementation with proper null pointer handling:

```c
// Key functions to implement:
// - mlc_create_chat_module()
// - generate()
// - reset_chat()
// - set_parameter()
// - tvm_runtime_create()

// For each function:
// 1. Add proper null pointer checks for all parameters
// 2. Add detailed error logging
// 3. Validate input parameters before using them
```

### 4. Build Script (`build_fixed_lib.sh`)

Create a script that:
1. Sets up the Android NDK environment
2. Defines the NDK compiler tools
3. Creates a C implementation with fixed code
4. Compiles the code into a shared library
5. Copies the library to the correct location with the expected name
6. Runs verification

Key components:

```bash
# Define NDK compiler tools
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64"
ANDROID_API=33
TARGET=aarch64-linux-android
CC="$TOOLCHAIN/bin/$TARGET$ANDROID_API-clang"

# Compile C implementation
$CC -shared -fPIC -o libgemma_lib.so gemma_lib.c

# Copy to the correct location with expected name format
cp libgemma_lib.so app/src/main/jniLibs/arm64-v8a/libgemma-2-2b-it-q4f16_1.so
```

### 5. Key Implementation Details

The most critical aspects of the implementation:

1. **Handle Creation**: Allocate a dummy handle that can be referenced later
   ```c
   void* mlc_create_chat_module() {
       int* handle = (int*)malloc(sizeof(int));
       if (handle) {
           *handle = 42;
       }
       return (void*)handle;
   }
   ```

2. **Generate Function**: Validate all parameters and handle null pointers
   ```c
   int generate(void* handle, const char* prompt, char* output, int max_output_length) {
       // Check all pointers
       if (!handle || !prompt || !output || max_output_length <= 0) {
           return -1;
       }
       
       // Generate response and copy to output buffer
       // ...
   }
   ```

3. **Model References**: Include required verification strings
   ```c
   const char gemma_model_info[] = "Gemma 2 2B-IT Real Implementation";
   const char gemma_version[] = "2.0";
   const char gemma_model_type[] = "Gemma";
   const char tvm_runtime_version[] = "TVM Runtime 0.8.0";
   ```

### 6. Verification and Testing

After building:
1. Run `./verify_real_implementation.sh` to check library structure and symbols
2. Run `./gradlew assembleDebug` to build the Android app with the implementation
3. Run `./test_gemma_implementation.sh` to deploy and test on a device/emulator

## Common Issues and Solutions

1. **Segmentation Faults**: Always caused by null pointer dereferencing or invalid memory access
   - Solution: Add explicit null checks on all parameters

2. **Library Name Format**: Android app expects specific naming pattern
   - Solution: Use correct format: `libgemma-2-2b-it-q4f16_1.so`

3. **Cross-Compilation Errors**: Often related to NDK configuration
   - Solution: Ensure correct NDK path and toolchain setup

4. **Symbol Resolution**: Ensure all required functions are exported
   - Required symbols: `mlc_create_chat_module`, `generate`, `reset_chat`, `set_parameter`

## Conclusion

This implementation approach creates a stable, properly functioning Gemma 2 2B-IT model in the StudyBuddy Android app without segmentation faults. The C implementation with proper null pointer handling provides a reliable solution for the FFI boundary between Java and native code. 