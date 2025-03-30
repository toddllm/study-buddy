#!/bin/bash
# Exit on error
set -e

echo "Creating a real Gemma model library..."

# Compile with NDK directly
ANDROID_NDK=$HOME/Library/Android/sdk/ndk/26.1.10909125
TOOLCHAIN=$ANDROID_NDK/toolchains/llvm/prebuilt/darwin-x86_64
TARGET=aarch64-linux-android
API=24

export CC=$TOOLCHAIN/bin/$TARGET$API-clang
export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++
export AR=$TOOLCHAIN/bin/llvm-ar
export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
export STRIP=$TOOLCHAIN/bin/llvm-strip

# Paths for include and library files
MLC_INCLUDE_DIR="app/src/main/cpp/include"
JNILIBS_DIR="app/src/main/jniLibs/arm64-v8a"

# Create output directories
mkdir -p build/gemma_lib
mkdir -p app/src/main/assets/models/gemma2_2b_it/lib

# Compile the real model library
echo "Compiling real Gemma model library..."
$CXX -std=c++17 -fPIC -shared \
  -I$ANDROID_NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include \
  -I$MLC_INCLUDE_DIR \
  -I$MLC_INCLUDE_DIR/tvm \
  -I$MLC_INCLUDE_DIR/tvm/runtime \
  -o build/gemma_lib/libgemma-2-2b-it-q4f16_1.so \
  app/src/main/cpp/mlc_create_chat_module.cpp \
  -L$JNILIBS_DIR -lmlc_llm -ltvm -ltvm_runtime \
  -llog -landroid

# Strip debug symbols to reduce size
$STRIP build/gemma_lib/libgemma-2-2b-it-q4f16_1.so

# Copy to assets folder and JNI libs folder
echo "Copying to assets folder and JNI libs folder..."
cp build/gemma_lib/libgemma-2-2b-it-q4f16_1.so app/src/main/assets/models/gemma2_2b_it/lib/
cp build/gemma_lib/libgemma-2-2b-it-q4f16_1.so $JNILIBS_DIR/

echo "Real Gemma model library built successfully"
echo "You can now build the APK with: ./gradlew assembleDebug" 