#!/bin/bash

# This script collects debug information about the model files and libraries
# Run this script on your device to help diagnose MLC-LLM initialization issues

echo "=== StudyBuddy MLC-LLM Debug Information ==="
echo "Date: $(date)"
echo

echo "=== Android Device Information ==="
echo "Android version: $(getprop ro.build.version.release)"
echo "Device: $(getprop ro.product.model)"
echo "Architecture: $(getprop ro.product.cpu.abi)"
echo

echo "=== Libraries in jniLibs directory ==="
ls -la app/src/main/jniLibs/arm64-v8a/
echo

echo "=== Model Files in assets ==="
if [ -f "app/src/main/assets/gemma-2b-it-q4f16_1-android.tar" ]; then
    echo "TAR file exists with size: $(du -h app/src/main/assets/gemma-2b-it-q4f16_1-android.tar | cut -f1)"
    
    # Create a tmp directory to extract the tar
    mkdir -p tmp_extract
    tar -xf app/src/main/assets/gemma-2b-it-q4f16_1-android.tar -C tmp_extract
    
    echo "Contents of extracted TAR file:"
    ls -la tmp_extract/
    
    # Remove the temp directory
    rm -rf tmp_extract
else
    echo "TAR file not found!"
fi

echo
echo "=== Models directory content ==="
ls -la app/src/main/assets/models/
if [ -d "app/src/main/assets/models/gemma2_2b_it" ]; then
    echo "Contents of gemma2_2b_it directory:"
    ls -la app/src/main/assets/models/gemma2_2b_it/
    
    if [ -f "app/src/main/assets/models/gemma2_2b_it/config.json" ]; then
        echo "Content of config.json:"
        cat app/src/main/assets/models/gemma2_2b_it/config.json
    else
        echo "config.json not found!"
    fi
else
    echo "gemma2_2b_it directory not found!"
fi

echo
echo "=== Running model extraction script ==="
cd app/src/main/assets/models/
./create_models_directory.sh
cd ../../../../

echo
echo "=== Checking if models were successfully extracted ==="
if [ -d "app/src/main/assets/models/gemma2_2b_it" ]; then
    echo "Model directory exists after extraction"
    ls -la app/src/main/assets/models/gemma2_2b_it/
else
    echo "Model directory still not found after extraction!"
fi

echo
echo "=== Debug information collection complete ==="
echo "This information can help diagnose MLC-LLM initialization issues" 