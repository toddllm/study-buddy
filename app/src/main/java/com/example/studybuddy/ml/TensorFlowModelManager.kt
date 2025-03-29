package com.example.studybuddy.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.IOException
import java.lang.Runtime
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Manages TensorFlow Lite models for the app.
 * This class now integrates MLC-LLM for more powerful on-device LLM capabilities.
 */
class TensorFlowModelManager(private val context: Context) {
    private val TAG = "TFModelManager"
    
    // Memory threshold for safe operation (in bytes)
    private val MIN_REQUIRED_MEMORY = 300L * 1024 * 1024 // 300MB
    
    // Model managers
    private val mlcModel = MlcLanguageModel(context)
    private val languageModel = OnDeviceLanguageModel(context)
    private val imageClassifier = OnDeviceImageClassifier(context)
    private val ocrProcessor = OcrProcessor(context)
    
    // Model status
    private var mlcInitialized = false
    private var tfInitialized = false
    
    /**
     * Initialize the models with priority to MLC-LLM
     */
    suspend fun initialize(hfToken: String? = null): Boolean {
        Log.d(TAG, "Initializing models...")
        
        if (!hasEnoughMemory()) {
            Log.e(TAG, "Device doesn't have enough memory")
            return false
        }
        
        // First check if MLC model files are available
        val mlcFilesAvailable = mlcModel.areModelFilesAvailable()
        
        if (mlcFilesAvailable) {
            Log.d(TAG, "MLC-LLM model files available, initializing...")
            
            // First try to initialize MLC-LLM
            mlcInitialized = try {
                mlcModel.initialize(hfToken)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MLC-LLM model", e)
                false
            }
        } else {
            Log.d(TAG, "MLC-LLM model files not available, skipping initialization")
            mlcInitialized = false
        }
        
        // Fall back to TensorFlow models if MLC fails or is unavailable
        if (!mlcInitialized) {
            Log.d(TAG, "MLC-LLM not available, falling back to TensorFlow models")
            
            val langSuccess = try {
                languageModel.initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TF language model", e)
                false
            }
            
            val imageSuccess = try {
                imageClassifier.initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TF image classifier", e)
                false
            }
            
            tfInitialized = langSuccess || imageSuccess
        }
        
        val success = mlcInitialized || tfInitialized
        Log.d(TAG, "Models initialized: MLC-LLM=$mlcInitialized, TensorFlow=$tfInitialized")
        return success
    }
    
    /**
     * Checks if the device has enough memory to load and run the models
     */
    fun hasEnoughMemory(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val freeMemory = runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val usedMemory = totalMemory - freeMemory
        
        Log.d(TAG, "Memory - Max: ${maxMemory/1024/1024}MB, Total: ${totalMemory/1024/1024}MB, " +
              "Free: ${freeMemory/1024/1024}MB, Used: ${usedMemory/1024/1024}MB")
        
        // Check if the max memory is less than our threshold
        if (maxMemory < MIN_REQUIRED_MEMORY) {
            Log.e(TAG, "Device has insufficient max memory: ${maxMemory/1024/1024}MB < ${MIN_REQUIRED_MEMORY/1024/1024}MB")
            return false
        }
        
        // Check available storage
        val internalStorage = StatFs(context.filesDir.absolutePath)
        val availableBytes = internalStorage.availableBytes
        Log.d(TAG, "Available internal storage: ${availableBytes/1024/1024}MB")
        
        if (availableBytes < MIN_REQUIRED_MEMORY) {
            Log.e(TAG, "Device has insufficient storage: ${availableBytes/1024/1024}MB < ${MIN_REQUIRED_MEMORY/1024/1024}MB")
            return false
        }
        
        return true
    }
    
    /**
     * Process an image and generate an educational response.
     * This combines image classification with text processing.
     */
    suspend fun processImage(bitmap: Bitmap, userQuestion: String): String {
        Log.d(TAG, "Processing image with user question: $userQuestion")
        
        try {
            // First make sure models are initialized
            if (!mlcInitialized && !tfInitialized && !initialize()) {
                return "I couldn't initialize any of the models. " +
                       "This may be due to memory constraints on your device."
            }
            
            // Track processing time
            val processingTimeMs = measureTimeMillis {
                // Placeholder for actual processing
            }
            
            // Use OCR with MLC if available
            if (mlcInitialized) {
                val ocrText = ocrProcessor.extractText(bitmap)
                if (ocrText.isNotEmpty()) {
                    // Build a prompt with the OCR text and user question
                    val prompt = "I have this text extracted from an image: '$ocrText'. Based on this text, $userQuestion"
                    return mlcModel.generateText(prompt)
                }
            }
            
            // Fallback to TensorFlow
            if (tfInitialized) {
                val classes = imageClassifier.classifyImage(bitmap)
                val topLabels = classes.take(3).joinToString(", ") {
                    "${it.label} (${(it.confidence * 100).toInt()}%)"
                }
                
                val imageContext = "The image shows: $topLabels."
                return languageModel.getResponse(imageContext, userQuestion)
            }
            
            Log.d(TAG, "Total image processing time: $processingTimeMs ms")
            return "Sorry, I couldn't process this image. No working models were available."
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            return "Sorry, I encountered an error while processing the image. " +
                   "This might be due to memory limitations or the complexity of the image. " +
                   "Error details: ${e.message}"
        }
    }
    
