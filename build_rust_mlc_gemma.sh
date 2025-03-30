#!/bin/bash
set -e

# Prerequisite check
if ! command -v rustc &> /dev/null; then
    echo "Error: Rust toolchain not found. Please install Rust first using:"
    echo "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
    exit 1
fi

if ! command -v cargo &> /dev/null; then
    echo "Error: Cargo not found. Please install Rust properly."
    exit 1
fi

if [[ -z "${ANDROID_NDK}" ]]; then
    # Try to automatically locate the NDK
    if [[ -d "${HOME}/Library/Android/sdk/ndk" ]]; then
        # Find the latest NDK version
        LATEST_NDK=$(find "${HOME}/Library/Android/sdk/ndk" -maxdepth 1 -type d | sort -r | head -n 1)
        if [[ ! -z "${LATEST_NDK}" ]]; then
            export ANDROID_NDK="${LATEST_NDK}"
            echo "Auto-detected Android NDK at: ${ANDROID_NDK}"
        fi
    else
        echo "Error: ANDROID_NDK environment variable not set and NDK not found in standard location."
        echo "Please set ANDROID_NDK to point to your Android NDK installation."
        exit 1
    fi
fi

# Set up Rust target for Android ARM64
echo "Adding Android ARM64 target to Rust toolchain..."
rustup target add aarch64-linux-android

# Set up Android CC linker
export TVM_NDK_CC="${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android24-clang"
echo "Using Android NDK compiler: ${TVM_NDK_CC}"

if [[ ! -f "${TVM_NDK_CC}" ]]; then
    echo "Error: Android NDK compiler not found at: ${TVM_NDK_CC}"
    echo "Please check your NDK installation."
    exit 1
fi

# Create a directory for our work
mkdir -p mlc_build
cd mlc_build

# Clone the MLC-LLM repository if not already done
if [[ ! -d "mlc-llm" ]]; then
    echo "Cloning MLC-LLM repository..."
    git clone --recursive https://github.com/mlc-ai/mlc-llm.git
    cd mlc-llm
else
    echo "MLC-LLM repository already exists, updating..."
    cd mlc-llm
    git pull
    git submodule update --init --recursive
fi

# Set up cargo config for Android
mkdir -p .cargo
cat > .cargo/config <<EOF
[target.aarch64-linux-android]
linker = "${TVM_NDK_CC}"
ar = "${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"
EOF

# Create a minimal Cargo.toml for the tokenizer binding
cat > Cargo.toml <<EOF
[package]
name = "gemma_tokenizer"
version = "0.1.0"
edition = "2021"

[dependencies]
tokenizers = "0.13.3"
serde_json = "1.0"
serde = { version = "1.0", features = ["derive"] }

[lib]
name = "gemma_tokenizer"
crate-type = ["staticlib", "cdylib"]
EOF

# Create Rust source for tokenizer bindings
mkdir -p src
cat > src/lib.rs <<EOF
use std::ffi::{c_char, CStr, CString};
use std::ptr;
use tokenizers::tokenizer::{Tokenizer};

#[no_mangle]
pub extern "C" fn load_tokenizer(path: *const c_char) -> *mut Tokenizer {
    let c_str = unsafe {
        if path.is_null() {
            return ptr::null_mut();
        }
        CStr::from_ptr(path)
    };

    let path_str = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return ptr::null_mut()
    };

    match Tokenizer::from_file(path_str) {
        Ok(tokenizer) => Box::into_raw(Box::new(tokenizer)),
        Err(_) => ptr::null_mut()
    }
}

#[no_mangle]
pub extern "C" fn tokenize(tokenizer_ptr: *mut Tokenizer, text: *const c_char) -> *mut c_char {
    let tokenizer = unsafe {
        if tokenizer_ptr.is_null() {
            return ptr::null_mut();
        }
        &*tokenizer_ptr
    };

    let c_str = unsafe {
        if text.is_null() {
            return ptr::null_mut();
        }
        CStr::from_ptr(text)
    };

    let text_str = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return ptr::null_mut()
    };

    match tokenizer.encode(text_str, false) {
        Ok(encoding) => {
            // Convert to JSON string
            let ids = encoding.get_ids();
            let json = serde_json::to_string(&ids).unwrap_or_default();
            
            // Convert to C string
            match CString::new(json) {
                Ok(c_string) => c_string.into_raw(),
                Err(_) => ptr::null_mut()
            }
        },
        Err(_) => ptr::null_mut()
    }
}

#[no_mangle]
pub extern "C" fn free_tokenizer(tokenizer_ptr: *mut Tokenizer) {
    if !tokenizer_ptr.is_null() {
        unsafe {
            let _ = Box::from_raw(tokenizer_ptr);
        }
    }
}

#[no_mangle]
pub extern "C" fn free_string(string_ptr: *mut c_char) {
    if !string_ptr.is_null() {
        unsafe {
            let _ = CString::from_raw(string_ptr);
        }
    }
}
EOF

# Create C wrapper for Rust tokenizer
cat > gemma_model_wrapper.cpp <<EOF
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>

