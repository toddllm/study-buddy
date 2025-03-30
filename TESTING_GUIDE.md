# StudyBuddy Real Gemma Implementation Testing Guide

This guide walks you through testing the real Gemma implementation in the StudyBuddy app.

## Prerequisites

- Android device with at least 4GB RAM
- Android Studio installed
- USB debugging enabled on device
- Device connected via USB
- ADB (Android Debug Bridge) installed and in your PATH

## Verification

First, verify that your implementation is using the real Gemma model, not a mock:

```bash
./verify_real_implementation.sh
```

This script will:
1. Check source files for mock/stub implementation indicators
2. Verify shared library symbols
3. Check model files are present and complete
4. Produce a verification report

## Quick Test

For a quick verification that the real Gemma implementation is working:

```bash
./test_gemma_implementation.sh
```

This script will:
1. Build and install the app
2. Check logs for real implementation indicators
3. Send a test prompt and verify the model responds
4. Provide a summary of test results

## Performance Testing

To evaluate the performance of the Gemma model:

```bash
./performance_monitoring.sh
```

This script will:
1. Monitor CPU and memory usage during model operation
2. Time the model loading and response generation
3. Generate a detailed performance report with metrics
4. Identify potential bottlenecks or issues

## Optimization

To optimize the model for better performance:

```bash
./optimize_gemma_model.sh
```

This script will:
1. Find and optimize model configuration parameters
2. Create the ModelOptimizer helper class that adjusts parameters based on device capabilities
3. Build an optimized APK with R8/ProGuard
4. Provide instructions for using the optimization features

## Manual Testing

If you prefer to test manually:

1. Build and install the app:
   ```bash
   ./build_real_implementation.sh
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Launch the app and try these test prompts:
   - "What is quantum physics?"
   - "Summarize the key points of photosynthesis"
   - "How do I solve a quadratic equation?"

3. Check log output with more detailed filters:
   ```bash
   adb logcat -v threadtime MLC_CHAT_MODULE:V MlcJniWrapper:V SimpleMlcModel:V StudyBuddy:V *:S
   ```

## Expected Behavior

A successful implementation should:

1. Load the real Gemma model library (look for "native library loaded" messages)
2. Initialize the model without errors
3. Generate coherent responses to prompts
4. Show fast token generation after the initial response

## Troubleshooting

If the implementation doesn't work:

1. Check if model files are correctly downloaded:
   ```bash
   adb shell ls -la /data/data/com.example.studybuddy/files/models/
   ```

2. Verify library loading:
   ```bash
   adb logcat | grep -i "dlopen\|library\|model"
   ```

3. Check permissions on device:
   ```bash
   adb shell pm list permissions | grep com.example.studybuddy
   ```

4. Run with clean build:
   ```bash
   ./gradlew clean
   ./build_real_implementation.sh
   ```

5. Check for common errors:
   - Missing model files (see verification script)
   - Incompatible library format (ARM vs x86)
   - Device memory limitations (need at least 4GB RAM)
   - Permissions issues (storage, etc.)

## Model Tuning

After verifying the implementation works, you can tune the model by:

1. Adjusting temperature parameter (0.5-1.0)
2. Changing top_p value (0.7-1.0)
3. Modifying max token generation limits

Add this code to your activity to automatically optimize based on device:

```kotlin
val optimizer = ModelOptimizer(this)
optimizer.applyOptimalParameters(mlcModel)
```

## Report Issues

If you encounter issues, collect logs and report them with:
- Device information (model, Android version, RAM)
- Complete logcat output (filtered for relevant tags)
- Steps to reproduce the issue
- Performance report from performance_monitoring.sh 