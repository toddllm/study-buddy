# StudyBuddy with MLC-LLM

An Android application that uses on-device machine learning to analyze images and text to provide educational responses using Google's Gemma 2 model powered by MLC-LLM.

## Features

- Text extraction from images with ML Kit OCR
- Image classification with TensorFlow Lite (as fallback)
- On-device Language processing with Google's Gemma 2 (2B parameters) using MLC-LLM
- Completely runs on-device (no server required)
- Fallback mechanisms if primary models aren't available

## Setup MLC-LLM

To properly integrate MLC-LLM and the Gemma 2 model, follow these steps:

### Prerequisites

- Python 3.8+ with pip
- Git
- Android Studio
- 10+ GB of free disk space for building
- A Hugging Face account and token (optional but recommended)

### Step 1: Run the Setup Script

The provided `setup_mlc_llm.sh` script handles downloading and building MLC-LLM:

```bash
# Make the script executable
chmod +x setup_mlc_llm.sh

# Run the script
./setup_mlc_llm.sh
```

This script:
1. Clones the MLC-LLM repository
2. Sets up a Python environment
3. Installs dependencies
4. Builds MLC-LLM for Android
5. Copies the necessary files to your project

### Step 2: Get a Hugging Face Token (Optional)

If you want to access gated models or have better download rates:

1. Create or log into your [Hugging Face account](https://huggingface.co/login)
2. Go to https://huggingface.co/settings/tokens
3. Create a new token with "read" permissions
4. Copy this token to use in the app

### Step 3: Build and Run the App

Open the project in Android Studio and run it on your Pixel 8 Pro. When first launched, the app will:

1. Display a download screen for the Gemma 2 model
2. Allow you to enter your Hugging Face token (optional)
3. Download and prepare the model (only needed once)
4. Show the main app interface when ready

## Troubleshooting

If you encounter any issues:

1. **Build errors**: Make sure Java 17 is installed and selected in your project
2. **Download issues**: Check internet connection and verify your Hugging Face token
3. **Memory errors**: The app requires a device with at least 4GB of RAM
4. **Model crashes**: The app will automatically fall back to TensorFlow Lite models

## License

This project uses MLC-LLM under the Apache 2.0 license and Google's Gemma 2 model under the [Gemma Terms of Use](https://ai.google.dev/gemma/terms). 