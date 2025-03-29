#!/bin/bash

# This script extracts the model files from the tar file and places them in the assets directory
# This is helpful for debugging, as it bypasses runtime extraction

echo "=== Extracting Model Files for Debugging ==="

# Constants
TAR_FILE="app/src/main/assets/gemma-2b-it-q4f16_1-android.tar"
OUTPUT_DIR="app/src/main/assets/models/gemma2_2b_it"

# Check if tar file exists
if [ ! -f "$TAR_FILE" ]; then
    echo "ERROR: Tar file not found at $TAR_FILE"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Extract the tar file
echo "Extracting model files from $TAR_FILE to $OUTPUT_DIR..."
tar -xf "$TAR_FILE" -C "$OUTPUT_DIR"

# Check if extraction was successful
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to extract tar file"
    exit 1
fi

# Create a minimal config.json file if it doesn't exist
CONFIG_FILE="$OUTPUT_DIR/config.json"
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

# Count the number of extracted files
NUM_FILES=$(find "$OUTPUT_DIR" -type f | wc -l)
echo "Successfully extracted $NUM_FILES files"

echo "=== Model files are now ready for direct use ==="
echo "The app will now use these pre-extracted files instead of extracting at runtime"
echo "This should help with debugging and performance" 