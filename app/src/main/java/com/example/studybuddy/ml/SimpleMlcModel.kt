package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A simplified implementation of LanguageModel that uses our native library
 * containing a basic Gemma model implementation.
 */
class SimpleMlcModel(context: Context) : LanguageModel(context) {
    private val tag = "SimpleMlcModel"
    private var isInitialized = false
    private var modelInfo = "Gemma 2-2B-IT (Simplified implementation)"
    
    // Model directory path
    private val internalModelDir by lazy { File(context.filesDir, "models/gemma2_2b_it") }
    
    companion object {
        // Load the native library
        init {
            try {
                System.loadLibrary("gemma-2-2b-it-q4f16_1")
                Log.d("SimpleMlcModel", "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("SimpleMlcModel", "Failed to load native library: ${e.message}")
            }
        }
    }
    
    // Native function declarations
    private external fun mlc_create_chat_module(modelPath: String): Any
    private external fun generate(prompt: String): String
    private external fun reset_chat()
    private external fun set_parameter(key: String, value: Float)
    
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Initializing SimpleMlcModel")
                
                // Create model directory if it doesn't exist
                if (!internalModelDir.exists()) {
                    Log.d(tag, "Creating model directory: ${internalModelDir.absolutePath}")
                    internalModelDir.mkdirs()
                }
                
                // Initialize the native model
                try {
                    mlc_create_chat_module(internalModelDir.absolutePath)
                    
                    // Set initialized state
                    isInitialized = true
                    _initialized.value = true
                    _error.value = null
                    
                    // Set parameters
                    onTemperatureChanged(temperature)
                    onTopPChanged(topP)
                    
                    Log.d(tag, "SimpleMlcModel initialized successfully")
                } catch (e: Exception) {
                    val error = "Failed to initialize model: ${e.message}"
                    Log.e(tag, error, e)
                    _error.value = error
                    _initialized.value = false
                    throw e
                }
            } catch (e: Exception) {
                val error = "Error during initialization: ${e.message}"
                Log.e(tag, error, e)
                _error.value = error
                _initialized.value = false
            }
        }
    }
    
    override suspend fun generateText(prompt: String): String {
        if (!isInitialized) {
            val error = "Model not initialized"
            _error.value = error
            return "ERROR: $error"
        }
        
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Generating text for prompt: $prompt")
                val response = generate(prompt)
                Log.d(tag, "Generated text successfully")
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
            onError("ERROR: $error")
            return
        }
        
        // Use kotlinx.coroutines.GlobalScope to launch a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = generateText(prompt)
                onToken(response)
            } catch (e: Exception) {
                val error = "Error streaming text: ${e.message}"
                Log.e(tag, error, e)
                _error.value = error
                onError("ERROR: $error")
            }
        }
    }
    
    override suspend fun reset() {
        if (isInitialized) {
            try {
                reset_chat()
                Log.d(tag, "Chat reset successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error resetting chat: ${e.message}", e)
            }
        }
    }
    
    override fun getModelInfo(): String {
        return modelInfo
    }
    
    override suspend fun shutdown() {
        // No specific shutdown needed, just update state
        isInitialized = false
        _initialized.value = false
        Log.d(tag, "SimpleMlcModel shut down")
    }
    
    override public fun onTemperatureChanged(newValue: Float) {
        if (isInitialized) {
            try {
                set_parameter("temperature", newValue)
                Log.d(tag, "Temperature set to $newValue")
            } catch (e: Exception) {
                Log.e(tag, "Error setting temperature: ${e.message}", e)
            }
        }
    }
    
    override public fun onTopPChanged(newValue: Float) {
        if (isInitialized) {
            try {
                set_parameter("top_p", newValue)
                Log.d(tag, "Top-p set to $newValue")
            } catch (e: Exception) {
                Log.e(tag, "Error setting top-p: ${e.message}", e)
            }
        }
    }
} 