#!/bin/bash

# Script to create a minimal model structure for the LLM
# Uses placeholder files to save space

echo "Setting up minimal Gemma 2B-IT model structure for Android..."

# Create target directories
MODEL_DIR="app/src/main/assets/models/gemma2_2b_it"
mkdir -p $MODEL_DIR
mkdir -p $MODEL_DIR/lib

# Create a basic config.json file
echo "Creating config.json..."
cat > "$MODEL_DIR/config.json" << EOL
{
    "model_name": "gemma-2b-it",
    "quantization": "q4f16_1",
    "model_lib": "libmlc_llm.so",
    "runtime_lib": "libtvm_runtime.so"
}
EOL

# Create placeholder lib file
echo "Creating placeholder library file..."
touch "$MODEL_DIR/lib/libgemma-2b-it-q4f16_1.so"

# Create placeholder tokenizer files (minimalist to save space)
echo "Creating placeholder tokenizer files..."
echo "{}" > "$MODEL_DIR/tokenizer.json"
echo "{}" > "$MODEL_DIR/tokenizer_config.json"
touch "$MODEL_DIR/tokenizer.model"

# Create minimal MLC chat config file
echo "Creating mlc-chat-config.json..."
cat > "$MODEL_DIR/mlc-chat-config.json" << EOL
{
  "model_lib": "gemma-2b-it-q4f16_1",
  "model_name": "gemma-2b-it",
  "conv_template": "gemma-2b-instruct",
  "temperature": 0.7,
  "top_p": 0.95
}
EOL

# Create a couple of small parameter shard files instead of all 38
echo "Creating minimal parameter shard files..."
echo "PLACEHOLDER" > "$MODEL_DIR/params_shard_0.bin"
echo "PLACEHOLDER" > "$MODEL_DIR/params_shard_1.bin"

echo "Model structure created successfully (minimal) in $MODEL_DIR"
echo "Note: These are placeholder files to minimize APK size."
echo "Script completed" 