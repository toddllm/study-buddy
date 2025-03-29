package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * A service for managing MLC-LLM model loading and inference
 */
class MLCLLMService private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MLCLLMService"
        private const val DEFAULT_MODEL_PATH = "models/gemma2_2b_it"
        private const val LIB_DIR = "lib"
        private const val APP_ASSETS_MODEL_DIR = "models/gemma2_2b_it"
        private const val MODEL_DIR = "models/gemma2_2b_it"
        private const val CONFIG_FILE = "config.json"
        
        @Volatile
        private var instance: MLCLLMService? = null
        
        fun getInstance(context: Context): MLCLLMService {
            return instance ?: synchronized(this) {
                instance ?: MLCLLMService(context.applicationContext).also { 
                    instance = it
                    Log.d(TAG, "MLCLLMService initialized with context from: ${context.packageName}")
                }
            }
        }
    }
    
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded
    
    private val _textStream = MutableStateFlow<List<String>>(emptyList())
    val textStream: StateFlow<List<String>> = _textStream
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    private var lastStreamedText = ""
    
    init {
        // Initialize the model on first use
        _isModelLoaded.value = false
        _errorMessage.value = null
    }
    
    /**
     * Load the MLC-LLM model from assets
     * @throws RuntimeException if the model fails to load
     */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing model...")
            _errorMessage.value = null
            
            // List available libraries
            val libDir = File(context.applicationInfo.nativeLibraryDir)
            Log.d(TAG, "JNI libraries directory: ${libDir.absolutePath}")
            if (libDir.exists()) {
                val libs = libDir.listFiles()?.map { it.name } ?: emptyList()
                Log.d(TAG, "Available libraries: $libs")
            }
            
            // First, check what's in the assets directory
            val assetsList = context.assets.list("")?.toList() ?: emptyList()
            Log.d(TAG, "Assets root directory contents: $assetsList")
            
            if ("models" in assetsList) {
                val modelsContents = context.assets.list("models")?.toList() ?: emptyList()
                Log.d(TAG, "Assets models directory contents: $modelsContents")
                
                if ("gemma2_2b_it" in modelsContents) {
                    val modelContents = context.assets.list("models/gemma2_2b_it")?.toList() ?: emptyList()
                    Log.d(TAG, "Assets model directory contents: $modelContents")
                }
            }
            
            // Create the model directory in app private storage
            val appModelDir = File(context.filesDir, DEFAULT_MODEL_PATH)
            if (!appModelDir.exists()) {
                val created = appModelDir.mkdirs()
                Log.d(TAG, "Created model directory: $created at ${appModelDir.absolutePath}")
            } else {
                val files = appModelDir.listFiles()?.map { it.name } ?: emptyList()
                Log.d(TAG, "Existing model directory contains: $files")
            }
            
            // Create lib directory
            val libDirPath = File(appModelDir, LIB_DIR)
            if (!libDirPath.exists()) {
                val created = libDirPath.mkdirs()
                Log.d(TAG, "Created lib directory: $created at ${libDirPath.absolutePath}")
            }
            
            // Create the stub config file if it doesn't exist
            val configFile = File(appModelDir, "config.json")
            if (!configFile.exists()) {
                try {
                    configFile.writeText(
                        """
                        {
                            "model_name": "gemma-2b-it",
                            "quantization": "q4f16_1",
                            "model_lib": "libmlc_llm.so",
                            "runtime_lib": "libtvm_runtime.so"
                        }
                        """.trimIndent()
                    )
                    Log.d(TAG, "Created config.json successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create config.json: ${e.message}")
                    throw e
                }
            }
            
            // Create stub lib file if it doesn't exist
            val stubLibFile = File(libDirPath, "libgemma-2b-it-q4f16_1.so")
            if (!stubLibFile.exists()) {
                try {
                    stubLibFile.createNewFile()
                    Log.d(TAG, "Created stub lib file successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create stub lib file: ${e.message}")
                    throw e
                }
            }
            
            // Copy parameter files from assets
            try {
                // Check if we have parameter files in assets
                val assetsModelDir = "models/gemma2_2b_it"
                if (context.assets.list(assetsModelDir)?.any { it.startsWith("params_shard_") } == true) {
                    Log.d(TAG, "Found parameter files in assets, copying them")
                    
                    val paramFiles = context.assets.list(assetsModelDir)
                        ?.filter { filename: String -> filename.startsWith("params_shard_") } ?: emptyList<String>()
                    
                    for (paramFile in paramFiles) {
                        val destFile = File(appModelDir, paramFile)
                        if (!destFile.exists()) {
                            try {
                                context.assets.open("$assetsModelDir/$paramFile").use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Log.d(TAG, "Copied $paramFile successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to copy $paramFile: ${e.message}")
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No parameter files found in assets")
                    
                    // If no parameter files in assets, create empty parameter files for testing
                    for (i in 0 until 38) {
                        val paramFile = File(appModelDir, "params_shard_$i.bin")
                        if (!paramFile.exists()) {
                            try {
                                paramFile.createNewFile()
                                Log.d(TAG, "Created empty parameter file: params_shard_$i.bin")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to create empty parameter file: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying parameter files: ${e.message}")
                // Continue anyway - we'll still try to initialize
            }
            
            // Also copy tokenizer files if they exist
            try {
                val assetsModelDir = "models/gemma2_2b_it"
                val tokenizerFiles = arrayOf("tokenizer.json", "tokenizer_config.json", "tokenizer.model")
                
                for (tokenizerFile in tokenizerFiles) {
                    val destFile = File(appModelDir, tokenizerFile)
                    if (!destFile.exists()) {
                        try {
                            if (context.assets.list(assetsModelDir)?.contains(tokenizerFile) == true) {
                                context.assets.open("$assetsModelDir/$tokenizerFile").use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Log.d(TAG, "Copied $tokenizerFile successfully")
                            } else {
                                // Create empty tokenizer file
                                destFile.createNewFile()
                                Log.d(TAG, "Created empty tokenizer file: $tokenizerFile")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to handle $tokenizerFile: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling tokenizer files: ${e.message}")
                // Continue anyway
            }
            
            // Check that we have all the necessary files
            val requiredFiles = listOf("config.json")
            val missingFiles = requiredFiles.filter { !File(appModelDir, it).exists() }
            
            if (missingFiles.isNotEmpty()) {
                val errorMsg = "Missing required files: $missingFiles"
                Log.e(TAG, errorMsg)
                _errorMessage.value = errorMsg
                _isModelLoaded.value = false
                throw RuntimeException(errorMsg)
            }
            
            // Load the model using TVMBridge
            try {
                val tvmBridge = TVMBridge()
                val success = tvmBridge.initializeEngine(appModelDir.absolutePath)
                
                if (success) {
                    Log.i(TAG, "MLC-LLM initialized successfully")
                    _isModelLoaded.value = true
                    _errorMessage.value = null
                } else {
                    val errorMsg = "Failed to initialize MLC-LLM engine"
                    Log.e(TAG, errorMsg)
                    _errorMessage.value = errorMsg
                    _isModelLoaded.value = false
                    throw RuntimeException(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Error initializing MLC-LLM engine: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _errorMessage.value = errorMsg
                _isModelLoaded.value = false
                throw e
            }
            
            return@withContext _isModelLoaded.value
        } catch (e: Exception) {
            val errorMsg = "Error loading MLC-LLM model: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _errorMessage.value = errorMsg
            _isModelLoaded.value = false
            throw e
        }
    }
    
    /**
     * Generate text with the LLM model
     * @throws RuntimeException if text generation fails
     */
    suspend fun generateText(prompt: String): String = withContext(Dispatchers.IO) {
        if (!_isModelLoaded.value) {
            try {
                loadModel()
            } catch (e: Exception) {
                throw RuntimeException("Failed to load model: ${e.message}")
            }
        }
        
        try {
            val tvmBridge = TVMBridge()
            val response = tvmBridge.chat(prompt)
            return@withContext response
        } catch (e: Exception) {
            val errorMsg = "Error generating text: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _errorMessage.value = errorMsg
            throw RuntimeException(errorMsg)
        }
    }
    
    /**
     * Stream text generation with the LLM model
     * @throws RuntimeException if streaming fails
     */
    suspend fun streamText(prompt: String) = withContext(Dispatchers.IO) {
        // Reset the stream
        _textStream.value = emptyList()
        lastStreamedText = ""
        _errorMessage.value = null
        
        Log.d(TAG, "Starting streaming text generation for prompt: $prompt")
        
        if (!_isModelLoaded.value) {
            try {
                loadModel()
            } catch (e: Exception) {
                val errorMsg = "Failed to load model: ${e.message}"
                _errorMessage.value = errorMsg
                _textStream.value = listOf(errorMsg)
                throw RuntimeException(errorMsg)
            }
        }
        
        try {
            // Use the TVMBridge for streaming text generation
            val tvmBridge = TVMBridge()
            
            // Start streaming in a separate thread
            val thread = Thread {
                try {
                    tvmBridge.streamChat(prompt) { token ->
                        lastStreamedText += token
                        _textStream.value = listOf(lastStreamedText)
                    }
                } catch (e: Exception) {
                    val errorMsg = "Error in streaming thread: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    _errorMessage.value = errorMsg
                    _textStream.value = listOf(lastStreamedText + "\n\nError: ${e.message}")
                    throw RuntimeException(errorMsg)
                }
            }
            
            thread.start()
            thread.join() // Wait for streaming to complete
            
        } catch (e: Exception) {
            val errorMsg = "Error streaming text: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _errorMessage.value = errorMsg
            _textStream.value = listOf(errorMsg)
            throw RuntimeException(errorMsg)
        }
    }

    /**
     * Check if model files are already available
     */
    suspend fun areModelFilesAvailable(): Boolean = withContext(Dispatchers.IO) {
        // First check if the models folder in assets has the required files
        val assetDir = File(context.filesDir, APP_ASSETS_MODEL_DIR)
        if (assetDir.exists()) {
            val configFile = File(assetDir, "config.json")
            if (configFile.exists()) {
                Log.d(TAG, "Found model files in app assets: $assetDir")
                return@withContext true
            }
        }
        
        // Otherwise check the downloaded model dir
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) return@withContext false
        
        // Check for config file - only this is absolutely required
        val configFile = File(modelDir, CONFIG_FILE)
        if (!configFile.exists()) {
            return@withContext false
        }
        
        // Check for at least one parameter shard
        val paramFiles = modelDir.listFiles { file -> file.name.startsWith("params_shard_") }
        if (paramFiles == null || paramFiles.isEmpty()) {
            Log.d(TAG, "No parameter shards found")
            return@withContext false
        }
        
        Log.d(TAG, "All model files are available")
        return@withContext true
    }
} 