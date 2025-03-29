package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementation of LanguageModel that uses the TVM bridge for model execution
 */
class TVMLanguageModel(context: Context) : LanguageModel(context) {
    private val tag = "TVMLanguageModel"
    private val bridge = TVMBridge()
    private var isInitialized = false
    
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Initializing TVMLanguageModel")
                
                // Find model directory
                val modelsDir = File(context.filesDir, "models")
                val modelDir = File(modelsDir, "gemma-2b-it")
                
                if (!modelDir.exists()) {
                    val error = "Model not found at ${modelDir.absolutePath}"
                    Log.e(tag, error)
                    _error.value = error
                    _initialized.value = false
                    return@withContext
                }
                
                Log.d(tag, "Found model at ${modelDir.absolutePath}")
                
                // Initialize the model using the TVM bridge
                val result = bridge.initializeEngine(modelDir.absolutePath)
                if (!result) {
                    val error = "Failed to initialize TVM runtime"
                    Log.e(tag, error)
                    _error.value = error
                    _initialized.value = false
                    return@withContext
                }
                
                // Set up model parameters
                bridge.setTemperature(temperature)
                bridge.setTopP(topP)
                
                isInitialized = true
                _initialized.value = true
                _error.value = null
                Log.d(tag, "TVMLanguageModel initialized successfully")
            } catch (e: Exception) {
                val error = "Error initializing TVMLanguageModel: ${e.message}"
                Log.e(tag, error, e)
                _error.value = error
                _initialized.value = false
            }
        }
    }
    
    override suspend fun generateText(prompt: String): String {
        if (!isInitialized) {
            _error.value = "Model not initialized"
            return "ERROR: Model not initialized"
        }
        
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Generating text for prompt: $prompt")
                val response = bridge.chat(prompt)
                Log.d(tag, "Generated response: $response")
                response
            } catch (e: Exception) {
                val error = "Error generating text: ${e.message}"
                Log.e(tag, error, e)
                _error.value = error
                "ERROR: $error"
            }
        }
    }
    
    override fun streamText(prompt: String, onToken: (String) -> Unit, onError: (String) -> Unit) {
        if (!isInitialized) {
            val error = "Model not initialized"
            _error.value = error
            onError(error)
            return
        }
        
        try {
            Log.d(tag, "Streaming text for prompt: $prompt")
            bridge.streamChat(prompt) { token ->
                onToken(token)
            }
        } catch (e: Exception) {
            val error = "Error streaming text: ${e.message}"
            Log.e(tag, error, e)
            _error.value = error
            onError(error)
        }
    }
    
    override suspend fun shutdown() {
        withContext(Dispatchers.IO) {
            try {
                if (isInitialized) {
                    Log.d(tag, "Shutting down TVMLanguageModel")
                    bridge.resetChat()
                    isInitialized = false
                    _initialized.value = false
                } else {
                    Log.d(tag, "TVMLanguageModel was not initialized, nothing to shut down")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error shutting down TVMLanguageModel: ${e.message}", e)
            }
        }
    }
    
    override fun onTemperatureChanged(newValue: Float) {
        if (isInitialized) {
            bridge.setTemperature(newValue)
        } else {
            // Do nothing if not initialized
        }
    }
    
    override fun onTopPChanged(newValue: Float) {
        if (isInitialized) {
            bridge.setTopP(newValue)
        } else {
            // Do nothing if not initialized
        }
    }
} 