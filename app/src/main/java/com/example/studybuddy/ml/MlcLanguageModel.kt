package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    private var modelInfo = "Gemma 2B-IT: A lightweight, efficient large language model optimized for mobile devices."
    
    // Model directory paths
    private val internalModelDir = File(context.filesDir, "models/gemma2_2b_it")
    
    /**
     * Check if model files are already available
     */
    suspend fun areModelFilesAvailable(): Boolean = withContext(Dispatchers.IO) {
        // First check if the model files are in the internal storage path
        if (internalModelDir.exists()) {
            val configFile = File(internalModelDir, "mlc-chat-config.json")
            val tokenizerFile = File(internalModelDir, "tokenizer.model")
            
            if (configFile.exists() && configFile.length() > 0 && 
                tokenizerFile.exists() && tokenizerFile.length() > 0) {
                Log.d(tag, "Found model files in internal storage: $internalModelDir")
                
                // Check for parameter shards - at least a few should exist
                val paramFiles = internalModelDir.listFiles { file -> 
                    file.name.startsWith("params_shard_") && file.length() > 0
                }
                
                if (paramFiles != null && paramFiles.isNotEmpty()) {
                    Log.d(tag, "Found ${paramFiles.size} parameter shards")
                    return@withContext true
                } else {
                    Log.d(tag, "No valid parameter shards found")
                }
            }
        }
        
        // Check if the models need to be copied from assets
        try {
            val assetsList = context.assets.list("models/gemma2_2b_it") ?: arrayOf()
            if (assetsList.isEmpty()) {
                Log.e(tag, "No model files found in assets directory")
                return@withContext false
            }
            
            // Assets exist, check if they need to be copied
            if (!internalModelDir.exists()) {
                internalModelDir.mkdirs()
            }
            
            // Copy only if the files don't already exist in internal storage
            if (!File(internalModelDir, "mlc-chat-config.json").exists() ||
                !File(internalModelDir, "tokenizer.model").exists()) {
                
                // Copy all model files from assets to internal storage
                Log.d(tag, "Copying model files from assets to internal storage")
                copyModelFilesFromAssets(context, "models/gemma2_2b_it", internalModelDir.absolutePath)
                
                // Verify the files were copied correctly
                val configFile = File(internalModelDir, "mlc-chat-config.json")
                val tokenizerFile = File(internalModelDir, "tokenizer.model")
                
                if (configFile.exists() && configFile.length() > 0 && 
                    tokenizerFile.exists() && tokenizerFile.length() > 0) {
                    Log.d(tag, "Successfully copied model files to internal storage")
                    return@withContext true
                }
            }
        } catch (e: IOException) {
            Log.e(tag, "Error accessing model files from assets", e)
        }
        
        Log.e(tag, "Model files not available")
        return@withContext false
    }
    
    /**
     * Helper method to copy model files from assets to internal storage
     */
    private fun copyModelFilesFromAssets(context: Context, assetPath: String, destPath: String) {
        context.assets.list(assetPath)?.forEach { fileName ->
            try {
                val subAssetPath = "$assetPath/$fileName"
                val destFilePath = "$destPath/$fileName"
                val destFile = File(destFilePath)
                
                // Check if it's a directory
                val subAssets = context.assets.list(subAssetPath)
                if (subAssets != null && subAssets.isNotEmpty()) {
                    // It's a directory, create it and recurse
                    destFile.mkdirs()
                    copyModelFilesFromAssets(context, subAssetPath, destFilePath)
                } else {
                    // It's a file, copy it if it doesn't already exist or is empty
                    if (!destFile.exists() || destFile.length() == 0L) {
                        context.assets.open(subAssetPath).use { inputStream ->
                            FileOutputStream(destFile).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Log.d(tag, "Copied asset file: $subAssetPath to $destFilePath")
                    } else {
                        Log.d(tag, "Skipped existing asset file: $subAssetPath")
                    }
                }
            } catch (e: IOException) {
                Log.e(tag, "Failed to copy asset: $fileName", e)
            }
        }
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
                
                if (!internalModelDir.exists()) {
                    Log.d(tag, "Creating model directory structure")
                    internalModelDir.mkdirs()
                    File(internalModelDir, "lib").mkdirs()
                    File(internalModelDir, "params").mkdirs()
                }
                
                // Check for key model files
                val configFile = File(internalModelDir, "mlc-chat-config.json")
                val tokenizerFile = File(internalModelDir, "tokenizer.model")
                val modelLibDir = File(internalModelDir, "lib")
                val modelLib = File(modelLibDir, "libgemma-2-2b-it-q4f16_1.so")
                
                // Copy model library from assets if it doesn't exist in internal storage
                if (!modelLib.exists() || modelLib.length() == 0L) {
                    Log.d(tag, "Model library not found in internal storage, attempting to copy from assets")
                    try {
                        // Ensure the lib directory exists
                        modelLibDir.mkdirs()
                        
                        // Copy from assets
                        val assetManager = context.assets
                        val inputStream = assetManager.open("models/gemma2_2b_it/lib/libgemma-2-2b-it-q4f16_1.so")
                        val outputStream = FileOutputStream(modelLib)
                        
                        // Copy the file
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        
                        // Close streams
                        inputStream.close()
                        outputStream.close()
                        
                        Log.d(tag, "Successfully copied model library from assets to internal storage")
                    } catch (e: IOException) {
                        val error = "FATAL ERROR: Could not copy model library from assets: ${e.message}"
                        Log.e(tag, error, e)
                        _error.value = error
                        _initialized.value = false
                        return@withContext
                    }
                }
                
                if (!configFile.exists() || !tokenizerFile.exists()) {
                    val error = "FATAL ERROR: Missing critical model files in ${internalModelDir.absolutePath}"
                    Log.e(tag, error)
                    _error.value = error
                    _initialized.value = false
                    return@withContext
                }
                
                if (!modelLibDir.exists() || !modelLib.exists()) {
                    val error = "FATAL ERROR: Model library not found at ${modelLib.absolutePath}"
                    Log.e(tag, error)
                    _error.value = error
                    _initialized.value = false
                    return@withContext
                }
                
                Log.d(tag, "Found model at ${internalModelDir.absolutePath}, beginning initialization")
                
                // Also make sure the model library is properly set as executable
                try {
                    if (modelLib.exists()) {
                        val chmod = ProcessBuilder("chmod", "755", modelLib.absolutePath)
                        chmod.start().waitFor()
                        Log.d(tag, "Set execute permissions on model library")
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Could not set execute permissions on model library: ${e.message}")
                    // Continue anyway, as this might not be necessary
                }
                
                // Initialize the model using the MLC bridge
                val result = bridge.initializeEngine(internalModelDir.absolutePath)
                if (!result) {
                    val error = "FATAL ERROR: Failed to initialize MLC-LLM runtime. The model files exist but the LLM engine could not be initialized."
                    Log.e(tag, error)
                    _error.value = error
                    _initialized.value = false
                    return@withContext
                }
                
                // Set up model parameters
                bridge.setTemperature(temperature)
                bridge.setTopP(topP)
                bridge.setMaxGenLen(1024) // Set a reasonable max length
                
                isInitialized = true
                _initialized.value = true
                _error.value = null
                
                // Update model info with more details if available
                modelInfo = "Gemma 2-2B-IT: A lightweight, efficient large language model optimized for mobile devices. " +
                           "Loaded from: ${internalModelDir.absolutePath}"
                
                Log.d(tag, "MlcLanguageModel initialized successfully")
            } catch (e: Exception) {
                val error = "FATAL ERROR: Error initializing MlcLanguageModel: ${e.message}"
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
            val error = "FATAL ERROR: Model not initialized"
            _error.value = error
            return error
        }
        
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Generating text for prompt: $prompt")
                val response = bridge.generateResponse(prompt)
                
                // If the response starts with "FATAL ERROR", propagate it to the error state
                if (response.startsWith("FATAL ERROR:")) {
                    _error.value = response
                    Log.e(tag, "Generation failed: $response")
                }
                
                Log.d(tag, "Generated response: $response")
                response
            } catch (e: Exception) {
                val error = "FATAL ERROR: Error generating text: ${e.message}"
                Log.e(tag, error, e)
                _error.value = error
                
                error
            }
        }
    }
    
    override fun streamText(prompt: String, onToken: (String) -> Unit, onError: (String) -> Unit) {
        if (!isInitialized) {
            val error = "FATAL ERROR: Model not initialized"
            _error.value = error
            onError(error)
            return
        }
        
        try {
            Log.d(tag, "Streaming text for prompt: $prompt")
            
            bridge.streamResponse(prompt) { token ->
                // If the token is an error message, propagate it
                if (token.startsWith("FATAL ERROR:")) {
                    val error = token
                    _error.value = error
                    Log.e(tag, "Streaming error: $error")
                    onError(error)
                } else {
                    onToken(token)
                }
            }
        } catch (e: Exception) {
            val error = "FATAL ERROR: Error streaming text: ${e.message}"
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
                    bridge.closeEngine()
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
        }
    }
    
    override fun onTopPChanged(newValue: Float) {
        if (isInitialized) {
            bridge.setTopP(newValue)
        }
    }
} 