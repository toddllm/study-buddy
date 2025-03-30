#!/bin/bash
nm -D --defined-only app/src/main/jniLibs/arm64-v8a/libgemma-2-2b-it-q4f16_1.so | grep -E " [TD] " | sort
