package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * MLC-LLM Language Model for on-device LLM capabilities.
 * Uses Google's Gemma 2 (2B parameters) model which is optimized for mobile devices.
 * 
 * This class now acts as a wrapper around SimpleMlcModel to maintain compatibility
 * with existing code that references MlcLanguageModel.
 */
class MlcLanguageModel(context: Context) : LanguageModel(context) {
    private val tag = "MlcLanguageModel"
    private val simpleModel = SimpleMlcModel(context) // Delegate to SimpleMlcModel
    private var modelInfo = "Gemma 2-2B-IT: A lightweight, efficient large language model optimized for mobile devices."
    
    // Model directory paths 
    private val internalModelDir by lazy { File(context.filesDir, "models/gemma2_2b_it") }
    
    /**
     * Check if model files are already available
     */
    suspend fun areModelFilesAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Just check if the directory exists - the model will create it if needed
            if (internalModelDir.exists()) {
                Log.d(tag, "Found model directory at ${internalModelDir.absolutePath}")
                return@withContext true
            }
            
            Log.d(tag, "Model directory not found")
            return@withContext false
        } catch (e: Exception) {
            Log.e(tag, "Error checking model files: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Initialize the model, optionally with an auth token for downloading
     */
    suspend fun initialize(authToken: String? = null): Boolean {
        return try {
            initialize()
            true
        } catch (e: Exception) {
            Log.e(tag, "Error initializing model: ${e.message}", e)
            false
        }
    }
    
    override suspend fun initialize() {
        simpleModel.initialize()
    }
    
    override suspend fun generateText(prompt: String): String {
        return simpleModel.generateText(prompt)
    }
    
    override fun streamText(prompt: String, onToken: (String) -> Unit, onError: (String) -> Unit) {
        simpleModel.streamText(prompt, onToken, onError)
    }
    
    override suspend fun reset() {
        simpleModel.reset()
    }
    
    override fun getModelInfo(): String {
        return modelInfo
    }
    
    override suspend fun shutdown() {
        simpleModel.shutdown()
    }
    
    override fun onTemperatureChanged(newValue: Float) {
        simpleModel.onTemperatureChanged(newValue)
    }
    
    override fun onTopPChanged(newValue: Float) {
        simpleModel.onTopPChanged(newValue)
    }
} 