# StudyBuddy Model Testing

This directory contains tools for testing the TensorFlow Lite models used in the StudyBuddy Android app outside of the Android environment.

## Prerequisites

To use these tools, you need to have Python installed with the following libraries:

```bash
pip install tensorflow numpy pillow
```

## Available Models

The app uses three TensorFlow Lite models:

1. **mobilenet_v1.tflite** - MobileNet image classification model (size: ~4MB)
2. **text_classification.tflite** - Text classification model (size: ~0.7MB)
3. **mobilebert_qa.tflite** - MobileBERT question answering model (size: ~0.3MB)

## Extracting Models

First, extract the models from the assets directory or APK file:

```bash
# Extract from assets directory
python extract_models.py --assets_dir app/src/main/assets --output_dir models

# Or extract from APK (if already built)
python extract_models.py --apk_path app/build/outputs/apk/debug/app-debug.apk --output_dir models
```

If you run `extract_models.py` without parameters, it will try to find the assets directory or APK automatically.

## Testing Models

### 1. Image Classification Model

Test the MobileNet image classification model with an image:

```bash
python model_tester.py --model_type image --model_path models/mobilenet_v1.tflite --input path/to/image.jpg
```

For better results with class labels, download the ImageNet labels and use:

```bash
# Download ImageNet labels
curl -O https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt

# Test with labels
python model_tester.py --model_type image --model_path models/mobilenet_v1.tflite --input path/to/image.jpg --label_path ImageNetLabels.txt
```

### 2. Text Classification Model

Test the text classification model:

```bash
python model_tester.py --model_type text --model_path models/text_classification.tflite --input "This is an article about science and technology"
```

### 3. Question Answering Model

Test the MobileBERT QA model:

```bash
python model_tester.py --model_type qa --model_path models/mobilebert_qa.tflite --input "The mitochondria is the powerhouse of the cell." --question "What is the function of mitochondria?"
```

## Sample Images for Testing

You can use any images for testing, but here are some suggestions for different categories:

- **Animals**: dog, cat, bird, horse, elephant
- **Objects**: car, chair, bottle, laptop, book
- **Scenes**: beach, mountain, forest, city, room
- **Food**: apple, banana, pizza, cake, salad

## Interpreting Results

The model tester provides classification results with confidence scores. For the image model, these represent the probability of each class. For text models, they indicate the likelihood of different topics or categories.

## Troubleshooting

If you encounter issues:

1. Make sure the models are correctly extracted and have the expected file sizes
2. Verify that the input image is readable and in a common format (JPG, PNG)
3. Check that all dependencies are installed (`tensorflow`, `numpy`, `pillow`)
4. For QA models, ensure both context and question are provided

## Advanced Usage

For more advanced use cases, you can modify the Python scripts to:

- Batch process multiple images
- Save results to a file
- Compare model performance with different inputs
- Integrate with your own testing pipeline

## Implementation Notes

These testing tools provide a simplified interface to the TensorFlow Lite models. The actual Android app implementation may have additional preprocessing or postprocessing steps specific to the mobile environment. 