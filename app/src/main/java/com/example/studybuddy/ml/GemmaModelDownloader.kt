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
import java.security.MessageDigest

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
        
        // Checksums for essential files (SHA-256)
        private val FILE_CHECKSUMS = mapOf(
            "tokenizer_config.json" to "75b24ea2b06f254e9f4e0633a9c0dbb0b051a6517bef557dfa234a5f70caa957",
            "tokenizer.json" to "23dc84c5517c16e248c3191f6b7d120ef53ffecc8b24e7b0d4128ad2a7c0c7dc",
            "tokenizer.model" to "cf5dfd8dbf9b1c6dcb66dea0e41084c40204df4ced345f4de358b07bd9e4c1ec",
            "mlc-chat-config.json" to "7184a3294871879922e028a9aaac6c41702058d20e79892883ebe7c21c7f2a32",
            "ndarray-cache.json" to "5e35e5f6144fb5aaaba7c1fa333cd89e39bd5a1301e1b89b4d5a1f1b1685f35d"
        )
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
            
            // For essential files, verify checksum
            if (FILE_CHECKSUMS.containsKey(fileName) && !verifyFileChecksum(targetFile, fileName)) {
                Log.w(TAG, "Checksum verification failed for existing file: $fileName. Re-downloading...")
            } else {
                progressCallback(1.0f)
                return@withContext true
            }
        }
        
        // Create URL for file download
        val fileUrl = URL("$BASE_URL$fileName")
        Log.d(TAG, "Downloading file from $fileUrl")
        
        try {
            // Check if we have a partial download to resume
            var downloadedSize = 0L
            if (tempFile.exists() && tempFile.length() > 0) {
                downloadedSize = tempFile.length()
                Log.d(TAG, "Found partial download for $fileName (${downloadedSize} bytes). Attempting to resume.")
            }
            
            // Open connection to URL
            val connection = fileUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000  // Longer timeout for large files
            connection.instanceFollowRedirects = true
            
            // Set range header for resumption if needed
            if (downloadedSize > 0) {
                connection.setRequestProperty("Range", "bytes=${downloadedSize}-")
                Log.d(TAG, "Setting Range header: bytes=${downloadedSize}-")
            }
            
            val responseCode = connection.responseCode
            
            // Check if range request was accepted (206) or normal request (200)
            val isResumingDownload = responseCode == HttpURLConnection.HTTP_PARTIAL
            
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode}: ${connection.responseMessage}")
                if (responseCode == 416) { // HTTP_RANGE_NOT_SATISFIABLE (code 416)
                    Log.e(TAG, "Range request not satisfiable, file on server may be smaller than local partial file")
                    tempFile.delete() // Delete corrupted temp file
                    return@withContext downloadFile(fileName, progressCallback) // Retry without resumption
                }
                return@withContext false
            }
            
            // Get file size for progress tracking
            val contentLength = connection.contentLength.toLong()
            val fileSize = if (isResumingDownload) {
                downloadedSize + contentLength
            } else {
                contentLength
            }
            
            // Prepare initial progress update
            if (fileSize > 0 && downloadedSize > 0) {
                val initialProgress = downloadedSize.toFloat() / fileSize.toFloat()
                progressCallback(initialProgress)
                Log.d(TAG, "Resuming download from ${(initialProgress * 100).toInt()}%")
            }
            
            // Create or append to temp file
            val outputStream = if (isResumingDownload) {
                FileOutputStream(tempFile, true) // Append mode
            } else {
                FileOutputStream(tempFile) // Create/overwrite
            }
            
            // Download the file
            outputStream.use { output ->
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
            
            // Verify checksum for essential files
            if (FILE_CHECKSUMS.containsKey(fileName) && !verifyFileChecksum(targetFile, fileName)) {
                Log.e(TAG, "Checksum verification failed for downloaded file: $fileName")
                targetFile.delete() // Delete corrupted file
                return@withContext false
            }
            
            // Final progress update
            progressCallback(1.0f)
            Log.d(TAG, "Successfully downloaded $fileName (${targetFile.length()} bytes)")
            
            return@withContext true
        } catch (e: IOException) {
            // If it's a temporary error (like network interruption), keep the partial file for future resumption
            if (tempFile.exists() && tempFile.length() > 0 && 
                (e is java.net.SocketTimeoutException || e is java.net.SocketException)) {
                Log.w(TAG, "Download interrupted for $fileName, will resume next time: ${e.message}")
                return@withContext false
            }
            
            Log.e(TAG, "Error downloading file $fileName", e)
            // Clean up temp file if it exists and this was a non-recoverable error
            if (tempFile.exists()) {
                tempFile.delete()
            }
            return@withContext false
        }
    }
    
    /**
     * Verify file integrity using SHA-256 checksum
     */
    private fun verifyFileChecksum(file: File, fileName: String): Boolean {
        val expectedChecksum = FILE_CHECKSUMS[fileName] ?: return true // Skip verification if no checksum
        
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            file.inputStream().use { inputStream ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            val calculatedChecksum = digest.digest().joinToString("") { 
                String.format("%02x", it) 
            }
            
            val isValid = calculatedChecksum == expectedChecksum
            if (isValid) {
                Log.d(TAG, "Checksum verification passed for $fileName")
            } else {
                Log.e(TAG, "Checksum verification failed for $fileName")
                Log.e(TAG, "Expected: $expectedChecksum")
                Log.e(TAG, "Calculated: $calculatedChecksum")
            }
            
            return isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying checksum for $fileName", e)
            return false
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
        
        // Verify all essential files are present with correct checksums
        val missingOrInvalidFiles = ESSENTIAL_FILES.filter { fileName ->
            val file = File(modelDir, fileName)
            !file.exists() || !verifyFileChecksum(file, fileName)
        }
        
        if (missingOrInvalidFiles.isNotEmpty()) {
            Log.e(TAG, "Missing or invalid essential files after download: $missingOrInvalidFiles")
            return@coroutineScope false
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
        return tokenizer.exists() && tokenizer.length() > 0L
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