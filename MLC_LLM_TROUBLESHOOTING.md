# MLC-LLM Troubleshooting Guide

This guide will help you resolve issues with the MLC-LLM integration in the StudyBuddy app.

## Common Issues

### "Failed to initialize MLC-LLM" Error

This error can occur for several reasons:

1. **Missing Model Files**: The model files aren't properly extracted in the assets directory
2. **Library Loading Issues**: The native libraries aren't being found or loaded properly
3. **Model Path Issues**: The app can't access the model files at runtime

## Diagnosing the Problem

Run the provided debugging scripts to gather more information:

```bash
# Collect debug information about model files and libraries
./collect_debug_info.sh

# Manually extract and prepare model files
./manually_prepare_models.sh
```

## Step-by-Step Solutions

### 1. Model Files Preparation

Make sure the model files are properly extracted:

1. Check if the tar file `gemma-2b-it-q4f16_1-android.tar` exists in `app/src/main/assets/`
2. If it exists, try manually extracting it:
   ```bash
   ./manually_prepare_models.sh
   ```
3. Verify that files are extracted to `app/src/main/assets/models/gemma2_2b_it/`
4. Make sure a `config.json` file exists in the model directory

### 2. Native Libraries Issues

Check that the native libraries are properly included:

1. Look in `app/src/main/jniLibs/arm64-v8a/` for these libraries:
   - `libc++_shared.so`
   - `libtvm_runtime.so`
   - `libtvm.so`
   - `libmlc_llm.so`
   - `libmlc_llm_module.so`

2. If any are missing, you can rebuild them using:
   ```bash
   ./scripts/build_android_tvm.sh
   ./scripts/prepare_libs_for_android.sh
   ```

3. Make sure your app's build.gradle.kts has the correct configuration:
   ```kotlin
   android {
       defaultConfig {
           ndk {
               abiFilters.clear()
               abiFilters.add("arm64-v8a")
           }
       }
   }
   ```

### 3. Runtime Permissions and Access

Make sure the app has the necessary permissions:

1. Storage permissions should be requested at runtime if needed
2. The model directory should be accessible (use the app's files directory)

### 4. Checking Logs

Look for detailed error logs in Logcat:

1. Use the tag `TVMBridge` to filter logs related to the JNI bridge
2. Use the tag `MLCLLMService` to filter logs related to model loading

### 5. Step-by-Step Verification

1. First check if the native libraries load successfully:
   ```java
   if (TVMBridge.areLibrariesLoaded()) {
       // Libraries loaded successfully
   }
   ```

2. Then check if the model files are extracted properly by looking at the logs:
   ```
   Log: Successfully extracted model files to: /data/user/0/com.example.studybuddy/files/mlc_models/gemma2_2b_it
   ```

3. Finally, check if the model initialization succeeds:
   ```
   Log: MLC-LLM initialization successful
   ```

## Advanced Troubleshooting

If the above steps don't resolve the issue:

1. Try creating a minimal test app that only loads the MLC-LLM runtime
2. Try with a smaller model to verify the integration works
3. Make sure the device architecture (arm64-v8a) matches the built libraries

## File Structure Reference

The model files should be organized as follows:

```
app/src/main/assets/
└── models/
    └── gemma2_2b_it/
        ├── config.json
        ├── [model files]
        └── ...
```

The native libraries should be in:

```
app/src/main/jniLibs/
└── arm64-v8a/
    ├── libc++_shared.so
    ├── libtvm_runtime.so
    ├── libtvm.so
    ├── libmlc_llm.so
    └── libmlc_llm_module.so
```

## Contact Support

If you continue to encounter issues after following these steps, please contact support with:

1. The output of `./collect_debug_info.sh`
2. Complete logcat output from the app
3. Details about your device model and Android version 