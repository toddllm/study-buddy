#!/bin/bash

# This script manually extracts the Gemma model files from the tar
# and places them in the right location for the app to use

echo "=== Manual MLC-LLM Model Preparation ==="

# Define paths
ASSETS_DIR="app/src/main/assets"
MODELS_DIR="$ASSETS_DIR/models"
TAR_FILE="$ASSETS_DIR/gemma-2b-it-q4f16_1-android.tar"
TARGET_DIR="$MODELS_DIR/gemma2_2b_it"

# Check if the tar file exists
if [ ! -f "$TAR_FILE" ]; then
    echo "ERROR: Tar file not found at $TAR_FILE"
    echo "Please make sure the Gemma model tar file is placed in the assets directory."
    exit 1
fi

# Create the target directory if it doesn't exist
mkdir -p "$TARGET_DIR"

# Extract the tar file
echo "Extracting model files from tar..."
tar -xf "$TAR_FILE" -C "$TARGET_DIR"

# Check if extraction was successful
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to extract the tar file"
    exit 1
fi

# Create a minimal config.json file if it doesn't exist
CONFIG_FILE="$TARGET_DIR/config.json"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Creating minimal config.json file..."
    cat > "$CONFIG_FILE" << EOF
{
    "model_name": "gemma-2b-it",
    "quantization": "q4f16_1",
    "model_lib": "libmlc_llm.so",
    "runtime_lib": "libtvm_runtime.so"
}
EOF
fi

# Verify the extracted files
echo "Verifying extracted files..."
EXTRACTED_FILES=$(find "$TARGET_DIR" -type f | wc -l)
echo "Found $EXTRACTED_FILES files in $TARGET_DIR"

if [ "$EXTRACTED_FILES" -gt 0 ]; then
    echo "Model files extraction successful!"
    echo "The following files were extracted:"
    ls -la "$TARGET_DIR"
else
    echo "ERROR: No files were extracted. Something went wrong."
    exit 1
fi

echo
echo "=== Setting correct permissions ==="
chmod -R 755 "$TARGET_DIR"

echo
echo "=== Model Preparation Complete ==="
echo "Model files have been extracted to $TARGET_DIR"
echo "You can now run the app again to test the MLC-LLM integration." 