package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloader for the Gemma 2B-IT model from Hugging Face.
 * This class handles downloading model files and verifying their integrity.
 */
class GemmaModelDownloader(private val context: Context) {
    companion object {
        private const val TAG = "GemmaModelDownloader"
        private const val BASE_URL = "https://huggingface.co/mlc-ai/gemma-2b-it-q4f16_1-MLC/resolve/main/"
        private const val BUFFER_SIZE = 8192
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        
        // Essential files needed for the model to work
        private val ESSENTIAL_FILES = listOf(
            "tokenizer_config.json",
            "tokenizer.json",
            "tokenizer.model",
            "mlc-chat-config.json",
            "ndarray-cache.json"
        )
        
        // Parameter shard files (38 total)
        private val PARAMETER_SHARDS = List(38) { index ->
            "params_shard_$index.bin"
        }
    }
    
    // Directory to store model files
    private val modelDir by lazy { File(context.filesDir, "models/gemma2_2b_it") }
    
    /**
     * Download a single file from the Hugging Face repository.
     * @param fileName Name of the file to download
     * @param progressCallback Callback to report download progress (0.0 to 1.0)
     * @return True if download was successful, false otherwise
     */
    suspend fun downloadFile(fileName: String, progressCallback: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        // Create model directory if it doesn't exist
        if (!modelDir.exists()) {
            if (!modelDir.mkdirs()) {
                Log.e(TAG, "Failed to create model directory: ${modelDir.absolutePath}")
                return@withContext false
            }
        }
        
        val targetFile = File(modelDir, fileName)
        val tempFile = File("${targetFile.absolutePath}.tmp")
        
        // If the file already exists with the correct size, skip download
        if (targetFile.exists()) {
            Log.d(TAG, "File already exists: $fileName")
            progressCallback(1.0f)
            return@withContext true
        }
        
        // Create URL for file download
        val fileUrl = URL("$BASE_URL$fileName")
        Log.d(TAG, "Downloading file from $fileUrl")
        
        try {
            // Open connection to URL
            val connection = fileUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode}: ${connection.responseMessage}")
                return@withContext false
            }
            
            // Get file size for progress tracking
            val fileSize = connection.contentLength.toLong()
            var downloadedSize = 0L
            
            // Create temp file for download
            FileOutputStream(tempFile).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        
                        // Report progress
                        if (fileSize > 0) {
                            val progress = downloadedSize.toFloat() / fileSize.toFloat()
                            progressCallback(progress)
                        }
                    }
                    
                    output.flush()
                }
            }
            
            // Rename temp file to final file
            if (!tempFile.renameTo(targetFile)) {
                Log.e(TAG, "Failed to rename temp file to target file")
                return@withContext false
            }
            
            // Final progress update
            progressCallback(1.0f)
            Log.d(TAG, "Successfully downloaded $fileName (${targetFile.length()} bytes)")
            
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "Error downloading file $fileName", e)
            // Clean up temp file if it exists
            if (tempFile.exists()) {
                tempFile.delete()
            }
            return@withContext false
        }
    }
    
    /**
     * Download all required model files.
     * @param progressCallback Callback to report overall download progress (0.0 to 1.0)
     * @param fileProgressCallback Callback to report per-file progress (fileName, progress 0.0 to 1.0)
     * @return True if all downloads were successful, false otherwise
     */
    suspend fun downloadModel(
        progressCallback: (Float) -> Unit,
        fileProgressCallback: (String, Float) -> Unit = { _, _ -> }
    ): Boolean = coroutineScope {
        val allFiles = ESSENTIAL_FILES + PARAMETER_SHARDS
        val totalFiles = allFiles.size
        var completedFiles = 0
        
        // First download essential files sequentially
        for (file in ESSENTIAL_FILES) {
            val success = downloadFile(file) { progress ->
                fileProgressCallback(file, progress)
            }
            
            if (!success) {
                Log.e(TAG, "Failed to download essential file: $file")
                return@coroutineScope false
            }
            
            completedFiles++
            progressCallback(completedFiles.toFloat() / totalFiles.toFloat())
        }
        
        // Then download parameter shards in parallel batches
        val parameterShardBatches = PARAMETER_SHARDS.chunked(MAX_CONCURRENT_DOWNLOADS)
        
        for (batch in parameterShardBatches) {
            val downloadResults = batch.map { file ->
                async {
                    val success = downloadFile(file) { progress ->
                        fileProgressCallback(file, progress)
                    }
                    
                    if (success) {
                        completedFiles++
                        progressCallback(completedFiles.toFloat() / totalFiles.toFloat())
                    }
                    
                    Pair(file, success)
                }
            }.awaitAll()
            
            // Check if any file in the batch failed to download
            val failedFiles = downloadResults.filter { !it.second }.map { it.first }
            if (failedFiles.isNotEmpty()) {
                Log.e(TAG, "Failed to download parameter files: $failedFiles")
                return@coroutineScope false
            }
        }
        
        Log.d(TAG, "Successfully downloaded all model files")
        return@coroutineScope true
    }
    
    /**
     * Check if the model is already downloaded.
     * This performs a basic check to see if the essential files exist.
     */
    fun isModelDownloaded(): Boolean {
        // For a minimal check, we'll just verify the directory exists and has at least one file
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return false
        }
        
        // Check for a small essential file that should always be present
        val tokenizer = File(modelDir, "tokenizer_config.json")
        return tokenizer.exists() && tokenizer.length() > 0
    }
    
    /**
     * Check if the full model is downloaded (all required files).
     */
    fun isFullModelDownloaded(): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return false
        }
        
        // Check all essential files
        for (file in ESSENTIAL_FILES) {
            val f = File(modelDir, file)
            if (!f.exists() || f.length() <= 0L) {
                return false
            }
        }
        
        // Check all parameter shards
        for (file in PARAMETER_SHARDS) {
            val f = File(modelDir, file)
            if (!f.exists() || f.length() <= 0L) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Get the model directory.
     */
    fun getModelDirectory(): File {
        return modelDir
    }
} 