#!/usr/bin/env python3
"""
Benchmark TensorFlow Lite models for the StudyBuddy app.

This script measures the performance (speed, memory usage) of the 
TensorFlow Lite models used in the StudyBuddy app.

Usage:
  python benchmark_models.py --model_dir models [--iterations 10] [--warmup 3]
"""

import os
import time
import argparse
import numpy as np
import tensorflow as tf
from PIL import Image
import tracemalloc

class ModelBenchmark:
    def __init__(self, model_dir, iterations=10, warmup=3):
        self.model_dir = model_dir
        self.iterations = iterations
        self.warmup = warmup
        self.models = self._find_models()
        
    def _find_models(self):
        """Find TFLite models in the model directory"""
        models = []
        for file in os.listdir(self.model_dir):
            if file.endswith(".tflite"):
                models.append(os.path.join(self.model_dir, file))
        return models
    
    def _create_random_image(self, size=224):
        """Create a random RGB image for testing"""
        img = Image.fromarray(np.random.randint(0, 255, (size, size, 3), dtype=np.uint8))
        return img
    
    def _create_random_text(self, length=100):
        """Create random text for testing"""
        words = ["the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog",
                "science", "math", "history", "physics", "chemistry", "biology",
                "computer", "algorithm", "data", "neural", "network", "learning"]
        return " ".join(np.random.choice(words, size=length//5)) + "."
    
    def benchmark_model(self, model_path):
        """Benchmark a single model"""
        print(f"\nBenchmarking model: {os.path.basename(model_path)}")
        
        # Load model
        interpreter = tf.lite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors()
        
        # Get input details
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        # Create appropriate test input based on input shape
        input_shape = input_details[0]['shape']
        input_type = input_details[0]['dtype']
        
        if len(input_shape) == 4:  # Image input (NHWC)
            height, width = input_shape[1], input_shape[2]
            test_input = np.random.random(input_shape).astype(np.float32)
            input_type_str = "Image"
        else:  # Text or other input
            test_input = np.random.random(input_shape).astype(np.float32)
            input_type_str = "Text/Other"
        
        print(f"Input type: {input_type_str}")
        print(f"Input shape: {input_shape}")
        print(f"Input dtype: {input_type}")
        
        # Warm-up runs
        print(f"Performing {self.warmup} warm-up runs...")
        for _ in range(self.warmup):
            interpreter.set_tensor(input_details[0]['index'], test_input)
            interpreter.invoke()
            _ = interpreter.get_tensor(output_details[0]['index'])
        
        # Benchmark runs
        print(f"Benchmarking with {self.iterations} iterations...")
        
        # Memory tracking
        tracemalloc.start()
        start_memory = tracemalloc.get_traced_memory()[0]
        
        # Time measurement
        inference_times = []
        for i in range(self.iterations):
            start_time = time.time()
            
            # Run inference
            interpreter.set_tensor(input_details[0]['index'], test_input)
            interpreter.invoke()
            _ = interpreter.get_tensor(output_details[0]['index'])
            
            end_time = time.time()
            inference_time = (end_time - start_time) * 1000  # ms
            inference_times.append(inference_time)
            
            print(f"  Iteration {i+1}/{self.iterations}: {inference_time:.2f} ms")
        
        # Get memory usage
        peak_memory = tracemalloc.get_traced_memory()[1] - start_memory
        tracemalloc.stop()
        
        # Calculate statistics
        avg_time = np.mean(inference_times)
        std_time = np.std(inference_times)
        min_time = np.min(inference_times)
        max_time = np.max(inference_times)
        
        # Print results
        print("\nResults:")
        print(f"  Average inference time: {avg_time:.2f} ms")
        print(f"  Standard deviation: {std_time:.2f} ms")
        print(f"  Min inference time: {min_time:.2f} ms")
        print(f"  Max inference time: {max_time:.2f} ms")
        print(f"  Peak memory usage: {peak_memory / (1024 * 1024):.2f} MB")
        
        return {
            "model_name": os.path.basename(model_path),
            "avg_time": avg_time,
            "std_time": std_time,
            "min_time": min_time,
            "max_time": max_time,
            "peak_memory": peak_memory / (1024 * 1024)  # Convert to MB
        }
    
    def run_all_benchmarks(self):
        """Run benchmarks on all models"""
        results = []
        
        if not self.models:
            print("No models found in directory:", self.model_dir)
            return results
        
        print(f"Found {len(self.models)} models to benchmark")
        
        for model_path in self.models:
            try:
                result = self.benchmark_model(model_path)
                results.append(result)
            except Exception as e:
                print(f"Error benchmarking {os.path.basename(model_path)}: {str(e)}")
                traceback.print_exc()
        
        # Print summary
        if results:
            print("\nBenchmark Summary:")
            print("-----------------")
            print("Model                    | Avg Time (ms) | Memory (MB)")
            print("-------------------------|---------------|------------")
            for result in results:
                print(f"{result['model_name']:<25} | {result['avg_time']:>12.2f} | {result['peak_memory']:>10.2f}")
        
        return results

def main():
    parser = argparse.ArgumentParser(description="Benchmark TensorFlow Lite models")
    parser.add_argument("--model_dir", default="models", 
                        help="Directory containing TFLite models (default: 'models')")
    parser.add_argument("--iterations", type=int, default=10,
                       help="Number of inference iterations (default: 10)")
    parser.add_argument("--warmup", type=int, default=3,
                       help="Number of warm-up iterations (default: 3)")
    
    args = parser.parse_args()
    
    print("TensorFlow Lite Model Benchmarking Tool for StudyBuddy")
    print("====================================================\n")
    
    # Check if model directory exists
    if not os.path.exists(args.model_dir):
        print(f"Error: Model directory not found: {args.model_dir}")
        return
    
    # Run benchmarks
    benchmark = ModelBenchmark(args.model_dir, args.iterations, args.warmup)
    benchmark.run_all_benchmarks()
    
    print("\nBenchmarking complete!")

if __name__ == "__main__":
    main() 