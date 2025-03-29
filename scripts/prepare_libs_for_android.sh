#!/bin/bash
set -e

# Define source and destination directories
SOURCE_DIR="$PWD/mlc_build_android"
DEST_DIR="$PWD/app/src/main/jniLibs/arm64-v8a"

# Create the destination directory if it doesn't exist
mkdir -p "$DEST_DIR"

# Copy the necessary shared libraries
echo "Copying shared libraries to Android project..."
cp "$SOURCE_DIR/tvm/libtvm_runtime.so" "$DEST_DIR/"
cp "$SOURCE_DIR/tvm/libtvm.so" "$DEST_DIR/"
cp "$SOURCE_DIR/libmlc_llm.so" "$DEST_DIR/"
cp "$SOURCE_DIR/libmlc_llm_module.so" "$DEST_DIR/"

# Copy the STL shared library from NDK
if [ -z "$ANDROID_NDK_HOME" ]; then
    # Try to find Android NDK from common locations
    if [ -d "$HOME/Library/Android/sdk/ndk" ]; then
        # Find the latest NDK version
        ANDROID_NDK_HOME=$(find "$HOME/Library/Android/sdk/ndk" -maxdepth 1 -type d | sort -r | head -n 1)
    elif [ -d "/usr/local/lib/android/sdk/ndk" ]; then
        ANDROID_NDK_HOME=$(find "/usr/local/lib/android/sdk/ndk" -maxdepth 1 -type d | sort -r | head -n 1)
    else
        echo "Error: ANDROID_NDK_HOME is not set. Please set it to the path of your Android NDK."
        exit 1
    fi
fi

echo "Using Android NDK at: $ANDROID_NDK_HOME"
STL_SOURCE="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
cp "$STL_SOURCE" "$DEST_DIR/"

echo "MLC-LLM libraries copied successfully to $DEST_DIR"
echo "Now you can use these libraries in your Android application" 