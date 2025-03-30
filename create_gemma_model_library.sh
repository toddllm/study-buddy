#!/bin/bash

# This script will create the necessary environment and compile the Gemma 2B model for Android
set -e

echo "Setting up environment to compile Gemma model library for Android"

# Create a Python virtual environment
python3 -m venv mlc_env
source mlc_env/bin/activate

# Install MLC LLM Python package
pip install --upgrade pip
pip install --upgrade mlc-llm mlc-ai-nightly

# Create a temporary directory for the model configuration
mkdir -p mlc_temp
cd mlc_temp

# Create model config file
cat > mlc-package-config.json << EOF
{
  "device": "android",
  "model_list": [
    {
      "model": "HF://mlc-ai/gemma-2-2b-it-q4f16_1-MLC",
      "model_id": "gemma-2-2b-it-q4f16_1-MLC",
      "estimated_vram_bytes": 3000000000,
      "bundle_weight": true
    }
  ]
}
EOF

echo "Configuration created. Running MLC LLM package command..."

# Run the package command to compile the model
export MLC_JIT_POLICY=REDO
mlc_llm package

echo "Checking compiled model files..."

# Check for the compiled model library
if [ -d "dist/bundle/gemma-2-2b-it-q4f16_1-MLC/lib" ]; then
    echo "Model library compiled successfully!"
    
    # Copy the model library to the app directory
    mkdir -p ../app/src/main/assets/models/gemma2_2b_it/lib
    cp -f dist/bundle/gemma-2-2b-it-q4f16_1-MLC/lib/libgemma-2-2b-it-q4f16_1.so ../app/src/main/assets/models/gemma2_2b_it/lib/
    
    echo "Model library copied to app assets directory"
else
    echo "Model library compilation failed. Please check the logs."
    exit 1
fi

# Copy any other required files
cd ..

# Deactivate virtual environment
deactivate

echo "Gemma model library setup complete!" 