    /**
     * Process OCR text and generate an educational response.
     */
    suspend fun processText(ocrText: String, userQuestion: String): String {
        return try {
            if (mlcInitialized) {
                // Build a prompt with the OCR text and user question
                val prompt = "I have this text extracted from an image: '$ocrText'. Based on this text, $userQuestion"
                mlcModel.generateText(prompt)
            } else {
                languageModel.getResponse(ocrText, userQuestion)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing text", e)
            "Sorry, I encountered an error while processing your text: ${e.message}"
        }
    }
    
    /**
     * Get diagnostic information about the device and models
     */
    fun getDiagnostics(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val freeMemory = runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val usedMemory = totalMemory - freeMemory
        
        val internalStorage = StatFs(context.filesDir.absolutePath)
        val availableBytes = internalStorage.availableBytes
        
        val mlcStatus = if (mlcInitialized) {
            "MLC-LLM: Initialized\n" + mlcModel.getModelInfo()
        } else {
            "MLC-LLM: Not initialized"
        }
        
        val tfModels = listOf(
            "mobilebert_qa.tflite", 
            "mobilenet_v1.tflite", 
            "mobilenet_v1_tiny.tflite",
            "mobilenet_quant_v1_224.tflite",
            "efficientnet_lite0.tflite",
            "text_classification.tflite"
        )
        val modelStatus = tfModels.map { modelName ->
            val assetFile = try {
                context.assets.open(modelName).close()
                "Available in assets"
            } catch (e: Exception) {
                "Not in assets"
            }
            
            val file = File(context.filesDir, modelName)
            "$modelName: ${if (file.exists()) "Extracted (${file.length() / 1024.0}KB)" else assetFile}"
        }.joinToString("\n")
        
        return "Device Information:\n" +
               "- Model: ${Build.MODEL}\n" +
               "- SDK: ${Build.VERSION.SDK_INT}\n" +
               "- Max Memory: ${maxMemory/1024/1024} MB\n" +
               "- Free Memory: ${freeMemory/1024/1024} MB\n" +
               "- Used Memory: ${usedMemory/1024/1024} MB\n" +
               "- Available Storage: ${availableBytes/1024/1024} MB\n\n" +
               "Model Status:\n$mlcStatus\n\n" +
               "TensorFlow Models:\n$modelStatus"
    }
    
    /**
     * Close and cleanup resources
     */
    fun close() {
        try {
            // Launch a coroutine in a new scope to call suspend functions
            GlobalScope.launch {
                try {
                    mlcModel.shutdown()
                    // languageModel doesn't have shutdown method
                } catch (e: Exception) {
                    Log.e(TAG, "Error shutting down language models", e)
                }
            }
            
            // Close non-suspend resources directly
            imageClassifier.close()
            ocrProcessor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing models", e)
        }
    }
    
    /**
     * Process OCR text with LLM
     */
    suspend fun processOcrText(ocrText: String, userQuestion: String): String {
        if (!mlcInitialized && !tfInitialized && !initialize()) {
            return "Model not initialized. Please initialize the model first."
        }
        
        try {
            // Build a prompt with the OCR text and user question
            val prompt = "I have this text extracted from an image: '$ocrText'. Based on this text, $userQuestion"
            
            // Process with MLC-LLM model if available
            return mlcModel.generateText(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing OCR text", e)
            return "Error processing OCR text: ${e.message}"
        }
    }
    
    /**
     * Process OCR text with LLM in a streaming fashion
     */
    fun streamProcessOcrText(ocrText: String, userQuestion: String, callback: (String) -> Unit) {
        if (!mlcInitialized && !tfInitialized) {
            // Try to initialize in a background coroutine
            GlobalScope.launch {
                val initialized = initialize()
                if (!initialized) {
                    callback("Model not initialized. Failed to initialize.")
                    return@launch
                }
                
                // Now that we're initialized, process the OCR text
                streamProcessOcrTextInternal(ocrText, userQuestion, callback)
            }
            return
        }
        
        // Already initialized, can proceed directly
        streamProcessOcrTextInternal(ocrText, userQuestion, callback)
    }
    
    // Helper method that doesn't need to deal with initialization
    private fun streamProcessOcrTextInternal(ocrText: String, userQuestion: String, callback: (String) -> Unit) {
        try {
            // Build a prompt with the OCR text and user question
            val prompt = "I have this text extracted from an image: '$ocrText'. Based on this text, $userQuestion"
            
            // Process with MLC-LLM model in streaming mode
            mlcModel.streamText(prompt, callback) { error ->
                callback("Error: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming OCR text", e)
            callback("Error streaming OCR text: ${e.message}")
        }
    }
    
    /**
     * Stream process an image with LLM in a streaming fashion
     */
    fun streamProcessImage(bitmap: Bitmap, userQuestion: String, callback: (String) -> Unit) {
        if (!mlcInitialized && !tfInitialized) {
            // Try to initialize in a background coroutine
            GlobalScope.launch {
                val initialized = initialize()
                if (!initialized) {
                    callback("Failed to initialize models.")
                    return@launch
                }
                
                processImageInCoroutine(bitmap, userQuestion, callback)
            }
            return
        }
        
        // Process if already initialized
        GlobalScope.launch {
            processImageInCoroutine(bitmap, userQuestion, callback)
        }
    }
    
    // Helper method to process image in a coroutine context
    private suspend fun processImageInCoroutine(bitmap: Bitmap, userQuestion: String, callback: (String) -> Unit) {
        try {
            val ocrText = ocrProcessor.extractText(bitmap)
            val prompt = if (ocrText.isNotEmpty()) {
                "I have this text extracted from an image: '$ocrText'. Based on this text, $userQuestion"
            } else {
                "I'm looking at an image. $userQuestion"
            }
            
            mlcModel.streamText(prompt, callback) { error ->
                callback("Error: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stream processing", e)
            callback("Error processing image: ${e.message}")
        }
    }
} 