// Define export macros
#define EXPORT __attribute__((visibility("default")))

// Import Rust tokenizer functions
extern "C" {
    void* load_tokenizer(const char* path);
    char* tokenize(void* tokenizer_ptr, const char* text);
    void free_tokenizer(void* tokenizer_ptr);
    void free_string(char* string_ptr);
}

// Implementation class to store state
class ChatModule {
public:
    ChatModule(const char* path) : model_path(path) {
        // Try to load the tokenizer
        tokenizer = load_tokenizer((std::string(path) + "/tokenizer.model").c_str());
    }

    ~ChatModule() {
        if (tokenizer) {
            free_tokenizer(tokenizer);
            tokenizer = nullptr;
        }
    }

    std::string Generate(const std::string& prompt) {
        // Generate token IDs
        std::string token_ids = "[]";
        if (tokenizer) {
            char* ids_json = tokenize(tokenizer, prompt.c_str());
            if (ids_json) {
                token_ids = ids_json;
                free_string(ids_json);
            }
        }
        
        // For now, return a mock response with token IDs included
        std::string response = "I'm a mock version of Gemma 2 running on your device.\n";
        response += "Tokenized input: " + token_ids + "\n";
        
        if (prompt.find("hello") != std::string::npos || prompt.find("hi") != std::string::npos) {
            response += "Hello! I'm Gemma 2, running on your device. How can I help you today?";
        } else if (prompt.find("what can you do") != std::string::npos || prompt.find("help") != std::string::npos) {
            response += "I can answer questions, provide information, and have conversations with you. I'm running completely on your device!";
        } else if (prompt.find("how are you") != std::string::npos) {
            response += "I'm functioning well, thanks for asking! I'm running efficiently on your device.";
        } else if (prompt.find("study") != std::string::npos || prompt.find("learn") != std::string::npos) {
            response += "I can help you study! Tell me what subject you're working on, and I'll try to assist you.";
        }
        
        return response;
    }

    void SetParameter(const std::string& key, float value) {
        params[key] = value;
    }

    void ResetChat() {
        // Reset chat state
    }

private:
    std::string model_path;
    void* tokenizer = nullptr;
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

# Build the Rust tokenizer
echo "Building Rust tokenizer for Android..."
cargo build --target aarch64-linux-android --release

# Compile the C++ wrapper with the Rust tokenizer
echo "Compiling C++ wrapper..."
TOOLCHAIN="${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64"
TARGET="aarch64-linux-android24"
CXX="${TOOLCHAIN}/bin/${TARGET}-clang++"
AR="${TOOLCHAIN}/bin/llvm-ar"
STRIP="${TOOLCHAIN}/bin/llvm-strip"

RUST_LIB="target/aarch64-linux-android/release/libgemma_tokenizer.so"
OUTPUT_DIR="../../android_lib"
mkdir -p ${OUTPUT_DIR}

$CXX -v -fPIC -shared -o ${OUTPUT_DIR}/libgemma-2-2b-it-q4f16_1.so gemma_model_wrapper.cpp \
    ${RUST_LIB} \
    -std=c++17 \
    -march=armv8-a \
    -target aarch64-linux-android24 \
    -ffunction-sections \
    -fdata-sections \
    -Wl,--gc-sections

# Strip the library to reduce size
$STRIP ${OUTPUT_DIR}/libgemma-2-2b-it-q4f16_1.so

# Check if the file is an ELF binary
file_info=$(file ${OUTPUT_DIR}/libgemma-2-2b-it-q4f16_1.so)
echo "File type: $file_info"

# Verify that it's an ARM64 ELF file, not a Mach-O file
if [[ $file_info == *"ELF 64-bit LSB shared object, ARM aarch64"* ]]; then
    echo "✅ Successfully built ARM64 ELF library with Rust tokenizer"
else
    echo "❌ Failed to build ARM64 ELF library. Got: $file_info"
    echo "This appears to be a Mach-O file for macOS, not an Android ELF binary."
    exit 1
fi

# Return to the main directory
cd ../../

# Create the jniLibs directory structure for proper APK packaging
JNILIBS_DIR="app/src/main/jniLibs/arm64-v8a"
mkdir -p $JNILIBS_DIR

# Copy the library to the jniLibs directory
cp android_lib/libgemma-2-2b-it-q4f16_1.so $JNILIBS_DIR/

echo "Library copied to $JNILIBS_DIR/"

# Also copy to assets for fallback (and for compatibility)
ASSETS_DIR="app/src/main/assets/models/gemma2_2b_it/lib"
mkdir -p $ASSETS_DIR
cp android_lib/libgemma-2-2b-it-q4f16_1.so $ASSETS_DIR/

echo "Library also copied to $ASSETS_DIR/"

# Print the permissions on the library files
ls -la $JNILIBS_DIR/libgemma-2-2b-it-q4f16_1.so
ls -la $ASSETS_DIR/libgemma-2-2b-it-q4f16_1.so

echo "Now rebuild the app with './gradlew installDebug'" 