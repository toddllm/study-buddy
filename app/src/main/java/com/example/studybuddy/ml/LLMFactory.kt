package com.example.studybuddy.ml

import android.content.Context
import android.util.Log

/**
 * Factory for creating LLM instances with proper error handling
 */
class LLMFactory {
    companion object {
        private const val TAG = "LLMFactory"
        
        /**
         * Create an appropriate LLM instance based on available libraries.
         * Prioritizes MLC-LLM and will fail if it cannot be initialized.
         */
        fun createLLM(context: Context): LanguageModel {
            try {
                Log.d(TAG, "Creating SimpleMlcModel language model")
                // Create our SimpleMlcModel which uses the mock implementation
                val mlcModel = MlcLanguageModel(context)
                
                // Check if we have the files but don't initialize yet
                // Let the component handle initialization
                return mlcModel
            } catch (e: Exception) {
                val errorMsg = "Failed to create SimpleMlcModel: ${e.message}"
                Log.e(TAG, errorMsg, e)
                
                // Return an error model instead of throwing
                return ErrorLanguageModel(context, errorMsg)
            }
        }
    }
}

/**
 * Language model that reports errors instead of generating text
 */
class ErrorLanguageModel(context: Context, private val errorMessage: String) : LanguageModel(context) {
    
    override suspend fun initialize() {
        _initialized.value = false
        _error.value = errorMessage
        Log.e("ErrorLanguageModel", "Error: $errorMessage")
    }
    
    override suspend fun generateText(prompt: String): String {
        return "ERROR: $errorMessage"
    }
    
    override fun streamText(prompt: String, onToken: (String) -> Unit, onError: (String) -> Unit) {
        onError("ERROR: $errorMessage")
    }
    
    override suspend fun reset() {
        // Nothing to reset
        Log.d("ErrorLanguageModel", "Reset called, but nothing to reset")
    }
    
    override fun getModelInfo(): String {
        return "Error Model: $errorMessage"
    }
    
    override suspend fun shutdown() {
        // Nothing to shut down
    }
} 