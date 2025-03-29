#!/bin/bash
set -e

echo "Testing compilation of tvm_bridge.cpp"

# Paths
SRC_FILE="app/src/main/cpp/tvm_bridge.cpp"
INCLUDE_DIR="app/src/main/cpp/include"
NDK_DIR="/Users/tdeshane/Library/Android/sdk/ndk/27.0.12077973"
CLANG="${NDK_DIR}/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++"

# Ensure headers exist
if [ ! -d "$INCLUDE_DIR/tvm/runtime" ]; then
    echo "Error: TVM headers not found in $INCLUDE_DIR/tvm/runtime"
    echo "Please run ./build_app.sh first to set up the headers"
    exit 1
fi

# Run the compiler directly to test compilation
echo "Running clang++ compiler..."
$CLANG --target=aarch64-none-linux-android24 \
    --sysroot=${NDK_DIR}/toolchains/llvm/prebuilt/darwin-x86_64/sysroot \
    -I${INCLUDE_DIR} \
    -c ${SRC_FILE} -o /tmp/tvm_bridge.o \
    -std=gnu++17 -fPIC

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    rm /tmp/tvm_bridge.o
else
    echo "❌ Compilation failed!"
    exit 1
fi 