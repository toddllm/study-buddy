#!/usr/bin/env python3
"""
Extract TensorFlow Lite models from the StudyBuddy Android app.

This script extracts the TensorFlow Lite models from the app's assets directory
to a local directory for testing.

Usage:
  python extract_models.py [--apk_path PATH] [--output_dir PATH]
"""

import os
import sys
import shutil
import argparse
import zipfile
import tempfile
from pathlib import Path

def extract_models_from_apk(apk_path, output_dir):
    """Extract TensorFlow Lite models from APK file"""
    # Create output directory if it doesn't exist
    os.makedirs(output_dir, exist_ok=True)
    
    # Create a temporary directory for extraction
    with tempfile.TemporaryDirectory() as temp_dir:
        print(f"Extracting APK to temporary directory: {temp_dir}")
        
        # Extract APK (which is just a ZIP file)
        with zipfile.ZipFile(apk_path, 'r') as zip_ref:
            zip_ref.extractall(temp_dir)
        
        # Find and copy TFLite models from assets directory
        assets_dir = os.path.join(temp_dir, "assets")
        if not os.path.exists(assets_dir):
            print(f"Error: Assets directory not found in APK")
            return False
        
        # Look for .tflite files
        model_files = []
        for root, _, files in os.walk(assets_dir):
            for file in files:
                if file.endswith(".tflite"):
                    model_files.append(os.path.join(root, file))
        
        if not model_files:
            print("No TFLite models found in the APK")
            return False
        
        # Copy each model to the output directory
        for model_path in model_files:
            model_filename = os.path.basename(model_path)
            output_path = os.path.join(output_dir, model_filename)
            shutil.copy2(model_path, output_path)
            print(f"Extracted: {model_filename} -> {output_path}")
        
        print(f"Successfully extracted {len(model_files)} models to {output_dir}")
        return True

def extract_models_from_assets(assets_dir, output_dir):
    """Extract TensorFlow Lite models from assets directory"""
    # Create output directory if it doesn't exist
    os.makedirs(output_dir, exist_ok=True)
    
    # Verify assets directory exists
    if not os.path.exists(assets_dir):
        print(f"Error: Assets directory not found: {assets_dir}")
        return False
    
    # Look for .tflite files
    model_files = []
    for root, _, files in os.walk(assets_dir):
        for file in files:
            if file.endswith(".tflite"):
                model_files.append(os.path.join(root, file))
    
    if not model_files:
        print("No TFLite models found in the assets directory")
        return False
    
    # Copy each model to the output directory
    for model_path in model_files:
        model_filename = os.path.basename(model_path)
        output_path = os.path.join(output_dir, model_filename)
        shutil.copy2(model_path, output_path)
        print(f"Copied: {model_filename} -> {output_path}")
    
    print(f"Successfully copied {len(model_files)} models to {output_dir}")
    return True

def find_default_apk_path():
    """Try to find the APK file in the current project"""
    possible_paths = [
        "app/build/outputs/apk/debug/app-debug.apk",
        "app/build/outputs/apk/release/app-release.apk"
    ]
    
    for path in possible_paths:
        if os.path.exists(path):
            return path
    
    return None

def find_default_assets_path():
    """Try to find the assets directory in the current project"""
    possible_paths = [
        "app/src/main/assets",
        "app/assets"
    ]
    
    for path in possible_paths:
        if os.path.exists(path):
            return path
    
    return None

def main():
    parser = argparse.ArgumentParser(description="Extract TensorFlow Lite models from StudyBuddy")
    parser.add_argument("--apk_path", help="Path to the APK file")
    parser.add_argument("--assets_dir", help="Path to the assets directory")
    parser.add_argument("--output_dir", default="models", 
                        help="Directory to save extracted models (default: 'models')")
    
    args = parser.parse_args()
    
    print("TensorFlow Lite Model Extractor for StudyBuddy")
    print("=============================================\n")
    
    # If neither apk_path nor assets_dir is provided, try to find defaults
    if not args.apk_path and not args.assets_dir:
        apk_path = find_default_apk_path()
        assets_dir = find_default_assets_path()
        
        if apk_path:
            print(f"Found APK file: {apk_path}")
            args.apk_path = apk_path
        elif assets_dir:
            print(f"Found assets directory: {assets_dir}")
            args.assets_dir = assets_dir
        else:
            print("Error: Could not find APK file or assets directory. Please specify --apk_path or --assets_dir.")
            return
    
    # Extract models
    if args.apk_path:
        if not os.path.exists(args.apk_path):
            print(f"Error: APK file not found: {args.apk_path}")
            return
        extract_models_from_apk(args.apk_path, args.output_dir)
    elif args.assets_dir:
        if not os.path.exists(args.assets_dir):
            print(f"Error: Assets directory not found: {args.assets_dir}")
            return
        extract_models_from_assets(args.assets_dir, args.output_dir)
    
    # Print test command example
    print("\nTest extracted models with:")
    print(f"python model_tester.py --model_type image --model_path {args.output_dir}/mobilenet_v1.tflite --input path/to/image.jpg")
    print(f"python model_tester.py --model_type text --model_path {args.output_dir}/text_classification.tflite --input \"Text to classify\"")
    print(f"python model_tester.py --model_type qa --model_path {args.output_dir}/mobilebert_qa.tflite --input \"Context paragraph\" --question \"Question to answer?\"")

if __name__ == "__main__":
    main() 