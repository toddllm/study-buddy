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

# Set properties
echo "Setting device properties for debugging..."
adb shell setprop debug.generate-debug-info true
adb shell setprop dalvik.vm.extra-opts -Xeditaging:events=segfault
adb shell setprop debug.atrace.tags.enableflags 0x1fffff

echo "✅ Debug properties set successfully"
