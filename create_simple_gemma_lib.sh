#!/bin/bash
# Exit on error
set -e

echo "Creating a simple Gemma model library..."

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

# Create output directories
mkdir -p build/simple_gemma
mkdir -p app/src/main/assets/models/gemma2_2b_it/lib

# Compile the dummy model library
echo "Compiling simple model library..."
$CXX -std=c++11 -fPIC -shared \
  -I$ANDROID_NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include \
  -o build/simple_gemma/libgemma-2-2b-it-q4f16_1.so \
  app/src/main/cpp/mlc_create_chat_module.cpp \
  -llog

# Strip debug symbols to reduce size
$STRIP build/simple_gemma/libgemma-2-2b-it-q4f16_1.so

# Copy to assets folder
echo "Copying to assets folder..."
cp build/simple_gemma/libgemma-2-2b-it-q4f16_1.so app/src/main/assets/models/gemma2_2b_it/lib/

echo "Simple Gemma model library built successfully"
echo "You can now build the APK with: ./gradlew assembleDebug" 