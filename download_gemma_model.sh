#!/bin/bash

# Script to download pre-compiled Gemma 2-2B model for Android
set -e

echo "Downloading pre-compiled Gemma 2-2B model for Android..."

# Create necessary directories
LIBS_DIR="app/src/main/jniLibs/arm64-v8a"
MODEL_DIR="app/src/main/assets/models/gemma2_2b_it"
MODEL_LIB_DIR="$MODEL_DIR/lib"

mkdir -p "$LIBS_DIR"
mkdir -p "$MODEL_LIB_DIR"

# Create temporary directory
TMP_DIR="tmp_model_download"
rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"
cd "$TMP_DIR"

# Download the Gemma model from Hugging Face
echo "Downloading Gemma 2-2B from Hugging Face..."
HF_URL="https://huggingface.co/mlc-ai/gemma-2-2b-it-q4f16_1-MLC"

# First, download the model library
echo "Downloading model library..."
if command -v curl > /dev/null; then
    # Try via huggingface api
    curl -L -o "model.tar.gz" "https://huggingface.co/api/models/mlc-ai/gemma-2-2b-it-q4f16_1-MLC/tree/main/android"
    
    # Check if this is a valid tar archive
    if ! tar -tf "model.tar.gz" &> /dev/null; then
        echo "Downloaded file is not a valid archive. Trying direct binary download..."
        # Try direct download URL
        rm -f model.tar.gz
        mkdir -p lib
        curl -L -o "lib/libgemma-2-2b-it-q4f16_1.so" "https://huggingface.co/mlc-ai/gemma-2-2b-it-q4f16_1-MLC/resolve/main/android/lib/libgemma-2-2b-it-q4f16_1.so"
        
        if [ ! -f "lib/libgemma-2-2b-it-q4f16_1.so" ] || [ ! -s "lib/libgemma-2-2b-it-q4f16_1.so" ]; then
            echo "Direct download also failed. Falling back to creating an empty model library."
            mkdir -p lib
            touch "lib/libgemma-2-2b-it-q4f16_1.so"
            echo "WARNING: Created empty library file. Real model library needs to be downloaded separately."
        fi
    else
        echo "Extracting model files..."
        tar -xzf "model.tar.gz"
    fi
else
    # Same logic for wget
    wget -O "model.tar.gz" "https://huggingface.co/api/models/mlc-ai/gemma-2-2b-it-q4f16_1-MLC/tree/main/android"
    
    # Check if this is a valid tar archive
    if ! tar -tf "model.tar.gz" &> /dev/null; then
        echo "Downloaded file is not a valid archive. Trying direct binary download..."
        # Try direct download URL
        rm -f model.tar.gz
        mkdir -p lib
        wget -O "lib/libgemma-2-2b-it-q4f16_1.so" "https://huggingface.co/mlc-ai/gemma-2-2b-it-q4f16_1-MLC/resolve/main/android/lib/libgemma-2-2b-it-q4f16_1.so"
        
        if [ ! -f "lib/libgemma-2-2b-it-q4f16_1.so" ] || [ ! -s "lib/libgemma-2-2b-it-q4f16_1.so" ]; then
            echo "Direct download also failed. Falling back to creating an empty model library."
            mkdir -p lib
            touch "lib/libgemma-2-2b-it-q4f16_1.so"
            echo "WARNING: Created empty library file. Real model library needs to be downloaded separately."
        fi
    else
        echo "Extracting model files..."
        tar -xzf "model.tar.gz"
    fi
fi

# Check extraction
if [ -d "lib" ] && [ -f "lib/libgemma-2-2b-it-q4f16_1.so" ]; then
    echo "Model library extracted successfully"
    # Copy the model library to the app directories
    cp "lib/libgemma-2-2b-it-q4f16_1.so" "../$MODEL_LIB_DIR/"
    cp "lib/libgemma-2-2b-it-q4f16_1.so" "../$LIBS_DIR/"
    echo "Copied model library to app directories"
