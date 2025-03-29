package com.example.studybuddy.mlc;

import android.content.Context;
import android.util.Log;

/**
 * Bridge class for working with MLC-LLM using JNI
 */
public class TVMBridge {
    private static final String TAG = "TVMBridge";
    private static boolean librariesLoaded = false;
    
    static {
        try {
            // Load the required libraries in the correct order
            System.loadLibrary("c++_shared");
            System.loadLibrary("tvm_runtime");
            System.loadLibrary("tvm");
            System.loadLibrary("mlc_llm");
            System.loadLibrary("mlc_llm_module");
            
            // Load our JNI bridge library that implements the native methods
            System.loadLibrary("tvm_bridge");
            
            librariesLoaded = true;
            Log.i(TAG, "TVM libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load TVM libraries: " + e.getMessage());
        }
    }
    
    // Check if libraries were loaded successfully
    public static boolean areLibrariesLoaded() {
        return librariesLoaded;
    }
    
    // Initialize the MLC-LLM runtime
    public static native boolean initRuntime(String modelPath);
    
    // Generate text response for a prompt
    public static native String generateText(String prompt, int maxTokens);
    
    // Set various generation parameters
    public static native boolean setTemperature(float temperature);
    public static native boolean setTopP(float topP);
    public static native boolean setRepetitionPenalty(float penalty);
    
    // Clean up resources
    public static native void destroyRuntime();
    
    /**
     * Interface for streaming text generation callbacks
     */
    public interface StreamingCallback {
        /**
         * Called when a new token is generated
         * @param token The token text
         * @param isFinished True if generation is complete, false if more tokens are coming
         */
        void onToken(String token, boolean isFinished);
    }
    
    /**
     * Start streaming text generation
     * @param prompt The input prompt
     * @param maxTokens Maximum number of tokens to generate
     * @param callback Callback to receive generated tokens
     * @return True if generation started successfully
     */
    public static native boolean startStreamingGeneration(String prompt, int maxTokens, StreamingCallback callback);
    
    /**
     * Stop any ongoing streaming generation
     */
    public static native void stopStreamingGeneration();
} 