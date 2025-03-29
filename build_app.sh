#!/bin/bash
set -e

echo "Building StudyBuddy Android App with MLC-LLM Integration"

# Step 1: Make sure the include directory is properly set up
INCLUDE_DIR="app/src/main/cpp/include"
TVM_HEADERS_SRC="/Users/tdeshane/AndroidStudioProjects/StudyBuddy/mlc_llm_temp/mlc-llm/3rdparty/tvm/include"
DLPACK_HEADERS_SRC="/Users/tdeshane/AndroidStudioProjects/StudyBuddy/mlc_llm_temp/mlc-llm/3rdparty/tvm/3rdparty/dlpack/include"
DMLC_HEADERS_SRC="/Users/tdeshane/AndroidStudioProjects/StudyBuddy/mlc_llm_temp/mlc-llm/3rdparty/tvm/3rdparty/dmlc-core/include"

echo "Ensuring TVM headers are available in $INCLUDE_DIR"
mkdir -p "$INCLUDE_DIR"
if [ ! -d "$INCLUDE_DIR/tvm" ]; then
    echo "Copying TVM headers from $TVM_HEADERS_SRC"
    cp -r "$TVM_HEADERS_SRC/tvm" "$INCLUDE_DIR/"
fi

echo "Ensuring dlpack headers are available in $INCLUDE_DIR"
if [ ! -d "$INCLUDE_DIR/dlpack" ]; then
    echo "Copying dlpack headers from $DLPACK_HEADERS_SRC"
    cp -r "$DLPACK_HEADERS_SRC/dlpack" "$INCLUDE_DIR/"
fi

echo "Ensuring dmlc headers are available in $INCLUDE_DIR"
if [ ! -d "$INCLUDE_DIR/dmlc" ]; then
    echo "Copying dmlc headers from $DMLC_HEADERS_SRC"
    cp -r "$DMLC_HEADERS_SRC/dmlc" "$INCLUDE_DIR/"
fi

# Step 2: Build the app using Gradle
echo "Building Android app with Gradle..."
./gradlew build

echo "Build completed successfully!" 