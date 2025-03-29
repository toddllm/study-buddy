#!/bin/bash

# This script extracts the gemma model files from the tar file
# to the models/gemma2_2b_it directory

echo "Creating model directory structure..."

# Create the gemma2_2b_it directory
mkdir -p gemma2_2b_it

# Check if the tar file exists in the parent directory
TAR_FILE="../gemma-2b-it-q4f16_1-android.tar"
if [ ! -f "$TAR_FILE" ]; then
    echo "Error: Tar file not found at $TAR_FILE"
    exit 1
fi

echo "Extracting model files from $TAR_FILE..."
tar -xf "$TAR_FILE" -C gemma2_2b_it

# Check if extraction was successful
if [ $? -eq 0 ]; then
    echo "Model files extracted successfully"
    echo "Files in gemma2_2b_it directory:"
    ls -la gemma2_2b_it
else
    echo "Failed to extract model files"
    exit 1
fi

# Create a minimal config.json file if it doesn't exist
CONFIG_FILE="gemma2_2b_it/config.json"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Creating minimal config.json file..."
    cat > "$CONFIG_FILE" << EOF
{
    "model_name": "gemma-2b-it",
    "quantization": "q4f16_1"
}
EOF
    echo "Created config.json file"
fi

echo "Setup complete!" 