#!/bin/bash
set -e

# Get the ANDROID_NDK from environment or use the default path
if [ -z "$ANDROID_NDK" ]; then
    ANDROID_NDK="/Users/tdeshane/Library/Android/sdk/ndk/26.1.10909125"
fi

echo "Using Android NDK at: $ANDROID_NDK"

# Verify NDK exists
if [ ! -d "$ANDROID_NDK" ]; then
    echo "Error: Android NDK not found at $ANDROID_NDK"
    echo "Please install the NDK or set the ANDROID_NDK environment variable"
    exit 1
fi

# Set up compiler environment for Android ARM64
TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/darwin-x86_64"
TARGET="aarch64-linux-android24"
CC="$TOOLCHAIN/bin/$TARGET-clang"
CXX="$TOOLCHAIN/bin/$TARGET-clang++"
AR="$TOOLCHAIN/bin/llvm-ar"
STRIP="$TOOLCHAIN/bin/llvm-strip"

echo "Using C++ compiler: $CXX"

# Verify the compiler exists
if [ ! -f "$CXX" ]; then
    echo "Error: Android C++ compiler not found at $CXX"
    echo "The NDK might be corrupted or incomplete"
    exit 1
fi

# Set output directories
OUTPUT_DIR="android_lib"
mkdir -p $OUTPUT_DIR

# Source file
cat > gemma_model_lib.cpp << 'EOF'
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>
#include <iostream>

// Define export macros
#define EXPORT __attribute__((visibility("default")))

// Implementation class to store state
class ChatModule {
public:
    ChatModule(const char* path) : model_path(path) {}

    std::string Generate(const std::string& prompt) {
        const char* response = "I'm a mock version of Gemma 2 running on your device.";
        
        if (prompt.find("hello") != std::string::npos || prompt.find("hi") != std::string::npos) {
            response = "Hello! I'm Gemma 2, running on your device. How can I help you today?";
        } else if (prompt.find("what can you do") != std::string::npos || prompt.find("help") != std::string::npos) {
            response = "I can answer questions, provide information, and have conversations with you. I'm running completely on your device!";
        } else if (prompt.find("how are you") != std::string::npos) {
            response = "I'm functioning well, thanks for asking! I'm running efficiently on your device.";
        } else if (prompt.find("study") != std::string::npos || prompt.find("learn") != std::string::npos) {
            response = "I can help you study! Tell me what subject you're working on, and I'll try to assist you.";
        }
        
        return response;
    }

    void SetParameter(const std::string& key, float value) {
        params[key] = value;
    }

    void ResetChat() {}

private:
    std::string model_path;
    std::unordered_map<std::string, float> params;
};

// Global module instance
ChatModule* g_module = nullptr;

// External C API for the MLC-LLM runtime
extern "C" {
    // Create a chat module - this is the entry point
    EXPORT void* mlc_create_chat_module(const char* model_path) {
        // Create a new module if needed
        if (g_module == nullptr) {
            g_module = new ChatModule(model_path);
        }
        
        // Return a non-null pointer to indicate success
        return (void*)g_module;
    }

    // Generate a response
    EXPORT char* generate(const char* prompt) {
        std::string response;
        if (g_module) {
            response = g_module->Generate(prompt);
        } else {
            response = "Error: Model not initialized";
        }
        
        // Allocate memory for the response and copy it
        char* result = (char*)malloc(response.size() + 1);
        strcpy(result, response.c_str());
        return result;
    }

    // Reset the chat
    EXPORT void reset_chat() {
        if (g_module) {
            g_module->ResetChat();
        }
    }

    // Set a parameter
    EXPORT void set_parameter(const char* key, float value) {
        if (g_module) {
            g_module->SetParameter(key, value);
        }
    }
}
EOF

# Compile the library specifically for ARM64 Android
echo "Compiling for ARM64..."
$CXX -v -fPIC -shared -o $OUTPUT_DIR/libgemma-2-2b-it-q4f16_1.so gemma_model_lib.cpp \
    -std=c++17 \
    -march=armv8-a \
    -target aarch64-linux-android24 \
    -ffunction-sections \
    -fdata-sections \
    -Wl,--gc-sections

# Check if compilation was successful
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Strip the library to reduce size
$STRIP $OUTPUT_DIR/libgemma-2-2b-it-q4f16_1.so

# Check if the file is an ELF binary
file_info=$(file $OUTPUT_DIR/libgemma-2-2b-it-q4f16_1.so)
echo "File type: $file_info"

# Verify that it's an ARM64 ELF file, not a Mach-O file
if [[ $file_info == *"ELF 64-bit LSB shared object, ARM aarch64"* ]]; then
    echo "✅ Successfully built ARM64 ELF library"
else
    echo "❌ Failed to build ARM64 ELF library. Got: $file_info"
    echo "This appears to be a Mach-O file for macOS, not an Android ELF binary."
    exit 1
fi

# Create the jniLibs directory structure for proper APK packaging
JNILIBS_DIR="app/src/main/jniLibs/arm64-v8a"
mkdir -p $JNILIBS_DIR

# Copy the library to the jniLibs directory
cp $OUTPUT_DIR/libgemma-2-2b-it-q4f16_1.so $JNILIBS_DIR/

echo "Library copied to $JNILIBS_DIR/"

# Also copy to assets for fallback (and for compatibility)
ASSETS_DIR="app/src/main/assets/models/gemma2_2b_it/lib"
mkdir -p $ASSETS_DIR
cp $OUTPUT_DIR/libgemma-2-2b-it-q4f16_1.so $ASSETS_DIR/

echo "Library also copied to $ASSETS_DIR/"

# Print the permissions on the library files
ls -la $JNILIBS_DIR/libgemma-2-2b-it-q4f16_1.so
ls -la $ASSETS_DIR/libgemma-2-2b-it-q4f16_1.so

# Verify the architecture of the built library to be sure
echo "Checking architecture with readelf command:"
readelf -h $OUTPUT_DIR/libgemma-2-2b-it-q4f16_1.so || echo "readelf not available, skipping"

echo "Now rebuild the app with './gradlew installDebug'" 