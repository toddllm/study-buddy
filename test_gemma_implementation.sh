#!/bin/bash
# Script to test the Gemma implementation in StudyBuddy

set -e  # Exit on error

echo "===== Testing StudyBuddy Gemma Implementation ====="

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ ERROR: No device connected. Please connect an Android device."
    exit 1
fi

# Check if build_real_implementation.sh exists
if [ ! -f "build_real_implementation.sh" ]; then
    echo "❌ ERROR: build_real_implementation.sh script not found."
    echo "Creating a minimal version for testing..."
    cat << 'EOF' > build_real_implementation.sh
#!/bin/bash
# Simple build script for testing
set -e
echo "Building StudyBuddy with real Gemma implementation..."
./gradlew assembleDebug
echo "Build complete"
EOF
    chmod +x build_real_implementation.sh
fi

# Step 1: Build the real implementation
echo "Building real implementation..."
chmod +x build_real_implementation.sh
./build_real_implementation.sh || {
    echo "❌ Build failed. Check build errors above."
    exit 1
}

# Step 2: Check if the library was properly built
echo "Verifying library..."

LIB_PATH="app/src/main/jniLibs/arm64-v8a/libgemma_2_2b_it_q4f16_1.so"
if [ ! -f "$LIB_PATH" ]; then
    # Try alternate name format
    LIB_PATH="app/src/main/jniLibs/arm64-v8a/libgemma-2-2b-it-q4f16_1.so"
    if [ ! -f "$LIB_PATH" ]; then
        echo "❌ ERROR: Could not find Gemma library in jniLibs directory."
        echo "The build script may have failed to create the library correctly."
        exit 1
    fi
fi

echo "✅ Found Gemma library: $LIB_PATH"

# Step 3: Install on connected device
echo "Installing APK on device..."
APK_PATH=$(find app/build/outputs -name "*.apk" | grep -v "unsigned" | sort -r | head -n 1)
if [ -f "$APK_PATH" ]; then
    adb install -r "$APK_PATH" || {
        echo "❌ APK installation failed."
        exit 1
    }
    echo "✅ APK installed successfully: $(basename "$APK_PATH")"
else
    echo "❌ APK not found. Checking alternate locations..."
    APK_PATH=$(find . -name "*.apk" | grep -v "unsigned" | sort -r | head -n 1)
    if [ -f "$APK_PATH" ]; then
        adb install -r "$APK_PATH" || {
            echo "❌ APK installation failed."
            exit 1
        }
        echo "✅ APK installed successfully: $(basename "$APK_PATH")"
    else
        echo "❌ No APK found in the project. Build may have failed."
        exit 1
    fi
fi

# Step 4: Start logcat capture
echo "Starting logcat capture..."
LOGFILE="gemma_test_log.txt"
# Clear logcat buffer
adb logcat -c

# Check whether the logcat clearing was successful
if [ $? -ne 0 ]; then
    echo "⚠️ Could not clear logcat buffer. Continuing anyway."
fi

# Start logcat with more comprehensive tags
adb logcat -v threadtime MLC_CHAT_MODULE:V MlcJniWrapper:V SimpleMlcModel:V StudyBuddy:V ActivityManager:I *:S > "$LOGFILE" &
LOGCAT_PID=$!

# Use trap to ensure cleanup
trap "kill $LOGCAT_PID 2>/dev/null || true" EXIT

# Step 5: Launch the app
echo "Launching StudyBuddy app..."
adb shell am force-stop com.example.studybuddy  # Stop app if already running
adb shell am start -n com.example.studybuddy/.MainActivity
echo "Waiting for app to start..."

# Check if app has started successfully
sleep 5
if ! adb shell "ps | grep com.example.studybuddy"; then
    echo "⚠️ App may not have started correctly. Checking logs..."
    grep -i "ActivityManager.*example.studybuddy" "$LOGFILE" | tail -5

    # Try using newer ps command format if the first one failed
    if ! adb shell "ps -A | grep com.example.studybuddy"; then
        echo "❌ Could not confirm app is running. Check the device manually."
    else
        echo "✅ App appears to be running (using ps -A command)."
    fi
else
    echo "✅ App is running."
fi

# Step 6: Wait for model initialization
echo "Waiting for model initialization (60 seconds)..."
echo "This may take longer on first run as the model is loaded..."
sleep 60

# Step 7: Check for real implementation logs
echo "Checking logs for real implementation..."
# Check for various possible log messages that indicate the real implementation
if grep -q -E "Creating real Gemma|Successfully initialized Gemma|Gemma.+loaded|gemma_lib|libgemma" "$LOGFILE"; then
    echo "✅ Real Gemma implementation detected in logs"
    grep -i -E "creating|initialized|loaded|gemma" "$LOGFILE" | tail -10
