package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MLC-LLM Language Model for on-device LLM capabilities.
 * Uses Google's Gemma 2 (2B parameters) model which is optimized for mobile devices.
 */
class MlcLanguageModel(context: Context) : LanguageModel(context) {
    private val tag = "MlcLanguageModel"
    private val bridge = MlcLlmBridge()
    private var isInitialized = false
    private var modelInfo = ""
    
    /**
     * Check if model files are already available
     */
    suspend fun areModelFilesAvailable(): Boolean = withContext(Dispatchers.IO) {
        // First check if the models folder in assets has the required files
        val assetDir = File(context.filesDir, "models/gemma2_2b_it")
        if (assetDir.exists()) {
            val configFile = File(assetDir, "config.json")
            if (configFile.exists()) {
                Log.d(tag, "Found model files in app assets: $assetDir")
                return@withContext true
            }
        }
        
        // Otherwise check the downloaded model dir
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) return@withContext false
        
        // Check for config file and tokenizer
        val configFile = File(modelDir, "gemma-2b-it/config.json")
        val tokenizerFile = File(modelDir, "gemma-2b-it/tokenizer.json")
        
        if (!configFile.exists() || !tokenizerFile.exists()) {
            return@withContext false
        }
        
        // Check for all parameter shards
        for (i in 0 until 38) {
            val shardFile = File(modelDir, "gemma-2b-it/params_shard_$i.bin")
            if (!shardFile.exists()) {
                Log.d(tag, "Missing parameter shard: ${shardFile.name}")
                return@withContext false
            }
        }
        
        Log.d(tag, "All model files are available")
        return@withContext true
    }
    
    /**
     * Initialize the model, optionally with an auth token for downloading
     */
    suspend fun initialize(authToken: String? = null): Boolean {
        initialize()
        return isInitialized
    }
    
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Initializing MlcLanguageModel")
                
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
                
                // Initialize the model using the MLC bridge
                val result = bridge.initializeEngine(modelDir.absolutePath)
                if (!result) {
                    val error = "Failed to initialize MLC-LLM runtime"
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
                Log.d(tag, "MlcLanguageModel initialized successfully")
            } catch (e: Exception) {
                val error = "Error initializing MlcLanguageModel: ${e.message}"
                Log.e(tag, error, e)
                _error.value = error
                _initialized.value = false
            }
        }
    }
    
    /**
     * Get information about the loaded model
     */
    fun getModelInfo(): String {
        return modelInfo
    }
    
    override suspend fun generateText(prompt: String): String {
        if (!isInitialized) {
            _error.value = "Model not initialized"
            return "ERROR: Model not initialized"
        }
        
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Generating text for prompt: $prompt")
                val response = bridge.generateResponse(prompt)
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
            bridge.streamResponse(prompt) { token ->
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
                    Log.d(tag, "Shutting down MlcLanguageModel")
                    bridge.resetChat()
                    isInitialized = false
                    _initialized.value = false
                } else {
                    Log.d(tag, "MlcLanguageModel was not initialized, nothing to shut down")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error shutting down MlcLanguageModel: ${e.message}", e)
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