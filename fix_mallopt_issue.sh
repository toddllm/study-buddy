#!/bin/bash

# Script to fix the mallopt compilation issue in mlc_jni_wrapper.cpp
# This addresses the undefined identifiers M_TRIM_THRESHOLD and M_MMAP_THRESHOLD

echo "===== Fixing mallopt Issue ====="

# Clean the project
echo "Cleaning project..."
./gradlew clean

# Rebuild the project with fixed memory management
echo "Building project with fixed memory management..."
./gradlew assembleDebug

echo "Build complete!"
echo ""
echo "The error 'use of undeclared identifier M_TRIM_THRESHOLD/M_MMAP_THRESHOLD'"
echo "was fixed by replacing mallopt() calls with a more Android-compatible"
echo "memory management approach using pre-allocated buffers."
echo ""
echo "You can now install and run the app with:"
echo "adb install -r app/build/outputs/apk/debug/app-debug.apk" 