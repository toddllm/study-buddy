# StudyBuddy Development Guide

This document provides instructions for developing and contributing to the StudyBuddy app.

## Development Environment Setup

### Prerequisites

1. Android Studio Arctic Fox or newer
2. Android SDK 24+
3. NDK 26.1.10909125
4. CMake 3.22+
5. Git LFS (for handling large model files)
6. Python 3.8+ (for model compilation scripts)

### Setting Up Your Development Environment

1. Clone the repository:
   ```
   git clone https://github.com/toddllm/study-buddy.git
   ```

2. Install the required NDK version in Android Studio:
   - Open Android Studio
   - Go to Settings > Appearance & Behavior > System Settings > Android SDK
   - Select the "SDK Tools" tab
   - Check "Show Package Details" at the bottom right
   - Expand "NDK (Side by side)" and select version 26.1.10909125
   - Click "Apply" to install

3. Set up environment variables:
   ```bash
   export ANDROID_SDK_ROOT=/path/to/android/sdk
   export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/26.1.10909125
   ```

## Building the Project

### Building from Android Studio

1. Open the project in Android Studio
2. Select "Build > Make Project" to build
3. Select "Run > Run app" to install and run on a connected device or emulator

### Building from Command Line

Build the debug version:
```bash
./gradlew assembleDebug
```

Install on a connected device:
```bash
./gradlew installDebug
```

Run the app:
```bash
adb shell am start -n com.example.studybuddy/.MainActivity
```

## JNI Development

### Modifying Native Code

1. Edit files in `app/src/main/cpp/`
2. Rebuild with `./gradlew assembleDebug`
3. Install with `./gradlew installDebug`

### Adding New Native Methods

1. Add the method declaration in `MlcLlmBridge.kt`
2. Implement the method in `mlc_jni/mlc_llm_jni.cpp`
3. Update `CMakeLists.txt` if necessary

## Working with Models

### Testing a New Model

1. Compile the model using `compile_model_for_android.py`
2. Add the model files to `app/src/main/assets/models/`
3. Update the model path in the app:
   - Edit `app/src/main/java/com/example/studybuddy/ml/ModelManager.kt`
   - Change the model path in the `loadModel()` method

### Optimizing Model Performance

1. Adjust quantization settings in `compile_model_for_android.py`
2. Set appropriate generation parameters in `MlcLlmBridge.kt`
3. Test on target devices to find optimal settings

## Debugging

### Logcat Filters

Use these filters to debug specific components:

- Native code: `adb logcat *:S MLC_LLM_JNI:V`
- Model loading: `adb logcat *:S ModelManager:V`
- App UI: `adb logcat *:S StudyBuddyApp:V`

### Common Issues

1. Library loading issues:
   - Check if all libraries are properly included in `app/src/main/jniLibs/arm64-v8a/`
   - Verify loading order in `MlcLlmBridge.kt`

2. Model loading issues:
   - Check model path in `ModelManager.kt`
   - Verify model files in assets directory

3. Performance issues:
   - Add logging to measure initialization and inference times
   - Consider using smaller models or more aggressive quantization

## Testing

### Unit Tests

Run unit tests:
```bash
./gradlew test
```

### Instrumented Tests

Run instrumented tests on a device:
```bash
./gradlew connectedAndroidTest
```

## Release Process

1. Update version code and name in `app/build.gradle`
2. Build release APK: `./gradlew assembleRelease`
3. Sign the APK:
   ```bash
   jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
      -keystore your-key.keystore \
      app/build/outputs/apk/release/app-release-unsigned.apk \
      alias_name
   ```
4. Optimize the APK:
   ```bash
   $ANDROID_SDK_ROOT/build-tools/30.0.3/zipalign -v 4 \
      app/build/outputs/apk/release/app-release-unsigned.apk \
      studybuddy.apk
   ```

## Profiling

1. Enable profiling:
   ```xml
   <!-- Add to AndroidManifest.xml -->
   <application android:debuggable="true">
   ```

2. Start profiling:
   ```bash
   adb shell am profile start com.example.studybuddy /data/local/tmp/studybuddy.trace
   ```

3. Perform actions in the app

4. Stop profiling:
   ```bash
   adb shell am profile stop com.example.studybuddy
   ```

5. Pull the trace file:
   ```bash
   adb pull /data/local/tmp/studybuddy.trace
   ```

6. Open in Android Studio: "Profiler > Load from file" 