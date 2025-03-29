#!/usr/bin/env python3
"""
TensorFlow Lite Model Tester for StudyBuddy

This script allows you to test the models used in the StudyBuddy app
outside of the Android environment. This is useful for debugging and
evaluating model performance.

Requirements:
- tensorflow
- numpy
- pillow (PIL)
"""

import os
import sys
import argparse
import numpy as np
from PIL import Image
import tensorflow as tf

def load_labels(label_path):
    """Load labels from file"""
    with open(label_path, 'r') as f:
        return [line.strip() for line in f.readlines()]

def preprocess_image(image_path, input_size=224):
    """Preprocess an image for MobileNet"""
    img = Image.open(image_path).convert('RGB')
    img = img.resize((input_size, input_size))
    img_array = np.array(img, dtype=np.float32)
    
    # Normalize image using ImageNet mean and std
    mean = np.array([0.485, 0.456, 0.406]) * 255
    std = np.array([0.229, 0.224, 0.225]) * 255
    normalized = (img_array - mean) / std
    
    # Add batch dimension
    return np.expand_dims(normalized, axis=0)

def test_image_classification(model_path, image_path, label_path=None):
    """Test image classification with MobileNet"""
    print(f"Loading model: {model_path}")
    print(f"Testing image: {image_path}")
    
    # Load TFLite model and allocate tensors
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    
    # Get input and output tensors
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    # Check input shape and type
    input_shape = input_details[0]['shape']
    print(f"Input shape: {input_shape}")
    
    # Preprocess image based on input shape
    input_size = input_shape[1]  # Assuming square image
    input_data = preprocess_image(image_path, input_size)
    
    # Set input tensor
    interpreter.set_tensor(input_details[0]['index'], input_data)
    
    # Run inference
    print("Running inference...")
    interpreter.invoke()
    
    # Get output tensor
    output_data = interpreter.get_tensor(output_details[0]['index'])
    results = np.squeeze(output_data)
    
    # Get top results
    top_k = results.argsort()[-5:][::-1]
    
    # Print results
    print("\nClassification results:")
    if label_path and os.path.exists(label_path):
        labels = load_labels(label_path)
        for i, idx in enumerate(top_k):
            if idx < len(labels):
                print(f"  {i+1}. {labels[idx]}: {results[idx]*100:.1f}%")
            else:
                print(f"  {i+1}. Class {idx}: {results[idx]*100:.1f}%")
    else:
        for i, idx in enumerate(top_k):
            print(f"  {i+1}. Class {idx}: {results[idx]*100:.1f}%")

def preprocess_text(text, max_seq_len=256):
    """
    Simple text preprocessing - in a real implementation, 
    this would properly tokenize text for the specific model
    """
    # This is a simplification - real implementation would use the 
    # correct tokenizer for the specific model
    return text[:max_seq_len]

def test_text_classification(model_path, text, label_path=None):
    """Test text classification"""
    print(f"Loading model: {model_path}")
    print(f"Testing text: {text}")
    
    try:
        # For demo purposes, we'll simulate inference
        # In a full implementation, we would:
        # 1. Tokenize the text correctly for the model
        # 2. Create input tensors
        # 3. Run inference
        # 4. Process results
        
        print("\nText classification not fully implemented in this demo")
        print("In a real implementation, we would:")
        print("1. Tokenize the text correctly for the model")
        print("2. Create input tensors")
        print("3. Run inference")
        print("4. Process results")
        
        # Simulated results
        print("\nSimulated classification results:")
        print("  1. Science: 72.5%")
        print("  2. Technology: 15.8%")
        print("  3. Business: 7.2%")
        print("  4. Politics: 3.1%")
        print("  5. Sports: 1.4%")
        
    except Exception as e:
        print(f"Error: {str(e)}")

def test_bert_qa(model_path, context, question):
    """Test BERT QA model"""
    print(f"Loading model: {model_path}")
    print(f"Context: {context}")
    print(f"Question: {question}")
    
    try:
        # For demo purposes, we'll simulate inference
        print("\nBERT QA not fully implemented in this demo")
        print("In a real implementation, we would:")
        print("1. Tokenize the context and question correctly for the model")
        print("2. Create input tensors")
        print("3. Run inference")
        print("4. Process start/end logits to extract the answer")
        
        # Simulated results
        print("\nSimulated answer:")
        print("  The mitochondria is the powerhouse of the cell.")
        
    except Exception as e:
        print(f"Error: {str(e)}")

def main():
    parser = argparse.ArgumentParser(description="Test TensorFlow Lite models for StudyBuddy")
    parser.add_argument("--model_type", choices=["image", "text", "qa"], required=True,
                        help="Type of model to test")
    parser.add_argument("--model_path", required=True,
                        help="Path to the TFLite model file")
    parser.add_argument("--label_path", 
                        help="(Optional) Path to label file for classification models")
    parser.add_argument("--input", required=True,
                        help="Input: image path for 'image', text for 'text', context for 'qa'")
    parser.add_argument("--question", 
                        help="Question text for QA model")
    
    args = parser.parse_args()
    
    print("TensorFlow Lite Model Tester for StudyBuddy")
    print("==========================================\n")
    
    if args.model_type == "image":
        test_image_classification(args.model_path, args.input, args.label_path)
    elif args.model_type == "text":
        test_text_classification(args.model_path, args.input, args.label_path)
    elif args.model_type == "qa":
        if not args.question:
            print("Error: --question is required for QA model")
            return
        test_bert_qa(args.model_path, args.input, args.question)
    else:
        print(f"Unknown model type: {args.model_type}")

if __name__ == "__main__":
    main() 