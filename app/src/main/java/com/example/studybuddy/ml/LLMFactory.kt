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
         * Create an appropriate LLM instance based on available libraries
         */
        fun createLLM(context: Context): LanguageModel {
            return try {
                // Try to create an MLC-LLM based model first
                Log.d(TAG, "Attempting to create MLC-LLM model")
                MlcLanguageModel(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create MLC-LLM model: ${e.message}")
                
                try {
                    // Try TVM bridge as a fallback
                    Log.d(TAG, "Attempting to create TVM bridge model")
                    TVMLanguageModel(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create TVM bridge model: ${e.message}")
                    
                    // Return a model that clearly indicates the error
                    ErrorLanguageModel(context, e.message ?: "Unknown error initializing LLM")
                }
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
    
    override suspend fun shutdown() {
        // Nothing to shut down
    }
} 