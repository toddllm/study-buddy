#!/usr/bin/env python3
"""
Compile Gemma 2 model with MLC-LLM for Android

This script compiles the Gemma 2 model with TVM and MLC-LLM for use on Android devices.
"""

import os
import sys
import argparse
import subprocess
from pathlib import Path

def run_command(cmd, cwd=None):
    """Run a shell command and print output."""
    print(f"Running: {cmd}")
    try:
        process = subprocess.Popen(
            cmd,
            shell=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            universal_newlines=True,
            cwd=cwd
        )
        
        # Stream output
        for line in process.stdout:
            print(line.strip())
            
        process.wait()
        if process.returncode != 0:
            print(f"Command failed with return code {process.returncode}")
            return False
        return True
    except Exception as e:
        print(f"Error running command: {e}")
        return False

def compile_model(args):
    """Compile the model for Android target."""
    print("\n===== Compiling Gemma 2 model for Android =====")
    
    # Ensure the MLC-LLM directory exists
    mlc_llm_dir = args.mlc_llm_dir
    if not os.path.exists(mlc_llm_dir):
        print(f"Error: MLC-LLM directory {mlc_llm_dir} does not exist.")
        return False
        
    # Activate the environment if needed
    activate_env = ""
    if args.venv_dir:
        venv_dir = os.path.abspath(args.venv_dir)
        if os.name == 'nt':  # Windows
            activate_env = f"call {os.path.join(venv_dir, 'Scripts', 'activate.bat')} && "
        else:  # Unix/Linux
            activate_env = f"source {os.path.join(venv_dir, 'bin', 'activate')} && "
    
    # Create output directory for compiled model
    output_dir = os.path.abspath(args.output_dir)
    os.makedirs(output_dir, exist_ok=True)
    
    # Prepare the command to compile the model
    # Using MLC-LLM's Python API to compile
    # This will create a model optimized for Android ARM64 architecture
    compile_script = f"""
import mlc_llm
import tvm
from mlc_llm import build_model_from_hf
    
# Set parameters
model_path = "{args.model_path}"
target = "android"  # Specify Android as target
quantization = "{args.quantization}"
output_path = "{output_dir}"
    
# Configure build options
build_config = {{
    "model": {{
        "name": "gemma-2b",
        "variant": "gemma-2b-it",
        "hf_path": model_path
    }},
    "quantization": quantization,
    "target": target,
    "system_lib": True,
    "conv_template": "mlc-chat-gemma",
    "output_path": output_path
}}

# Build the model
build_model_from_hf.build(build_config)
print("Model compilation complete! Output at:", output_path)
"""
    
    # Write the compilation script to a temporary file
    script_path = os.path.join(os.path.dirname(output_dir), "compile_script.py")
    with open(script_path, "w") as f:
        f.write(compile_script)
    
    # Run the compilation command
    cmd = f"{activate_env}python {script_path}"
    return run_command(cmd, cwd=mlc_llm_dir)

def check_model(args):
    """Check if the compiled model exists."""
    print("\n===== Checking compiled model =====")
    
    output_dir = os.path.abspath(args.output_dir)
    model_lib = os.path.join(output_dir, "gemma-2b", "lib", "libgemma-2b.so")
    
    if os.path.exists(model_lib):
        print(f"Compiled model library found at: {model_lib}")
        return True
    else:
        print(f"Compiled model library not found at: {model_lib}")
        return False

def copy_to_app(args):
    """Copy the compiled model to the app directory."""
    print("\n===== Copying compiled model to app =====")
    
    output_dir = os.path.abspath(args.output_dir)
    app_dir = os.path.abspath(args.app_dir)
    
    # Create the directories in the Android app
    app_assets_dir = os.path.join(app_dir, "src", "main", "assets", "models")
    app_jniLibs_dir = os.path.join(app_dir, "src", "main", "jniLibs", "arm64-v8a")
    
    os.makedirs(app_assets_dir, exist_ok=True)
    os.makedirs(app_jniLibs_dir, exist_ok=True)
    
    # Copy the model configuration to assets
    model_dir = os.path.join(output_dir, "gemma-2b")
    if not os.path.exists(model_dir):
        print(f"Error: Model directory {model_dir} does not exist.")
        return False
    
    # Copy config files
    for config_file in ["mlc-chat-config.json", "ndarray-cache.json", "tokenizer.json", "tokenizer_config.json"]:
        src_file = os.path.join(model_dir, config_file)
        if os.path.exists(src_file):
            cmd = f"cp {src_file} {app_assets_dir}/"
            run_command(cmd)
    
    # Copy library files to jniLibs
    lib_dir = os.path.join(model_dir, "lib")
    if os.path.exists(lib_dir):
        for lib_file in os.listdir(lib_dir):
            if lib_file.endswith(".so"):
                src_file = os.path.join(lib_dir, lib_file)
                cmd = f"cp {src_file} {app_jniLibs_dir}/"
                run_command(cmd)
    
    print(f"Model files copied to {app_assets_dir} and {app_jniLibs_dir}")
    return True

def main():
    parser = argparse.ArgumentParser(description="Compile Gemma 2 model for Android")
    parser.add_argument("--mlc-llm-dir", default="mlc_build/mlc-llm", help="Path to MLC-LLM directory")
    parser.add_argument("--model-path", default="mlc_build/model_files", help="Path to the model files downloaded from Hugging Face")
    parser.add_argument("--output-dir", default="mlc_build/compiled_model", help="Output directory for compiled model")
    parser.add_argument("--app-dir", default="app", help="Android app directory")
    parser.add_argument("--venv-dir", help="Virtual environment directory (optional)")
    parser.add_argument("--quantization", default="q4f16_1", help="Quantization configuration")
    
    args = parser.parse_args()
    
    # Execute steps
    if not compile_model(args):
        print("Model compilation failed.")
        return 1
    
    if not check_model(args):
        print("Model check failed.")
        return 1
    
    if not copy_to_app(args):
        print("Copying model to app failed.")
        return 1
    
    print("\nModel compilation and setup complete!")
    return 0

if __name__ == "__main__":
    sys.exit(main()) 