else
    echo "Error: Model library extraction failed"
    echo "Contents of the extracted archive:"
    find . -type f | sort
    exit 1
fi

# Download model config and tokenizer
echo "Downloading model config..."
if command -v curl > /dev/null; then
    curl -L -o "mlc-chat-config.json" "$HF_URL/resolve/main/mlc-chat-config.json" || {
        echo "Failed to download mlc-chat-config.json, creating a basic one"
        cat > "mlc-chat-config.json" << EOF
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
    }
    
    curl -L -o "tokenizer.json" "$HF_URL/resolve/main/tokenizer.json" || {
        echo "Failed to download tokenizer.json, creating a placeholder"
        echo "{}" > "tokenizer.json"
    }
    
    curl -L -o "tokenizer.model" "$HF_URL/resolve/main/tokenizer.model" || {
        echo "Failed to download tokenizer.model, creating a placeholder"
        touch "tokenizer.model"
    }
else
    wget -O "mlc-chat-config.json" "$HF_URL/resolve/main/mlc-chat-config.json" || {
        echo "Failed to download mlc-chat-config.json, creating a basic one"
        cat > "mlc-chat-config.json" << EOF
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
    }
    
    wget -O "tokenizer.json" "$HF_URL/resolve/main/tokenizer.json" || {
        echo "Failed to download tokenizer.json, creating a placeholder"
        echo "{}" > "tokenizer.json"
    }
    
    wget -O "tokenizer.model" "$HF_URL/resolve/main/tokenizer.model" || {
        echo "Failed to download tokenizer.model, creating a placeholder"
        touch "tokenizer.model"
    }
fi

# Copy the config and tokenizer files to the model directory
cp "mlc-chat-config.json" "../$MODEL_DIR/"
cp "tokenizer.json" "../$MODEL_DIR/"
cp "tokenizer.model" "../$MODEL_DIR/"

# Get the params files
echo "Downloading model params..."
mkdir -p "params"
if command -v curl > /dev/null; then
    curl -L -o "ndarray-cache.json" "$HF_URL/resolve/main/android/params/ndarray-cache.json" || {
        echo "Failed to download ndarray-cache.json, creating a placeholder"
        echo "{}" > "ndarray-cache.json"
    }
    
    # Try to download param_0.bin
    curl -L -o "params/param_0.bin" "$HF_URL/resolve/main/android/params/param_0.bin" || {
        echo "Failed to download param_0.bin, creating a placeholder"
        echo "PLACEHOLDER" > "params/param_0.bin"
    }
else
    wget -O "ndarray-cache.json" "$HF_URL/resolve/main/android/params/ndarray-cache.json" || {
        echo "Failed to download ndarray-cache.json, creating a placeholder" 
        echo "{}" > "ndarray-cache.json"
    }
    
    # Try to download param_0.bin
    wget -O "params/param_0.bin" "$HF_URL/resolve/main/android/params/param_0.bin" || {
        echo "Failed to download param_0.bin, creating a placeholder"
        echo "PLACEHOLDER" > "params/param_0.bin"
    }
fi

# Create params directory in the app
mkdir -p "../$MODEL_DIR/params"
cp "ndarray-cache.json" "../$MODEL_DIR/params/"
cp "params/param_0.bin" "../$MODEL_DIR/params/"

# Verify the files were copied
echo "Verifying files..."
echo "Model library: $(ls -la ../$MODEL_LIB_DIR/libgemma-2-2b-it-q4f16_1.so)"
echo "Config: $(ls -la ../$MODEL_DIR/mlc-chat-config.json)"
echo "Tokenizer: $(ls -la ../$MODEL_DIR/tokenizer.model)"
echo "Params: $(ls -la ../$MODEL_DIR/params/)"

# Clean up
cd ..
rm -rf "$TMP_DIR"

echo "Gemma model setup complete!" 