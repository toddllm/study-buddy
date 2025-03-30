#!/usr/bin/env python3
"""
Analyze Gemma model structure to prepare for Android integration.
This script examines a local copy of the Gemma model and generates
a report of its structure, file sizes, and dependencies.
"""

import os
import json
import argparse
import sys
from pathlib import Path


def format_size(size_bytes):
    """Format file size in a human-readable format."""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size_bytes < 1024.0 or unit == 'GB':
            return f"{size_bytes:.2f} {unit}"
        size_bytes /= 1024.0


def analyze_model_directory(model_path):
    """Analyze a model directory and return information about its contents."""
    model_path = Path(model_path).expanduser().resolve()
    
    if not model_path.exists() or not model_path.is_dir():
        print(f"Error: Model directory not found: {model_path}")
        return None
    
    result = {
        "directory": str(model_path),
        "total_size": 0,
        "file_count": 0,
        "files": [],
        "config": None,
        "weight_files": [],
        "tokenizer_files": [],
        "library_files": [],
        "other_files": []
    }
    
    # Analyze each file in the directory (recursive)
    for root, _, files in os.walk(model_path):
        for filename in files:
            file_path = Path(root) / filename
            relative_path = file_path.relative_to(model_path)
            file_size = file_path.stat().st_size
            
            file_info = {
                "name": filename,
                "path": str(relative_path),
                "size": file_size,
                "size_formatted": format_size(file_size)
            }
            
            result["files"].append(file_info)
            result["total_size"] += file_size
            result["file_count"] += 1
            
            # Categorize files
            if filename.endswith('.bin'):
                result["weight_files"].append(file_info)
            elif filename.endswith('.json'):
                if filename == 'config.json':
                    result["config"] = file_info
                    # Try to parse the config
                    try:
                        with open(file_path, 'r') as f:
                            config_data = json.load(f)
                            file_info["content"] = config_data
                    except Exception as e:
                        file_info["error"] = f"Failed to parse config: {e}"
                elif 'tokenizer' in filename:
                    result["tokenizer_files"].append(file_info)
                else:
                    result["other_files"].append(file_info)
            elif filename.endswith('.so'):
                result["library_files"].append(file_info)
            else:
                result["other_files"].append(file_info)
    
    # Format total size
    result["total_size_formatted"] = format_size(result["total_size"])
    
    return result


def save_analysis(analysis, output_path):
    """Save the analysis to a JSON file."""
    with open(output_path, 'w') as f:
        json.dump(analysis, f, indent=2)
    print(f"Analysis saved to {output_path}")


def print_summary(analysis):
    """Print a summary of the analysis."""
    if not analysis:
        return
    
    print("\n===== Gemma Model Analysis =====")
    print(f"Model directory: {analysis['directory']}")
    print(f"Total size: {analysis['total_size_formatted']} ({analysis['file_count']} files)")
    
    print("\n--- Configuration ---")
    if analysis['config']:
        print(f"Config file: {analysis['config']['path']} ({analysis['config']['size_formatted']})")
    else:
        print("No config file found.")
    
    print("\n--- Tokenizer Files ---")
    if analysis['tokenizer_files']:
        for file in analysis['tokenizer_files']:
            print(f"- {file['path']} ({file['size_formatted']})")
    else:
        print("No tokenizer files found.")
    
    print("\n--- Weight Files ---")
    if analysis['weight_files']:
        total_weight_size = sum(file['size'] for file in analysis['weight_files'])
        formatted_total = format_size(total_weight_size)
        print(f"Found {len(analysis['weight_files'])} weight files (total: {formatted_total}):")
        for file in analysis['weight_files']:
            print(f"- {file['path']} ({file['size_formatted']})")
    else:
        print("No weight files found.")
    
    print("\n--- Library Files ---")
    if analysis['library_files']:
        for file in analysis['library_files']:
            print(f"- {file['path']} ({file['size_formatted']})")
    else:
        print("No library (.so) files found.")
    
    print("\n--- Other Files ---")
    if analysis['other_files']:
        for file in analysis['other_files']:
            print(f"- {file['path']} ({file['size_formatted']})")
    else:
        print("No other files found.")


def main():
    parser = argparse.ArgumentParser(description="Analyze Gemma model structure")
    parser.add_argument("model_path", help="Path to the model directory")
    parser.add_argument("--output", "-o", help="Path to save the analysis JSON")
    args = parser.parse_args()
    
    analysis = analyze_model_directory(args.model_path)
    if analysis:
        print_summary(analysis)
        if args.output:
            save_analysis(analysis, args.output)


if __name__ == "__main__":
    main()
