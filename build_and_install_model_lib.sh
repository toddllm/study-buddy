#!/bin/bash
# Exit on error
set -e

echo "Building custom Gemma model library..."

# Clean and build the project
./gradlew clean
./gradlew assembleDebug

# Create output directory
mkdir -p app/src/main/assets/models/gemma2_2b_it/lib/

# Find the model library
MODEL_LIB=$(find app/build/ -name "libgemma-2-2b-it-q4f16_1.so" | head -n 1)

if [ -z "$MODEL_LIB" ]; then
    echo "ERROR: Could not find model library in build output"
    exit 1
fi

echo "Found model library at: $MODEL_LIB"

# Copy to assets folder
echo "Copying to assets folder..."
cp "$MODEL_LIB" app/src/main/assets/models/gemma2_2b_it/lib/

# Make it executable
chmod +x app/src/main/assets/models/gemma2_2b_it/lib/libgemma-2-2b-it-q4f16_1.so

echo "Model library installed successfully to assets"

# Building the final APK
echo "Building final APK with model library included..."
./gradlew assembleDebug

echo "Done. You can install the APK from app/build/outputs/apk/debug/" 