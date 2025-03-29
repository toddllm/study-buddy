#!/usr/bin/env python3
"""
Prepare MLC-LLM for Android integration

This script helps prepare the MLC-LLM dependencies and model files for proper integration with an Android app.
"""

import os
import sys
import platform
import subprocess
import shutil
import argparse
from pathlib import Path

def print_step(step, message):
    """Print a step in the process with formatting."""
    print(f"\n===== STEP {step}: {message} =====")

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

def clone_repositories(args):
    """Clone necessary repositories."""
    print_step(1, "Cloning repositories")
    
    # Create the build directory
    os.makedirs(args.build_dir, exist_ok=True)
    
    # Clone MLC-LLM if not already cloned
    mlc_llm_dir = os.path.join(args.build_dir, "mlc-llm")
    if not os.path.exists(mlc_llm_dir):
        success = run_command(f"git clone --recursive https://github.com/mlc-ai/mlc-llm.git {mlc_llm_dir}")
        if not success:
            print("Failed to clone MLC-LLM repository. Aborting.")
            return False
    else:
        print(f"MLC-LLM repository already exists at {mlc_llm_dir}")
        # Update it
        run_command("git pull && git submodule update --init --recursive", cwd=mlc_llm_dir)
    
    return True

def install_dependencies(args):
    """Install required Python dependencies."""
    print_step(2, "Installing dependencies")
    
    requirements = [
        "numpy",
        "torch",
        "sentencepiece",
        "transformers",
        "huggingface_hub"
    ]
    
    cmd = f"{sys.executable} -m pip install {' '.join(requirements)}"
    return run_command(cmd)

def download_model(args):
    """Download the model files from Hugging Face."""
    print_step(3, "Downloading model files")
    
    model_dir = os.path.join(args.build_dir, "model_files")
    os.makedirs(model_dir, exist_ok=True)
    
    # Use Hugging Face CLI to download
    auth_arg = f"--token {args.hf_token}" if args.hf_token else ""
    cmd = f"huggingface-cli download mlc-ai/gemma-2b-it-q4f16_1-MLC {auth_arg} --local-dir {model_dir} --local-dir-use-symlinks False"
    
    return run_command(cmd)

def setup_android_environment(args):
    """Set up the Android build environment."""
    print_step(4, "Setting up Android build environment")
    
    # Copy relevant files to the Android app
    mlc_android_dir = os.path.join(args.build_dir, "mlc-llm", "android")
    app_cpp_dir = os.path.join(args.app_dir, "src", "main", "cpp")
    app_jni_dir = os.path.join(app_cpp_dir, "jni")
    
    # Create directories
    os.makedirs(app_jni_dir, exist_ok=True)
    
    # Copy MLC-LLM Android Java files
    mlc4j_dir = os.path.join(mlc_android_dir, "mlc4j", "src", "main", "java", "ai", "mlc", "mlcllm")
    app_java_dir = os.path.join(args.app_dir, "src", "main", "java", "ai", "mlc", "mlcllm")
    os.makedirs(app_java_dir, exist_ok=True)
    
    print(f"Copying Java interface files from {mlc4j_dir} to {app_java_dir}")
    if os.path.exists(mlc4j_dir):
        for file in os.listdir(mlc4j_dir):
            if file.endswith('.java'):
                shutil.copy2(os.path.join(mlc4j_dir, file), app_java_dir)
    else:
        print(f"Warning: Source directory {mlc4j_dir} does not exist")
    
    # Now create a template CMakeLists.txt file
    with open(os.path.join(app_cpp_dir, "CMakeLists.txt"), "w") as f:
        f.write('''cmake_minimum_required(VERSION 3.18.1)
project(mlc_llm_android VERSION 1.0.0)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# Add the MLC-LLM JNI library
add_library(mlc_llm_jni SHARED
    mlc_llm_jni.cpp
)

# Find required packages
find_library(log-lib log)

# Link with required libraries
target_link_libraries(mlc_llm_jni
    ${log-lib}
)

# For production build, you would link against:
# Include TVM/MLC-LLM libraries
# target_link_libraries(mlc_llm_jni
#     tvm_runtime
#     mlc_llm_module
#     ${log-lib}
# )
''')
    
    return True

def print_next_steps(args):
    """Print instructions for next steps."""
    print_step(5, "Next steps")
    
    print("""
To complete the MLC-LLM integration:

1. Build the TVM runtime for Android:
   - This requires setting up the Android NDK
   - Follow the TVM4J Android build guide in the TVM documentation
   
2. Compile the MLC-LLM model with TVM:
   - Use the MLC-LLM Python API to compile models for Android
   - Specify the correct target (e.g., android_arm64)
   
3. Update the CMakeLists.txt in your app to link against the TVM runtime
   and MLC-LLM libraries

4. Make sure the Java files are correctly integrated with your app

For now, you can continue using the enhanced mock implementation while 
working on the full integration.
""")
    return True

def main():
    parser = argparse.ArgumentParser(description="Prepare MLC-LLM for Android integration")
    parser.add_argument("--build-dir", default="mlc_build", help="Directory to build in")
    parser.add_argument("--app-dir", default="app", help="Android app directory")
    parser.add_argument("--hf-token", help="Hugging Face token for downloading gated models")
    
    args = parser.parse_args()
    
    # Normalize paths
    args.build_dir = os.path.abspath(args.build_dir)
    args.app_dir = os.path.abspath(args.app_dir)
    
    # Execute all steps
    steps = [
        clone_repositories,
        install_dependencies, 
        download_model,
        setup_android_environment,
        print_next_steps
    ]
    
    for step_fn in steps:
        if not step_fn(args):
            print(f"Step {step_fn.__name__} failed. Aborting.")
            return 1
    
    print("\nAll preparation steps completed successfully!")
    return 0

if __name__ == "__main__":
    sys.exit(main()) 