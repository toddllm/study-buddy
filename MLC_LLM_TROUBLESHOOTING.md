# MLC-LLM Troubleshooting Guide

This guide provides solutions for common issues encountered when using MLC-LLM integration in StudyBuddy.

## Common Issues

### Installation Problems

#### UnsatisfiedLinkError when loading libraries

**Problem:** App crashes with `UnsatisfiedLinkError: dlopen failed: library "libtvm_runtime.so" not found`

**Solutions:**
1. Check that all required libraries are included in the `app/src/main/jniLibs/arm64-v8a/` directory
2. Verify library loading order in `MlcLlmBridge.kt`
3. Make sure the build.gradle configuration includes the correct ABI filters

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters 'arm64-v8a'
        }
    }
}
```

#### Library incompatibility

**Problem:** `java.lang.UnsatisfiedLinkError: ... version 'GLIBCXX_X.X.X' not found`

**Solution:** Rebuild the native libraries with the correct NDK version (NDK 26.1.10909125 recommended)

### Runtime Issues

#### Out of memory errors

**Problem:** `java.lang.OutOfMemoryError` when initializing model

**Solutions:**
1. Increase app memory limit in AndroidManifest.xml:
   ```xml
   <application android:largeHeap="true" ...>
   ```
2. Use a smaller model or a more efficient quantization
3. Close other apps before running StudyBuddy

#### Slow performance

**Problem:** Model inference is taking too long

**Solutions:**
1. Check device temperature (thermal throttling may occur)
2. Close background applications
3. Reduce the generation parameters (max_gen_len, temperature)
4. Use a smaller model or more aggressive quantization

#### Incorrect or poor quality responses

**Problem:** Model generates nonsensical or low-quality responses

**Solutions:**
1. Verify the model was correctly downloaded and extracted
2. Check that the tokenizer file is properly loaded
3. Try adjusting the temperature (lower for more focused responses)
4. Make sure the prompt is clear and well-formatted

### Model Loading Issues

#### Model not found

**Problem:** `Error: Failed to open model file at path...`

**Solutions:**
1. Check that the model files are correctly placed in the assets directory
2. Verify the model path is correctly passed to the initialization method
3. Check file permissions for the app's storage directory

#### Model too large

**Problem:** Model fails to load due to size constraints

**Solutions:**
1. Use a more aggressive quantization method 
2. Split the model into smaller chunks
3. Try a smaller model variant (2B instead of 7B, for example)

## Debugging Techniques

### Enable Debug Logging

Add the following to your application class to enable detailed logging:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Set log level to Debug
        Log.d("StudyBuddy", "Debug logging enabled")
        
        // Additional initialization
    }
}
```

### Check Native Logs

View native logs using ADB:

```
adb logcat *:W MLC_LLM_JNI:V
```

### Profile Memory Usage

Monitor memory usage:

```
adb shell dumpsys meminfo com.example.studybuddy
```

## Support Resources

1. MLC-LLM GitHub repository: https://github.com/mlc-ai/mlc-llm
2. TVM documentation: https://tvm.apache.org/docs/
3. StudyBuddy GitHub issues: https://github.com/toddllm/study-buddy/issues 