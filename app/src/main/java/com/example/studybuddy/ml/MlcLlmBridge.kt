package com.example.studybuddy.ml

import android.util.Log

/**
 * JNI bridge for MLC-LLM library
 */
class MlcLlmBridge {
    companion object {
        private const val TAG = "MlcLlmBridge"
        
        init {
            try {
                // Load native libraries in correct order
                System.loadLibrary("c++_shared")
                System.loadLibrary("tvm_runtime")
                System.loadLibrary("tvm")
                System.loadLibrary("mlc_llm")
                System.loadLibrary("mlc_llm_jni")
                Log.i(TAG, "MLC-LLM libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
                throw RuntimeException("Failed to load required native libraries: ${e.message}")
            }
        }
    }
    
    /**
     * Initialize the MLC-LLM engine
     */
    external fun initializeEngine(modelPath: String): Boolean
    
    /**
     * Generate a response using the model
     */
    external fun generateResponse(prompt: String): String
    
    /**
     * Stream a response using the model
     */
    external fun streamResponse(prompt: String, callback: (String) -> Unit)
    
    /**
     * Set the generation temperature
     */
    external fun setTemperature(temperature: Float)
    
    /**
     * Set the top-p sampling parameter
     */
    external fun setTopP(topP: Float)
    
    /**
     * Set maximum generation length
     */
    external fun setMaxGenLen(maxGenLen: Int)
    
    /**
     * Reset the chat session
     */
    external fun resetChat()
    
    /**
     * Close the engine and release resources
     */
    external fun closeEngine()
} 