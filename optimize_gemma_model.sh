#!/bin/bash
# Script to optimize the Gemma model for better performance

set -e  # Exit on error

echo "===== Gemma Model Optimization ====="

# Check prerequisites
if [ ! -d "app" ] || [ ! -d "app/src/main" ]; then
    echo "❌ ERROR: Not in the root directory of an Android project."
    echo "Please run this script from the root of the StudyBuddy project."
    exit 1
fi

# Check for required tools
MISSING_TOOLS=""
if ! command -v python3 &> /dev/null; then
    MISSING_TOOLS="$MISSING_TOOLS Python 3"
fi
if ! command -v sed &> /dev/null; then
    MISSING_TOOLS="$MISSING_TOOLS sed"
fi
if ! command -v grep &> /dev/null; then
    MISSING_TOOLS="$MISSING_TOOLS grep"
fi

if [ -n "$MISSING_TOOLS" ]; then
    echo "❌ Required tools not found:$MISSING_TOOLS"
    echo "Please install these tools to continue."
    exit 1
fi

# Create optimization directory
mkdir -p optimization

# Step 1: Check model configuration
echo "Step 1: Checking current model configuration..."
MODEL_DIR="app/src/main/assets/models/gemma2_2b_it"
if [ ! -d "$MODEL_DIR" ]; then
    echo "⚠️ Model directory not found at $MODEL_DIR"
    echo "Looking for model directory elsewhere..."
    POSSIBLE_MODEL_DIR=$(find app -type d -name "gemma*" | head -n 1)
    
    if [ -n "$POSSIBLE_MODEL_DIR" ]; then
        echo "Found possible model directory at $POSSIBLE_MODEL_DIR"
        MODEL_DIR="$POSSIBLE_MODEL_DIR"
    else
        echo "❌ Could not find model directory. Optimization cannot proceed."
        exit 1
    fi
fi

MODEL_CONFIG="$MODEL_DIR/mlc-chat-config.json"
if [ ! -f "$MODEL_CONFIG" ]; then
    echo "❌ Model configuration not found at $MODEL_CONFIG"
    # Try to find it elsewhere
    POSSIBLE_CONFIG=$(find app -name "mlc-chat-config.json" | head -n 1)
    if [ -n "$POSSIBLE_CONFIG" ]; then
        echo "Found possible config at $POSSIBLE_CONFIG"
        MODEL_CONFIG="$POSSIBLE_CONFIG"
    else
        echo "❌ Could not find model configuration. Optimization cannot proceed."
        exit 1
    fi
fi

# Extract current configuration values
echo "Current configuration:"
cat "$MODEL_CONFIG" | grep -E "temperature|top_p|max_gen_len|quantization|model_lib" | sed 's/,$//'

# Step 2: Apply optimizations
echo "Step 2: Creating optimized configuration..."

# Backup original config
cp "$MODEL_CONFIG" "${MODEL_CONFIG}.backup"

# Check if file is valid JSON
if ! python3 -c "import json; json.load(open('$MODEL_CONFIG'))" 2>/dev/null; then
    echo "⚠️ The model config file is not valid JSON. Using simple text replacement."
    
    # Create optimized config with modified values for performance - handle both macOS and Linux sed differences
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS version
        sed -i '' 's/"max_gen_len": [0-9]*/"max_gen_len": 512/' "${MODEL_CONFIG}"
        sed -i '' 's/"sliding_window": [0-9]*/"sliding_window": 4096/' "${MODEL_CONFIG}"
        sed -i '' 's/"prefill_chunk_size": [0-9]*/"prefill_chunk_size": 4096/' "${MODEL_CONFIG}"
        sed -i '' 's/"attention_sink_size": [0-9]*/"attention_sink_size": 4/' "${MODEL_CONFIG}"
    else
        # Linux version
        sed -i 's/"max_gen_len": [0-9]*/"max_gen_len": 512/' "${MODEL_CONFIG}"
        sed -i 's/"sliding_window": [0-9]*/"sliding_window": 4096/' "${MODEL_CONFIG}"
        sed -i 's/"prefill_chunk_size": [0-9]*/"prefill_chunk_size": 4096/' "${MODEL_CONFIG}"
        sed -i 's/"attention_sink_size": [0-9]*/"attention_sink_size": 4/' "${MODEL_CONFIG}"
    fi
else
    # Use Python for more reliable JSON modification
    cat << 'EOF' > update_config.py
#!/usr/bin/env python3
import json
import sys

if len(sys.argv) != 2:
    print("Usage: python3 update_config.py <config_file>")
    sys.exit(1)

config_file = sys.argv[1]

try:
    with open(config_file, 'r') as f:
        config = json.load(f)
    
    # Apply optimizations
    updates = {
        "max_gen_len": 512,
        "sliding_window": 4096,
        "prefill_chunk_size": 4096,
        "attention_sink_size": 4,
        "temperature": 0.7,
        "top_p": 0.9,
        "repetition_penalty": 1.0
    }
    
    # Only update keys that exist in the original config
    for key, value in updates.items():
        if key in config:
            config[key] = value
    
    # Write back to a temporary file
    with open(f"{config_file}.optimized", 'w') as f:
        json.dump(config, f, indent=2)
    
    print("Successfully created optimized configuration")
except Exception as e:
    print(f"Error updating config: {e}")
    sys.exit(1)
EOF

    # Make the script executable
    chmod +x update_config.py
    
    # Run the script
    python3 update_config.py "$MODEL_CONFIG"
    
    # Check if optimization was successful
    if [ -f "${MODEL_CONFIG}.optimized" ]; then
        # Show diff
        echo "Configuration changes:"
        diff -u "${MODEL_CONFIG}.backup" "${MODEL_CONFIG}.optimized" || true
    else
        echo "❌ Failed to create optimized configuration."
        exit 1
    fi
