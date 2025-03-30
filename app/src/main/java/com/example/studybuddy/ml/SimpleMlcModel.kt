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
        // Load the native libraries
        init {
            try {
                // Load our JNI wrapper library that contains all the necessary implementations
                System.loadLibrary("mlc_jni_wrapper")
                Log.d("SimpleMlcModel", "JNI wrapper library loaded successfully")
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
    private external fun shutdown_native()
    
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Initializing SimpleMlcModel")
                
                // Check if we have a downloader and if model files are downloaded
                val modelDownloader = GemmaModelDownloader(context)
                
                // First check for a full model download
                val modelDirPath = if (modelDownloader.isFullModelDownloaded()) {
                    // Use the fully downloaded model files
                    val downloadedModelDir = modelDownloader.getModelDirectory()
                    Log.d(tag, "Using complete downloaded model files from: ${downloadedModelDir.absolutePath}")
                    downloadedModelDir.absolutePath
                } else if (modelDownloader.isModelDownloaded()) {
                    // Use the partial downloaded model files
                    val downloadedModelDir = modelDownloader.getModelDirectory()
                    Log.d(tag, "Using partial downloaded model files from: ${downloadedModelDir.absolutePath}")
                    Log.w(tag, "Warning: Model files may be incomplete. Consider running a full download.")
                    downloadedModelDir.absolutePath
                } else {
                    // Fall back to internal model directory
                    if (!internalModelDir.exists()) {
                        Log.d(tag, "Creating model directory: ${internalModelDir.absolutePath}")
                        internalModelDir.mkdirs()
                    }
                    Log.d(tag, "Using internal model directory: ${internalModelDir.absolutePath}")
                    internalModelDir.absolutePath
                }
                
                // Verify essential files exist
                val missingFiles = checkEssentialFiles(modelDirPath)
                if (missingFiles.isNotEmpty()) {
                    val errorMsg = "Missing essential model files: ${missingFiles.joinToString(", ")}"
                    Log.e(tag, errorMsg)
                    _error.value = errorMsg
                    _initialized.value = false
                    throw Exception(errorMsg)
                }
                
                // Initialize the native model
                try {
                    mlc_create_chat_module(modelDirPath)
                    
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
                throw e
            }
        }
    }
    
    /**
     * Check if all essential files exist in the model directory
     * @return List of missing files (empty if all files are present)
     */
    private fun checkEssentialFiles(modelDirPath: String): List<String> {
        val missingFiles = mutableListOf<String>()
        val essentialFiles = listOf(
            "tokenizer_config.json",
            "tokenizer.json",
            "tokenizer.model",
            "mlc-chat-config.json",
            "ndarray-cache.json"
        )
        
        for (fileName in essentialFiles) {
            val file = File(modelDirPath, fileName)
            if (!file.exists() || file.length() <= 0L) {
                missingFiles.add(fileName)
            }
        }
        
        return missingFiles
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
                
                // Preprocess the prompt to ensure correct formatting
                val formattedPrompt = if (!prompt.endsWith("\nAssistant:")) {
                    // Make sure the prompt ends with "Assistant:" for proper response generation
                    if (prompt.contains("Assistant:")) {
                        prompt
                    } else {
                        "$prompt\nAssistant:"
                    }
                } else {
                    prompt
                }
                
                Log.d(tag, "Using formatted prompt: $formattedPrompt")
                val response = generate(formattedPrompt)
                
                // Post-process the response for better display
                val cleanedResponse = response.trim()
                Log.d(tag, "Generated text successfully")
                
                cleanedResponse
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
        if (isInitialized) {
            try {
                shutdown_native()
                Log.d(tag, "Native resources released")
            } catch (e: Exception) {
                Log.e(tag, "Error shutting down native resources: ${e.message}", e)
            }
        }
        
        // Update state
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