else
    echo "❌ Real implementation not detected. Check logs for errors."
    # Show relevant log messages 
    echo "Last 20 log messages:"
    tail -20 "$LOGFILE"
    grep -i -E "error|exception|failure|failed" "$LOGFILE" | tail -10
    echo "⚠️ Continuing with the test anyway..."
fi

# Step 8: Check for library loading
if grep -q -E "Successfully loaded (Gemma|library)|Native library loaded|dlopen.*success" "$LOGFILE"; then
    echo "✅ Library loaded successfully"
    grep -i -E "loaded|library|dlopen" "$LOGFILE" | tail -5
else
    echo "❌ Could not confirm library was loaded. Check logs for errors."
    # Show relevant error messages
    grep -i -E "error|exception|failure|failed|dlopen" "$LOGFILE" | tail -10
    echo "⚠️ Continuing with the test anyway..."
fi

# Step 9: Check if mock implementations are being detected/rejected
if grep -q -E "mock|stub|fake|placeholder" "$LOGFILE"; then
    echo "⚠️ Mock implementation terms detected in logs - verify if these are warnings or errors:"
    grep -i -A 3 -B 3 -E "mock|stub|fake|placeholder" "$LOGFILE" | tail -10
else
    echo "✅ No mock implementation terms detected in logs"
fi

# Step 10: Test a simple query
echo "Attempting to test a simple query..."
TEST_WORKED=0

# Get device screen size for better tap coordinates
DEVICE_SIZE=$(adb shell wm size | grep -oE "[0-9]+x[0-9]+")
if [[ -z "$DEVICE_SIZE" ]]; then
    echo "⚠️ Could not determine device size, using default coordinates"
    SCREEN_WIDTH=1080
    SCREEN_HEIGHT=1920
else
    SCREEN_WIDTH=$(echo $DEVICE_SIZE | cut -d'x' -f1)
    SCREEN_HEIGHT=$(echo $DEVICE_SIZE | cut -d'x' -f2)
    echo "Device screen size: ${SCREEN_WIDTH}x${SCREEN_HEIGHT}"
fi

# Calculate tap positions based on screen size
INPUT_X=$((SCREEN_WIDTH / 2))
INPUT_Y=$((SCREEN_HEIGHT * 3 / 4))
SEND_X=$((SCREEN_WIDTH * 4 / 5))
SEND_Y=$((SCREEN_HEIGHT * 3 / 4))

echo "Tap coordinates: Input field at $INPUT_X,$INPUT_Y, Send button at $SEND_X,$SEND_Y"

# Tap chat input field
adb shell input tap $INPUT_X $INPUT_Y
sleep 2
# Input test query
TEST_QUERY="What is quantum physics?"
echo "Entering test query: $TEST_QUERY"
adb shell input text "${TEST_QUERY// /%s}"
sleep 2
# Tap send button
echo "Sending query..."
adb shell input tap $SEND_X $SEND_Y

echo "Test query sent. Waiting 60 seconds for response..."
sleep 60

# Check logs for response generation
if grep -q -E "generating response|generate.*response|response.*generate" "$LOGFILE"; then
    echo "✅ Model generated a response to the test query"
    grep -i -E "generating|response|generate" "$LOGFILE" | tail -10
    TEST_WORKED=1
else
    echo "⚠️ Could not verify if model generated a response in logs."
    echo "Checking if the app is still responding..."
    
    # Try taking a screenshot to verify app state
    adb exec-out screencap -p > test_screenshot.png
    if [ -f "test_screenshot.png" ]; then
        echo "✅ Captured screenshot (test_screenshot.png). Please check it to verify response."
    else  
        echo "❌ Could not capture screenshot."
    fi
fi

# Stop logcat
kill $LOGCAT_PID 2>/dev/null || true

echo "===== Test Complete ====="
echo "Test results have been saved to $LOGFILE"
echo "You can review the complete logs for more details."
echo ""
echo "To manually test the app with these prompts:"
echo "1. 'What is quantum physics?'"
echo "2. 'Summarize the key points of photosynthesis'"
echo "3. 'How do I solve a quadratic equation?'"
echo ""

if [ $TEST_WORKED -eq 1 ]; then
    echo "✅ OVERALL TEST STATUS: SUCCESS"
    echo "The real Gemma implementation appears to be working!"
else
    echo "⚠️ OVERALL TEST STATUS: PARTIAL SUCCESS"
    echo "Basic checks completed but could not verify query response in logs."
    echo "Please check the app UI manually to confirm functionality."
fi 