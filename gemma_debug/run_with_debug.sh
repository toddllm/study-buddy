#!/bin/bash

# Set up Android SDK environment
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "❌ ERROR: adb command not found. Is Android SDK platform-tools installed?"
    echo "Android SDK is expected at: $ANDROID_HOME"
    exit 1
else
    echo "✅ adb found at: $(which adb)"
fi

echo "Starting app with debugging enabled..."
adb logcat -c
adb shell "am force-stop com.example.studybuddy"
adb shell "am start -n com.example.studybuddy/.MainActivity"

echo "App started, waiting for crash or 60 seconds..."
# Start capturing logs
adb logcat > gemma_debug/crash_logcat.txt &
LOGCAT_PID=$!

# Wait max 60 seconds
sleep 60

# Kill logcat process
kill $LOGCAT_PID

echo "Debug logs captured to gemma_debug/crash_logcat.txt"
echo "Look for lines with [GEMMA_CPP] prefix for our custom logging"
