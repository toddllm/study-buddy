package com.example.studybuddy.ml

import android.util.Log
import java.io.File

/**
 * Bridge to the native TVM/MLC runtime.
 */
class TVMBridge {
    companion object {
        private const val TAG = "TVMBridge"
        
        init {
            try {
                System.loadLibrary("c++_shared")
                System.loadLibrary("tvm_runtime")
                System.loadLibrary("tvm")
                System.loadLibrary("mlc_llm")
                System.loadLibrary("mlc_llm_module")
                System.loadLibrary("tvm_bridge")
                Log.i(TAG, "TVM libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native libraries: ${e.message}")
                throw RuntimeException("Failed to load required native libraries: ${e.message}")
            }
        }
    }
    
    /**
     * Initialize the TVM engine with the given model path
     */
    fun initializeEngine(modelPath: String): Boolean {
        Log.i(TAG, "Initializing MLC-LLM runtime with model at: $modelPath")
        
        // List the contents of the model directory for debugging
        val modelDir = File(modelPath)
        if (modelDir.exists()) {
            Log.d(TAG, "Directory contents of $modelPath:")
            modelDir.listFiles()?.forEach { file ->
                Log.d(TAG, "  ${file.name}")
            }
            
            // Count parameter files
            val paramFiles = modelDir.listFiles { file -> file.name.startsWith("params_shard_") }
            Log.d(TAG, "Found ${paramFiles?.size ?: 0} parameter files")
        } else {
            val errorMsg = "Model directory does not exist: $modelPath"
            Log.e(TAG, errorMsg)
            return false
        }
        
        // Call the C++ implementation to initialize
        return try {
            val result = initializeTVMRuntime(modelPath)
            if (!result) {
                Log.e(TAG, "Native TVM runtime initialization failed")
                return false
            }
            Log.i(TAG, "Native TVM runtime initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing native TVM runtime: ${e.message}")
            false
        }
    }
    
    /**
     * Generate a response for a prompt using non-streaming mode
     */
    fun chat(prompt: String): String {
        Log.d(TAG, "Generating response for prompt: $prompt")
        val response = generateResponse(prompt)
        if (response.startsWith("ERROR:") || response.startsWith("Error:")) {
            Log.e(TAG, "Error in text generation: $response")
            throw RuntimeException(response)
        }
        return response
    }
    
    /**
     * Stream tokens from the model for the given prompt
     */
    fun streamChat(prompt: String, callback: (String) -> Unit) {
        Log.d(TAG, "Streaming response for prompt: $prompt")
        streamResponse(prompt) { token ->
            if (token.startsWith("ERROR:") || token.startsWith("Error:")) {
                Log.e(TAG, "Error in streaming: $token")
                throw RuntimeException(token)
            }
            callback(token)
        }
    }
    
    /**
     * Set temperature for text generation
     */
    fun setTemperature(temperature: Float) {
        Log.i(TAG, "Temperature set to: $temperature")
        setGenerationTemperature(temperature)
    }
    
    /**
     * Set top_p for text generation
     */
    fun setTopP(topP: Float) {
        Log.i(TAG, "Top-p set to: $topP")
        setGenerationTopP(topP)
    }
    
    /**
     * Reset the chat session
     */
    fun resetChat() {
        Log.d(TAG, "Resetting chat session")
        resetChatSession()
    }
    
    // Native method declarations
    private external fun initializeTVMRuntime(modelPath: String): Boolean
    private external fun generateResponse(prompt: String): String
    private external fun streamResponse(prompt: String, callback: (String) -> Unit)
    private external fun setGenerationTemperature(temperature: Float): Boolean
    private external fun setGenerationTopP(topP: Float): Boolean
    private external fun resetChatSession(): Boolean
} 