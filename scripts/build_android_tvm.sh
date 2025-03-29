#!/bin/bash
set -e

# Check if Android NDK path is set or get it from the environment
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

# Install Rust target for Android
echo "Installing Rust target for Android..."
rustup target add aarch64-linux-android

# Set up build directory
BUILD_DIR="$PWD/mlc_build_android"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Configure CMake for Android
cmake -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared \
    -DUSE_OPENCL=OFF \
    -DUSE_VULKAN=OFF \
    -DUSE_CUDA=OFF \
    -DUSE_METAL=OFF \
    -DUSE_OPENCL_ENABLE_HOST_PTR=OFF \
    -DUSE_OPENCL_EXTN_QCOM=OFF \
    -DUSE_RPC=ON \
    "$PWD/../mlc_llm_temp/mlc-llm"

# Build
cmake --build . -j$(nproc)

echo "Android build completed successfully!"
echo "Output libraries can be found in $BUILD_DIR" 