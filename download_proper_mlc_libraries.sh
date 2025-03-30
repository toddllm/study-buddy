#!/bin/bash

# This script downloads the properly compiled MLC-LLM libraries
# that include the necessary chat module entry points

# Exit on error by default, but report it
trap 'echo "Error occurred at line $LINENO. Command: $BASH_COMMAND"' ERR

echo "Downloading properly compiled MLC-LLM libraries..."

# Create necessary directories
LIBS_DIR="app/src/main/jniLibs/arm64-v8a"
MODEL_LIB_DIR="app/src/main/assets/models/gemma2_2b_it/lib"
echo "Creating directories: $LIBS_DIR and $MODEL_LIB_DIR"
mkdir -p "$LIBS_DIR" || { echo "Failed to create $LIBS_DIR"; exit 1; }
mkdir -p "$MODEL_LIB_DIR" || { echo "Failed to create $MODEL_LIB_DIR"; exit 1; }

# Download URLs for pre-compiled libraries
MLC_LLM_APK_URL="https://github.com/mlc-ai/binary-mlc-llm-libs/releases/download/Android-09262024/mlc-chat.apk"

# We'll use the TVM runtime library to create our own model library
echo "Downloading from:"
echo " - MLC-LLM APK: $MLC_LLM_APK_URL"

# Create a temp directory for downloads
TMP_DIR="tmp_libraries"
echo "Creating temp directory: $TMP_DIR"
rm -rf "$TMP_DIR" # Clean up any previous attempt
mkdir -p "$TMP_DIR" || { echo "Failed to create temp directory"; exit 1; }
cd "$TMP_DIR" || { echo "Failed to enter temp directory"; exit 1; }

# Check if unzip is available
if ! command -v unzip > /dev/null; then
    echo "unzip command not found. Please install it first. For example:"
    echo "  - On macOS: brew install unzip"
    echo "  - On Ubuntu: sudo apt-get install unzip"
    exit 1
fi

# Function to handle downloading with retries
download_file() {
    local url=$1
    local output=$2
    local max_retries=3
    local retry=0
    local success=false
    
    echo "Downloading $url to $output"
    
    while [ $retry -lt $max_retries ] && [ "$success" = "false" ]; do
        if command -v curl > /dev/null; then
            echo "Using curl for download..."
            if curl -L --fail --silent --show-error --output "$output" "$url"; then
                success=true
            else
                echo "curl download failed (attempt $((retry+1))/$max_retries)"
            fi
        elif command -v wget > /dev/null; then
            echo "Using wget for download..."
            if wget -q -O "$output" "$url"; then
                success=true
            else
                echo "wget download failed (attempt $((retry+1))/$max_retries)"
            fi
        else
            echo "Neither curl nor wget found. Cannot download."
            return 1
        fi
        
        if [ "$success" = "false" ]; then
            retry=$((retry+1))
            [ $retry -lt $max_retries ] && echo "Retrying in 2 seconds..." && sleep 2
        fi
    done
    
    if [ "$success" = "true" ]; then
        echo "Download successful: $output ($(du -h "$output" | cut -f1) bytes)"
        return 0
    else
        echo "Failed to download after $max_retries attempts"
        return 1
    fi
}

# Download the MLC-LLM core libraries
echo "Downloading MLC-LLM core libraries..."
download_file "$MLC_LLM_APK_URL" "mlc-llm.apk" || { echo "Failed to download MLC-LLM libraries"; exit 1; }

# Extract the libraries
echo "Extracting libraries..."
rm -rf extracted_apk # Clean up any previous extraction
if ! unzip -q -o mlc-llm.apk -d extracted_apk; then
    echo "Failed to extract archive. Contents may be corrupted."
    exit 1
fi

# Check what was extracted
echo "Extracted files:"
ls -la extracted_apk

# Copy the libraries to the app's jniLibs directory
echo "Copying libraries to app..."

# Android APK stores native libraries in lib/[architecture]/ directory
if [ -d "extracted_apk/lib/arm64-v8a" ]; then
    echo "Found native libraries at lib/arm64-v8a:"
    ls -la extracted_apk/lib/arm64-v8a/
    
    # Copy all .so files
    for file in extracted_apk/lib/arm64-v8a/*.so; do
        if [ -f "$file" ]; then
            echo "Copying $file to ../$LIBS_DIR/"
            cp -f "$file" "../$LIBS_DIR/" || echo "Failed to copy $file"
        fi
    done
elif [ -d "extracted_apk/lib" ]; then
    # Sometimes libraries might be just in the lib directory
    echo "Checking lib directory:"
    ls -la extracted_apk/lib/
    
    # Look for any architecture directories
    for arch_dir in extracted_apk/lib/*; do
        if [ -d "$arch_dir" ]; then
            echo "Found directory: $arch_dir"
            for file in "$arch_dir"/*.so; do
                if [ -f "$file" ]; then
                    echo "Copying $file to ../$LIBS_DIR/"
                    cp -f "$file" "../$LIBS_DIR/" || echo "Failed to copy $file"
                fi
            done
        fi
    done
else
    echo "No lib directory found in APK. Looking for any .so files:"
    # Try to find any .so files anywhere in the extracted APK
    find extracted_apk -name "*.so" | while read -r file; do
        echo "Found .so file: $file"
        if [ -f "$file" ]; then
            echo "Copying $file to ../$LIBS_DIR/"
            cp -f "$file" "../$LIBS_DIR/" || echo "Failed to copy $file"
        fi
    done
fi

# Create an empty model library file (will be replaced with a real model in a separate step)
echo "Creating placeholder model library file"
touch "../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so"
echo "NOTE: You'll need to download the actual model library separately."

# Check what was copied to the target directories
echo "Contents of $LIBS_DIR:"
ls -la "../$LIBS_DIR/"

echo "Contents of $MODEL_LIB_DIR:"
ls -la "../$MODEL_LIB_DIR/"

# Cleanup
cd ..
rm -rf "$TMP_DIR"

echo "Library setup complete!"
echo "Please rebuild the app to use the new libraries." 