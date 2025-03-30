cmake_minimum_required(VERSION 3.4.1)
project(tvm_bridge)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define paths to the prebuilt libraries
set(PREBUILT_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a)

# Include directories for TVM headers
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Add the JNI bridge library - this builds libtvm_bridge.so
# This library implements the JNI methods declared in TVMBridge.java
add_library(tvm_bridge SHARED
    tvm_bridge.cpp
)

# Find the log library for Android logging
find_library(log-lib log)

# Specify the prebuilt libraries explicitly
add_library(tvm SHARED IMPORTED)
set_target_properties(tvm PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIB_DIR}/libtvm.so)

add_library(tvm_runtime SHARED IMPORTED)
set_target_properties(tvm_runtime PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIB_DIR}/libtvm_runtime.so)

add_library(mlc_llm SHARED IMPORTED)
set_target_properties(mlc_llm PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIB_DIR}/libmlc_llm.so)

# Link the JNI bridge with libraries
target_link_libraries(tvm_bridge
    ${log-lib}
    tvm_runtime
    tvm
    mlc_llm
)

# For production build, you would link against:
# Include TVM/MLC-LLM libraries
# target_link_libraries(mlc_llm_jni
#     tvm_runtime
#     mlc_llm_module
#     ${log-lib}
# )

# Use the real MLC-LLM implementation
add_library(mlc_llm_jni SHARED
    real_mlc_llm_jni.cpp
)

# Include headers for the MLC JNI library
target_include_directories(mlc_llm_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Configure mlc_llm_jni - link with log library and other required libraries
target_link_libraries(mlc_llm_jni
    ${log-lib}
    android
    tvm_runtime
    tvm
    mlc_llm
)
