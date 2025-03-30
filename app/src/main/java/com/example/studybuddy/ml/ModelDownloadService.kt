package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Headers
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Service to download ML model files from Hugging Face
 */
class ModelDownloadService(private val context: Context) {
    companion object {
        private const val TAG = "ModelDownloadService"
        
        // Base URL for the repository
        private const val REPO_BASE_URL = "https://huggingface.co/mlc-ai/gemma-2b-it-q4f16_1-MLC/resolve/main"
        
        // Required files
        private const val CONFIG_FILE = "mlc-chat-config.json"
        private const val TOKENIZER_FILE = "tokenizer.json"
        private const val TOKENIZER_MODEL_FILE = "tokenizer.model"
        private const val TOKENIZER_CONFIG_FILE = "tokenizer_config.json"
        private const val NDARRAY_CACHE_FILE = "ndarray-cache.json"
        
        // Constants for download directory
        private const val DOWNLOAD_DIR = "models/gemma2_2b_it"
        
        // Number of parameter shards (0-37 based on ndarray-cache)
        private const val NUM_PARAM_SHARDS = 38
    }
    
    // Track progress
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
        
    private var hfToken: String? = null
    
    /**
     * Set Hugging Face token for downloading gated models
     */
    fun setHuggingFaceToken(token: String) {
        hfToken = token.trim()
        Log.d(TAG, "HF token set" + (if (token.isNotEmpty()) " (non-empty)" else " (empty)"))
    }
    
    /**
     * Download model files from Hugging Face
     * @return Directory containing all model files, or null if download failed
     */
    suspend fun downloadModelFiles(): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download of model files from $REPO_BASE_URL")
            _downloadProgress.value = 0f
            
            val modelDir = prepareDownloadDirectory()
            
            // List of files to download
            val configFiles = listOf(
                CONFIG_FILE,
                TOKENIZER_FILE,
                TOKENIZER_MODEL_FILE,
                TOKENIZER_CONFIG_FILE,
                NDARRAY_CACHE_FILE
            )
            
            // Download config files first
            var filesDownloaded = 0
            val totalFiles = configFiles.size + NUM_PARAM_SHARDS
            
            for (fileName in configFiles) {
                val file = File(modelDir, fileName)
                val fileUrl = "$REPO_BASE_URL/$fileName"
                Log.d(TAG, "Downloading config file: $fileName")
                
                val success = downloadFile(fileUrl, file, true)
                
                if (!success) {
                    Log.e(TAG, "Failed to download config file: $fileName")
                    return@withContext null
                }
                
                filesDownloaded++
                _downloadProgress.value = filesDownloaded.toFloat() / totalFiles
                Log.d(TAG, "Config file downloaded: $fileName, progress: ${(_downloadProgress.value * 100).toInt()}%")
            }
            
            // Now download parameter shards
            for (i in 0 until NUM_PARAM_SHARDS) {
                val fileName = "params_shard_$i.bin"
                val file = File(modelDir, fileName)
                val fileUrl = "$REPO_BASE_URL/$fileName"
                
                // Only download if the file doesn't exist or is empty
                if (!file.exists() || file.length() == 0L) {
                    Log.d(TAG, "Downloading parameter shard: $fileName")
                    val success = downloadFile(fileUrl, file, false)
                    
                    if (!success) {
                        Log.e(TAG, "Failed to download parameter shard: $fileName")
                        return@withContext null
                    }
                } else {
                    Log.d(TAG, "Parameter shard already exists, skipping: $fileName")
                }
                
                filesDownloaded++
                _downloadProgress.value = filesDownloaded.toFloat() / totalFiles
                
                Log.d(TAG, "Downloaded shard $i/${NUM_PARAM_SHARDS-1} (${(_downloadProgress.value * 100).toInt()}%)")
            }
            
            // Verify integrity
            val success = validateDownloadedFiles(modelDir, configFiles)
            if (!success) {
                Log.e(TAG, "Validation of downloaded files failed")
                return@withContext null
            }
            
