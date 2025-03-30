#!/bin/bash
# Script to verify if the app is using real Gemma implementation

set -e  # Exit on error

echo "===== Verifying Real Gemma Implementation ====="

# Create output directory
mkdir -p verification

# Step 1: Check all source files for mock implementations
echo "Step 1: Checking source files for mock implementations..."

find app/src/main/cpp -type f -name "*.cpp" -o -name "*.h" > verification/cpp_files.txt
find app/src/main/java -type f -name "*.java" -o -name "*.kt" >> verification/source_files.txt

# Filter out TVM library includes from analysis
# These files can contain terms like 'mock', 'dummy', etc., but they're part of TVM, not our code
grep -v "app/src/main/cpp/include/tvm" verification/cpp_files.txt > verification/filtered_cpp_files.txt 2>/dev/null || true
grep -v "app/src/main/cpp/include/dmlc" verification/filtered_cpp_files.txt > verification/our_cpp_files.txt 2>/dev/null || true

# If files don't exist, handle gracefully
if [ ! -s "verification/our_cpp_files.txt" ]; then
    echo "No CPP implementation files found - this is expected if using Rust implementation"
    touch verification/our_cpp_files.txt
fi

MOCK_KEYWORDS=("mock implementation" "stub implementation" "dummy response" "fake implementation" "Hard-coded response" "placeholder response")
MOCK_FOUND=0

echo "Searching for mock-related keywords in implementation files..."
for file in $(cat verification/our_cpp_files.txt); do
    for keyword in "${MOCK_KEYWORDS[@]}"; do
        if grep -i "$keyword" "$file" > /dev/null 2>&1; then
            echo "❌ Potential mock detected in $file: '$keyword'"
            grep -i -n "$keyword" "$file" | head -5
            MOCK_FOUND=1
        fi
    done
done

# Step 2: Check for symbols in the shared library
echo "Step 2: Verifying shared library symbols..."

LIB_PATH="app/src/main/jniLibs/arm64-v8a/libgemma_2_2b_it_q4f16_1.so"
if [ ! -f "$LIB_PATH" ]; then
    # Try alternate name
    LIB_PATH="app/src/main/jniLibs/arm64-v8a/libgemma-2-2b-it-q4f16_1.so"
    if [ ! -f "$LIB_PATH" ]; then
        echo "❌ ERROR: Library not found in jniLibs directory"
        exit 1
    fi
fi

# Check if required symbols are exported
REQUIRED_SYMBOLS=("mlc_create_chat_module" "generate" "reset_chat" "set_parameter")

# Platform-specific symbol checking
if [ "$(uname)" == "Darwin" ]; then
    # macOS - symbols have different naming convention
    for symbol in "${REQUIRED_SYMBOLS[@]}"; do
        if ! nm -gU "$LIB_PATH" 2>/dev/null | grep -q "$symbol"; then
            # Try with underscore prefix (macOS format)
            if ! nm -gU "$LIB_PATH" 2>/dev/null | grep -q "_$symbol"; then
                echo "❌ CRITICAL: Required symbol '$symbol' not found in library"
                MOCK_FOUND=1
            else 
                echo "✅ Required symbol '_$symbol' found in library (macOS format)"
            fi
        else
            echo "✅ Required symbol '$symbol' found in library"
        fi
    done
else
    # Linux/other
    for symbol in "${REQUIRED_SYMBOLS[@]}"; do
        if ! nm -D "$LIB_PATH" 2>/dev/null | grep -q "$symbol"; then
            echo "❌ CRITICAL: Required symbol '$symbol' not found in library"
            MOCK_FOUND=1
        else
            echo "✅ Required symbol '$symbol' found in library"
        fi
    done
fi

# Step 3: Check library size and content
echo "Step 3: Checking library size and content..."

SIZE=$(ls -l "$LIB_PATH" | awk '{print $5}')
if [ "$SIZE" -lt 10000 ]; then  # Less than 10KB is very suspicious
    echo "❌ WARNING: Library is suspiciously small ($SIZE bytes)"
    MOCK_FOUND=1
else
    echo "✅ Library size looks reasonable ($SIZE bytes)"
fi

# Check for key strings in the library that would indicate a real implementation
# Use binary grep with case-insensitive to catch both Rust and C implementations
if strings "$LIB_PATH" | grep -i "tvm\|runtime" > /dev/null; then
    echo "✅ Library contains TVM references"
else
    echo "❌ WARNING: No TVM references in library"
    MOCK_FOUND=1
fi

if strings "$LIB_PATH" | grep -i "gemma" > /dev/null; then
    echo "✅ Library contains Gemma references"
else
    echo "❌ WARNING: No Gemma references in library"
    MOCK_FOUND=1
fi

# Step 4: Check for model files
echo "Step 4: Checking model files..."

MODEL_DIR="app/src/main/assets/models/gemma2_2b_it"
if [ ! -d "$MODEL_DIR" ]; then
    echo "❌ ERROR: Model directory not found"
    exit 1
fi

REQUIRED_FILES=("tokenizer.model" "mlc-chat-config.json" "ndarray-cache.json")
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "$MODEL_DIR/$file" ]; then
        echo "❌ CRITICAL: Required model file '$file' not found"
        MOCK_FOUND=1
    else
        echo "✅ Required model file '$file' found"
    fi
done

# Count param shard files
PARAM_SHARDS=$(find "$MODEL_DIR" -name "params_shard_*.bin" | wc -l)
if [ "$PARAM_SHARDS" -lt 1 ]; then
    echo "❌ CRITICAL: No parameter shards found"
    MOCK_FOUND=1
else
    echo "✅ Found $PARAM_SHARDS parameter shard files"
fi

# Final assessment
echo "===== Verification Complete ====="
if [ "$MOCK_FOUND" -eq 0 ]; then
    echo "✅ SUCCESS: All checks passed! App appears to be using real Gemma implementation."
    echo "Run the app with ./test_gemma_implementation.sh to confirm proper functionality."
else
    echo "❌ WARNING: Some verification checks failed. App may still be using mock implementations."
    echo "Please review the warnings above and fix any issues."
fi

# Log verification results
echo "===== $(date) =====" > verification/verification_results.log
if [ "$MOCK_FOUND" -eq 0 ]; then
    echo "✅ VERIFICATION PASSED" >> verification/verification_results.log
else
    echo "❌ VERIFICATION FAILED" >> verification/verification_results.log
fi

echo "Verification results saved to verification/verification_results.log" 