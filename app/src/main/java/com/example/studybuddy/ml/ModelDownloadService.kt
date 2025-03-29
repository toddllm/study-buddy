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
 * Service to download ML model files
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
        private const val DOWNLOAD_DIR = "mlc_models"
        
        // Number of parameter shards (0-37 based on your output)
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
    
    fun setHuggingFaceToken(token: String) {
        hfToken = token
    }
    
    /**
     * Download model files
     * @return Directory containing all model files, or null if download failed
     */
    suspend fun downloadModelFiles(): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download of model files...")
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
                val success = downloadFile(fileUrl, file, true)
                
                if (!success) {
                    Log.e(TAG, "Failed to download config file: $fileName")
                    return@withContext null
                }
                
                filesDownloaded++
                _downloadProgress.value = filesDownloaded.toFloat() / totalFiles
            }
            
            // Now download parameter shards
            for (i in 0 until NUM_PARAM_SHARDS) {
                val fileName = "params_shard_$i.bin"
                val file = File(modelDir, fileName)
                val fileUrl = "$REPO_BASE_URL/$fileName"
                val success = downloadFile(fileUrl, file, false)
                
                if (!success) {
                    Log.e(TAG, "Failed to download parameter shard: $fileName")
                    return@withContext null
                }
                
                filesDownloaded++
                _downloadProgress.value = filesDownloaded.toFloat() / totalFiles
                
                Log.d(TAG, "Downloaded shard $i/${NUM_PARAM_SHARDS-1} (${_downloadProgress.value * 100}%)")
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
     * Download a file
     */
    private suspend fun downloadFile(url: String, outputFile: File, isSmallFile: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading $url to ${outputFile.absolutePath}")
            
            // Skip if file already exists
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "File already exists, skipping download: ${outputFile.name}")
                return@withContext true
            }
            
            // Create request with token if available
            val requestBuilder = Request.Builder().url(url)
            
            // Add auth header if token is available
            hfToken?.let {
                requestBuilder.headers(
                    Headers.Builder()
                        .add("Authorization", "Bearer $it")
                        .build()
                )
            }
            
            val request = requestBuilder.build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download file: ${response.code} - ${response.message}")
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
                                    
                                    // Log progress
                                    val progressPercent = (fileProgress * 100).toInt()
                                    if (progressPercent % 20 == 0) {
                                        Log.d(TAG, "File download progress: $progressPercent%")
                                    }
                                }
                            }
                            
                            return bytesRead
                        }
                    }.buffer()
                    
                    // Write to file
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        
                        while (source.read(buffer).also { bytesRead = it.toInt() } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                    
                    return@withContext true
                }
                
                Log.e(TAG, "Empty response body")
                return@withContext false
            }
            
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout downloading file", e)
            return@withContext false
        } catch (e: IOException) {
            Log.e(TAG, "IO error downloading file", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
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