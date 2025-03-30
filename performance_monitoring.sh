#!/bin/bash
# Performance monitoring for Gemma implementation

echo "===== Gemma Performance Monitoring ====="

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ ERROR: No device connected. Please connect an Android device."
    exit 1
fi

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

# Start time tracking
start_time=$(date +%s)

# Create output directory
mkdir -p performance_logs

# Start monitoring CPU and memory usage
echo "Starting performance monitoring..."

# Make sure the app isn't already running
adb shell am force-stop com.example.studybuddy

# Try to capture memory info, handling potential device differences
adb shell "while true; do echo -n \"\$(date '+%H:%M:%S') \"; dumpsys meminfo com.example.studybuddy | grep -E 'TOTAL|Total PSS'; sleep 1; done" > performance_logs/memory_usage.log 2>/dev/null &
MEM_PID=$!

# Try to capture CPU usage, handling potential device differences
adb shell "top -d 1 -b | grep com.example.studybuddy" > performance_logs/cpu_usage.log 2>/dev/null &
CPU_PID=$!

# Start logcat with timestamps
adb logcat -c  # Clear existing logs
adb logcat -v threadtime MLC_CHAT_MODULE:V MlcJniWrapper:V SimpleMlcModel:V StudyBuddy:V *:S > performance_logs/model_operations.log &
LOG_PID=$!

echo "Monitoring started. Press Enter to send a test prompt to the app..."
read

# Timestamp for inference start
echo "$(date '+%H:%M:%S') INFERENCE_START" >> performance_logs/timing.log

# Launch the app and wait for it to start
echo "Launching StudyBuddy app..."
adb shell am start -n com.example.studybuddy/.MainActivity
sleep 5  # Give more time for app to initialize

# Safely send test prompt via adb
echo "Sending test prompt to the app..."

# Tap chat input field
echo "Tapping input field at $INPUT_X,$INPUT_Y"
adb shell input tap $INPUT_X $INPUT_Y
sleep 2

# Input text - replace spaces with proper encoding
TEST_PROMPT="Explain the concept of machine learning in simple terms"
echo "Typing: $TEST_PROMPT"
adb shell input text "${TEST_PROMPT// /%s}"
sleep 2

# Tap send button
echo "Tapping send button at $SEND_X,$SEND_Y"
adb shell input tap $SEND_X $SEND_Y

# Wait for generation to complete (adjust timing as needed)
echo "Waiting for response generation (60 seconds)..."
sleep 60

# Timestamp for inference end
echo "$(date '+%H:%M:%S') INFERENCE_END" >> performance_logs/timing.log

# Stop monitoring - use trap to ensure cleanup
trap "kill $MEM_PID $CPU_PID $LOG_PID 2>/dev/null || true" EXIT

# Try to kill monitoring processes
kill $MEM_PID $CPU_PID $LOG_PID 2>/dev/null || true

# Calculate elapsed time
end_time=$(date +%s)
duration=$((end_time - start_time))

echo "===== Performance Test Complete ====="
echo "Test duration: $duration seconds"
echo "Logs saved to performance_logs directory"

# Generate performance report
echo "===== Performance Report ====="
echo "Generating report..."

# Extract memory high water mark (handle different formats)
high_mem=$(grep -E "TOTAL|Total PSS" performance_logs/memory_usage.log | awk '{print $4" "$5}' | sort -nr | head -n 1)

# Calculate average CPU usage (simplified and more robust)
avg_cpu=$(awk 'BEGIN{sum=0; count=0} {sum+=$3; count++} END{if(count>0) print sum/count; else print "N/A"}' performance_logs/cpu_usage.log 2>/dev/null || echo "N/A")

# Extract model loading time
model_load_time=$(grep -o "Loading model took [0-9.]* ms" performance_logs/model_operations.log | head -n 1)
if [ -z "$model_load_time" ]; then
    model_load_time="Not found in logs"
fi

# Extract inference times
inference_times=$(grep "generating response took" performance_logs/model_operations.log)
if [ -z "$inference_times" ]; then
    inference_times="Not found in logs"
fi

# Check for any errors
errors=$(grep -i "error|exception|failure" performance_logs/model_operations.log | head -5)

cat << EOF > performance_report.txt
===== Gemma 2 2B-IT Performance Report =====

Test date: $(date)
Test duration: $duration seconds

Memory usage high water mark: $high_mem
Average CPU usage: ${avg_cpu}%

Model loading: $model_load_time
Inference details: 
$inference_times

Errors (if any):
$errors

Summary:
- Check if memory usage is within acceptable limits (<500MB)
- Check if response time is reasonable (<5 seconds for first token)
- Check log for any performance warnings or errors

Recommendations:
1. If memory usage is too high, consider further model quantization
2. If inference is slow, consider model optimization techniques
3. Monitor battery usage during extended sessions
EOF

echo "Report generated: performance_report.txt"
echo "===== End of Performance Monitoring =====" 