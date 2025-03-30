#!/bin/bash

# Script to create a minimal dummy Gemma model library
set -e

echo "Creating minimal dummy Gemma model library..."

# Create necessary directories
LIBS_DIR="app/src/main/jniLibs/arm64-v8a"
MODEL_DIR="app/src/main/assets/models/gemma2_2b_it"
MODEL_LIB_DIR="$MODEL_DIR/lib"

mkdir -p "$LIBS_DIR"
mkdir -p "$MODEL_LIB_DIR"
mkdir -p "tmp_model_build"
cd "tmp_model_build"

# Create an empty library file
echo "Creating empty model library file..."
dd if=/dev/zero of=libgemma-2-2b-it-q4f16_1.so bs=1k count=10
chmod +x libgemma-2-2b-it-q4f16_1.so

# Copy to app directories
cp libgemma-2-2b-it-q4f16_1.so "../$MODEL_LIB_DIR/"
cp libgemma-2-2b-it-q4f16_1.so "../$LIBS_DIR/"

# Create model config file if it doesn't exist
if [ ! -f "../$MODEL_DIR/mlc-chat-config.json" ]; then
    echo "Creating model config file..."
    cat > "../$MODEL_DIR/mlc-chat-config.json" << EOF
{
  "model_lib": "gemma-2-2b-it-q4f16_1",
  "model_name": "gemma-2-2b-it",
  "conv_template": "gemma-instruct",
  "temperature": 0.7,
  "top_p": 0.95,
  "repetition_penalty": 1.2,
  "max_gen_len": 2048
}
EOF
fi

# Create minimal tokenizer files if they don't exist
if [ ! -f "../$MODEL_DIR/tokenizer.model" ]; then
    echo "Creating minimal tokenizer files..."
    # Create a 1MB dummy tokenizer model file to simulate a real one
    dd if=/dev/zero of="../$MODEL_DIR/tokenizer.model" bs=1M count=1
    echo "{}" > "../$MODEL_DIR/tokenizer.json"
fi

# Create params directory with placeholder files
mkdir -p "../$MODEL_DIR/params"
if [ ! -f "../$MODEL_DIR/params/ndarray-cache.json" ]; then
    echo "{}" > "../$MODEL_DIR/params/ndarray-cache.json"
    # Create a 1MB dummy param file to simulate a real one
    dd if=/dev/zero of="../$MODEL_DIR/params/param_0.bin" bs=1M count=1
fi

# Verify files
echo "Verifying files..."
echo "Model library: $(ls -la ../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so)"
echo "Config: $(ls -la ../$MODEL_DIR/mlc-chat-config.json)"
echo "Tokenizer: $(ls -la ../$MODEL_DIR/tokenizer.model)"
echo "Params: $(ls -la ../$MODEL_DIR/params/)"

# Clean up
cd ..
rm -rf "tmp_model_build"

echo "Minimal dummy Gemma model library created successfully!"
echo "NOTE: This is just a placeholder library that won't actually work."
echo "The app will fail to initialize the model but with proper error messages." 