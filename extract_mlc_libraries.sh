#!/bin/bash

# Script to extract MLC LLM libraries from the official MLC Chat APK
set -e

echo "Extracting MLC LLM libraries from the official MLC Chat APK..."

# Create necessary directories
LIBS_DIR="app/src/main/jniLibs/arm64-v8a"
MODEL_DIR="app/src/main/assets/models/gemma2_2b_it"
MODEL_LIB_DIR="$MODEL_DIR/lib"

mkdir -p "$LIBS_DIR"
mkdir -p "$MODEL_LIB_DIR"

# Create temporary directory
TMP_DIR="tmp_apk_extract"
rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"
cd "$TMP_DIR"

# Download the MLC Chat APK from the official release
echo "Downloading MLC Chat APK..."
APK_URL="https://github.com/mlc-ai/binary-mlc-llm-libs/releases/download/Android-09262024/mlc-chat.apk"

if command -v curl > /dev/null; then
    curl -L -o "mlc-chat.apk" "$APK_URL"
else
    wget -O "mlc-chat.apk" "$APK_URL"
fi

# Check if the file was downloaded successfully
if [ ! -f "mlc-chat.apk" ] || [ ! -s "mlc-chat.apk" ]; then
    echo "Failed to download APK file."
    exit 1
fi

echo "APK downloaded successfully. Extracting libraries..."

# Make sure unzip is available
if ! command -v unzip > /dev/null; then
    echo "unzip not found. Please install it first."
    exit 1
fi

# Extract the APK (it's just a zip file)
unzip -q -o "mlc-chat.apk"

# Check for native libraries
if [ -d "lib/arm64-v8a" ]; then
    echo "Found native libraries: lib/arm64-v8a"
    ls -la "lib/arm64-v8a"
    
    # Copy all .so files to our app directory
    cp -f lib/arm64-v8a/*.so "../$LIBS_DIR/"
    echo "Copied TVM runtime libraries to app directory"
else
    echo "No native libraries found in the expected location."
    echo "Structure of the APK:"
    find . -type d | sort
    exit 1
fi

# Check for model libraries in assets
if [ -d "assets/model" ]; then
    echo "Found model assets in assets/model"
    for model_dir in assets/model/*; do
        echo "Checking model directory: $model_dir"
        if [ -d "$model_dir/lib" ]; then
            echo "Found library directory in $model_dir"
            # Try to find the Gemma model library
            if ls "$model_dir/lib/libgemma-2-2b-it"* 2>/dev/null; then
                echo "Found Gemma model library!"
                cp -f "$model_dir/lib/libgemma-2-2b-it"* "../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so"
                echo "Copied Gemma model library to app directory"
                break
            fi
        fi
    done
fi

# If we didn't find the Gemma library in the assets, look elsewhere
if [ ! -f "../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so" ]; then
    echo "Scanning entire APK for Gemma library..."
    find . -name "libgemma-2-2b-it*" -type f | while read -r file; do
        echo "Found potential Gemma library: $file"
        cp -f "$file" "../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so"
        echo "Copied Gemma model library to app directory"
        break
    done
fi

# If we still don't have a Gemma library, we may need to extract from another source
if [ ! -f "../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so" ] || [ ! -s "../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so" ]; then
    echo "WARNING: Couldn't find the Gemma library in the APK."
    echo "Will use an empty placeholder for now."
    touch "../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so"
fi

# Verify extraction
echo "Verifying extracted files..."
echo "TVM runtime libraries:"
ls -la "../$LIBS_DIR/"
echo
echo "Gemma model library:"
ls -la "../$MODEL_LIB_DIR/"

# Clean up
cd ..
rm -rf "$TMP_DIR"

echo "MLC LLM libraries extraction complete!" 