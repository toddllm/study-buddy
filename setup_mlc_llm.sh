#!/bin/bash

# Setup script for MLC-LLM with Android
# This script downloads and builds MLC-LLM for your Android project

# Exit on error
set -e

echo "==================================================================="
echo "   MLC-LLM Android Setup Script for StudyBuddy                     "
echo "==================================================================="

# Check for Android NDK
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

# 1. Clone MLC-LLM repository if it doesn't exist
if [ ! -d "mlc_llm_temp" ]; then
    echo "Cloning MLC-LLM repository..."
    git clone --recursive https://github.com/mlc-ai/mlc-llm.git mlc_llm_temp
else
    echo "MLC-LLM repository already exists, skipping clone..."
fi

# 2. Build MLC-LLM for Android
echo "Building MLC-LLM for Android..."
chmod +x scripts/build_android_tvm.sh
./scripts/build_android_tvm.sh

# 3. Copy libraries to the Android project
echo "Copying libraries to Android project..."
chmod +x scripts/prepare_libs_for_android.sh
./scripts/prepare_libs_for_android.sh

# 4. Set up include directories for the JNI code
echo "Setting up include directories for JNI..."
INCLUDE_DIR="app/src/main/cpp/include"
TVM_HEADERS_SRC="$PWD/mlc_llm_temp/mlc-llm/3rdparty/tvm/include"
DLPACK_HEADERS_SRC="$PWD/mlc_llm_temp/mlc-llm/3rdparty/tvm/3rdparty/dlpack/include"
DMLC_HEADERS_SRC="$PWD/mlc_llm_temp/mlc-llm/3rdparty/tvm/3rdparty/dmlc-core/include"

mkdir -p "$INCLUDE_DIR"

echo "Copying TVM headers..."
if [ ! -d "$INCLUDE_DIR/tvm" ]; then
    cp -r "$TVM_HEADERS_SRC/tvm" "$INCLUDE_DIR/"
fi

echo "Copying dlpack headers..."
if [ ! -d "$INCLUDE_DIR/dlpack" ]; then
    cp -r "$DLPACK_HEADERS_SRC/dlpack" "$INCLUDE_DIR/"
fi

echo "Copying dmlc headers..."
if [ ! -d "$INCLUDE_DIR/dmlc" ]; then
    cp -r "$DMLC_HEADERS_SRC/dmlc" "$INCLUDE_DIR/"
fi

# 5. Create stub Java classes for TVM API if they don't exist
echo "Setting up TVM Java API stubs..."
JAVA_DIR="app/src/main/java/org/apache/tvm"
mkdir -p "$JAVA_DIR"

# Function to create Java file if it doesn't exist
create_java_file() {
    local file="$JAVA_DIR/$1.java"
    if [ ! -f "$file" ]; then
        echo "Creating $file..."
        cp "stub_classes/$1.java" "$file" 2>/dev/null || echo "// Auto-generated stub" > "$file"
    else
        echo "File $file already exists, skipping..."
    fi
}

# Create stub classes
create_java_file "Device"
create_java_file "Function"
create_java_file "Module"
create_java_file "TVMValue"

# 6. Build the Android app
echo "Building Android app..."
chmod +x build_app.sh
./build_app.sh

echo "==================================================================="
echo "Setup completed successfully!"
echo "==================================================================="
echo "You can now open the project in Android Studio and run it."
echo "See JNI_INTEGRATION.md for more details on the integration."
echo "===================================================================" 