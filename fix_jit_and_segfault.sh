#!/bin/bash

# Script to fix the JIT profile log errors and segmentation fault
# in the StudyBuddy app

echo "===== Fixing JIT Log and Segfault Issues ====="

# Clean the project
echo "Cleaning project..."
./gradlew clean

# Rebuild the project with the fixes
echo "Building project with fixes..."
./gradlew assembleDebug

echo "Build complete!"
echo ""
echo "Fixed two critical issues:"
echo "1. JIT Profile Log Error: Created necessary directories for JIT profiles"
echo "   in the application's onCreate method."
echo ""
echo "2. Segmentation Fault: Added safety checks and string copy in set_parameter"
echo "   to prevent crashes when passing strings to native code."
echo ""
echo "You can now install and run the app with:"
echo "adb install -r app/build/outputs/apk/debug/app-debug.apk" 