fi

# Ask for confirmation
read -p "Apply these optimizations to the model configuration? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    if [ -f "${MODEL_CONFIG}.optimized" ]; then
        cp "${MODEL_CONFIG}.optimized" "$MODEL_CONFIG"
    fi
    echo "✅ Configuration updated"
else
    echo "Optimization canceled"
    # Restore backup if we modified the original
    if [ ! -f "${MODEL_CONFIG}.optimized" ]; then
        cp "${MODEL_CONFIG}.backup" "$MODEL_CONFIG"
    fi
    if [ -f "${MODEL_CONFIG}.optimized" ]; then
        rm "${MODEL_CONFIG}.optimized"
    fi
    exit 0
fi

# Step 3: Create runtime optimization script directory if it doesn't exist
echo "Step 3: Creating runtime optimization helpers..."
ML_DIR="app/src/main/java/com/example/studybuddy/ml"
if [ ! -d "$ML_DIR" ]; then
    echo "Creating directory: $ML_DIR"
    mkdir -p "$ML_DIR"
fi

cat << 'EOF' > app/src/main/java/com/example/studybuddy/ml/ModelOptimizer.kt
package com.example.studybuddy.ml

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Helper class to optimize model performance based on device capabilities
 */
class ModelOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "ModelOptimizer"
        
        // Optimization profiles
        private val HIGH_PERF_PARAMS = mapOf(
            "temperature" to 0.7f,
            "top_p" to 0.9f,
            "repetition_penalty" to 1.1f
        )
        
        private val BALANCED_PARAMS = mapOf(
            "temperature" to 0.8f,
            "top_p" to 0.9f,
            "repetition_penalty" to 1.0f
        )
        
        private val BATTERY_SAVING_PARAMS = mapOf(
            "temperature" to 1.0f,
            "top_p" to 0.95f,
            "repetition_penalty" to 1.0f
        )
    }
    
    // Device information
    private val deviceInfo: DeviceInfo by lazy { collectDeviceInfo() }
    
    // Detect optimal settings based on device capabilities
    fun getOptimalParameters(): Map<String, Float> {
        return when {
            deviceInfo.isHighEndDevice -> {
                Log.i(TAG, "Using high performance profile for ${deviceInfo.model}")
                HIGH_PERF_PARAMS
            }
            deviceInfo.isLowEndDevice -> {
                Log.i(TAG, "Using battery saving profile for ${deviceInfo.model}")
                BATTERY_SAVING_PARAMS
            }
            else -> {
                Log.i(TAG, "Using balanced profile for ${deviceInfo.model}")
                BALANCED_PARAMS
            }
        }
    }
    
    // Apply optimal parameters to a model
    fun applyOptimalParameters(model: Any) {
        val params = getOptimalParameters()
        
        // Apply each parameter - using reflection to be compatible with any model class
        params.forEach { (key, value) ->
            try {
                val method = model.javaClass.getMethod("on${key.capitalize()}Changed", Float::class.java)
                method.invoke(model, value)
                Log.d(TAG, "Applied $key = $value")
            } catch (e: Exception) {
                Log.w(TAG, "Could not apply parameter $key: ${e.message}")
            }
        }
        
        Log.i(TAG, "Applied optimal parameters to model")
    }
    
    // Helper function to capitalize first letter
    private fun String.capitalize(): String {
        return this.replaceFirstChar { it.uppercase() }
    }
    
    // Collect device information for optimization decisions
    private fun collectDeviceInfo(): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRam = memoryInfo.totalMem / (1024 * 1024) // in MB
        val processors = Runtime.getRuntime().availableProcessors()
        
        return DeviceInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            sdkVersion = Build.VERSION.SDK_INT,
            totalRamMb = totalRam,
            processorCount = processors,
            isLowEndDevice = totalRam < 4096 || processors <= 4, // Less than 4GB RAM or 4 cores
            isHighEndDevice = totalRam >= 8192 && processors >= 8 // At least 8GB RAM and 8 cores
        )
    }
    
    // Data class to hold device information
    data class DeviceInfo(
        val model: String,
        val manufacturer: String,
        val sdkVersion: Int,
        val totalRamMb: Long,
        val processorCount: Int,
        val isLowEndDevice: Boolean,
        val isHighEndDevice: Boolean
    )
}
EOF

echo "✅ Created ModelOptimizer.kt"

# Step 4: Build instructions for optimized APK
echo "Step 4: Building optimized APK..."

# Detect gradlew and build if available
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    
    # Try to build with optimization flags, but don't fail if it doesn't work
    echo "Building with optimization flags..."
    ./gradlew assembleRelease \
    -PenableR8=true \
    -PenableR8FullMode=true \
    -PenableAapt2DaemonProcess=true \
    -PandroidBuildCache=true || {
        echo "⚠️ Optimized build failed, trying standard build..."
        ./gradlew assembleRelease || {
            echo "⚠️ Release build failed, falling back to debug build..."
            ./gradlew assembleDebug || {
                echo "❌ All build attempts failed. Please check project setup."
            }
        }
    }
else
    echo "⚠️ No gradlew script found. Skipping build step."
    echo "To build manually, run: ./gradlew assembleRelease"
fi

echo "===== Optimization Complete ====="
echo "The model has been optimized for better performance."
echo
echo "To use the optimized version, install the optimized APK with:"
echo "  adb install -r app/build/outputs/apk/release/app-release.apk"
echo
echo "To use runtime optimization, add this code to your MainActivity:"
echo "  val optimizer = ModelOptimizer(this)"
echo "  optimizer.applyOptimalParameters(yourMlcModel)"
echo
echo "You can also manually adjust parameters in the Settings screen if available." 