            _downloadProgress.value = 1f
            Log.d(TAG, "Successfully downloaded all model files to $modelDir")
            return@withContext modelDir
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model files", e)
            return@withContext null
        }
    }
    
    /**
     * Validate downloaded files
     */
    private fun validateDownloadedFiles(modelDir: File, configFiles: List<String>): Boolean {
        // Check that all config files exist and are not empty
        for (fileName in configFiles) {
            val file = File(modelDir, fileName)
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "Validation failed: $fileName does not exist or is empty")
                return false
            }
        }
        
        // Check that at least some parameter shards exist
        var shardCount = 0
        for (i in 0 until NUM_PARAM_SHARDS) {
            val file = File(modelDir, "params_shard_$i.bin")
            if (file.exists() && file.length() > 0L) {
                shardCount++
            }
        }
        
        if (shardCount < NUM_PARAM_SHARDS) {
            Log.w(TAG, "Warning: Only $shardCount out of $NUM_PARAM_SHARDS parameter shards were downloaded")
        }
        
        return true
    }
    
    /**
     * Download a file from a URL
     * @param url URL to download from
     * @param outputFile File to write to
     * @param isSmallFile Whether this is a small file (config) or large file (parameter shard)
     * @return true if download was successful, false otherwise
     */
    private suspend fun downloadFile(url: String, outputFile: File, isSmallFile: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading $url to ${outputFile.absolutePath}")
            
            // Skip if file already exists and has content
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "File already exists with ${outputFile.length()} bytes, skipping download: ${outputFile.name}")
                return@withContext true
            }
            
            // Create request with token if available
            val requestBuilder = Request.Builder().url(url)
            
            // Add auth header if token is available
            if (!hfToken.isNullOrEmpty()) {
                Log.d(TAG, "Adding HF token to request")
                requestBuilder.headers(
                    Headers.Builder()
                        .add("Authorization", "Bearer $hfToken")
                        .build()
                )
            }
            
            val request = requestBuilder.build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download file: ${response.code} - ${response.message}")
                    if (response.code == 401 || response.code == 403) {
                        Log.e(TAG, "Authorization error - check your Hugging Face token")
                    }
                    return@withContext false
                }
                
                // Write the file
                response.body?.let { body ->
                    val contentLength = body.contentLength()
                    
                    // For small files (like config), no need to track progress
                    if (isSmallFile) {
                        FileOutputStream(outputFile).use { output ->
                            body.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        return@withContext true
                    }
                    
                    // For large files (model), track progress
                    var totalBytes = 0L
                    
                    val source = object : ForwardingSource(body.source()) {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            val bytesRead = super.read(sink, byteCount)
                            
                            if (bytesRead != -1L) {
                                totalBytes += bytesRead
                                
                                // Log progress only for parameter shards
                                if (contentLength > 0 && !isSmallFile) {
                                    // We don't update the global progress here as it's managed at the file level
                                    val fileProgress = totalBytes.toFloat() / contentLength.toFloat()
                                    
                                    // Log progress at 10% intervals
                                    val progressPercent = (fileProgress * 100).toInt()
                                    if (progressPercent % 10 == 0 && progressPercent > 0) {
                                        Log.d(TAG, "File download progress for ${outputFile.name}: $progressPercent%")
                                    }
                                }
                            }
                            
                            return bytesRead
                        }
                    }.buffer()
                    
                    // Create parent directories if they don't exist
                    outputFile.parentFile?.mkdirs()
                    
                    // Write to file
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (source.read(buffer).also { bytesRead = it.toInt() } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                    
                    Log.d(TAG, "Downloaded ${outputFile.name}: ${outputFile.length()} bytes")
                    return@withContext true
                }
                
                Log.e(TAG, "Empty response body")
                return@withContext false
            }
            
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout downloading file: ${outputFile.name}", e)
            return@withContext false
        } catch (e: IOException) {
            Log.e(TAG, "IO error downloading file: ${outputFile.name}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file: ${outputFile.name}", e)
            return@withContext false
        }
    }
    
    /**
     * Prepare the download directory
     */
    private fun prepareDownloadDirectory(): File {
        val downloadDir = File(context.filesDir, DOWNLOAD_DIR)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        return downloadDir
    }
} 