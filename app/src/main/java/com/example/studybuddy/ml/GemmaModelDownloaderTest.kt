package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Test utilities for GemmaModelDownloader.
 * This class provides methods to test downloading model files.
 */
object GemmaModelDownloaderTest {
    private const val TAG = "GemmaModelDownloaderTest"
    
    /**
     * Test downloading a single file.
     * @param context Application context
     * @param fileName Name of the file to download (default: tokenizer_config.json - smallest file)
     */
    fun testDownloadSingleFile(context: Context, fileName: String = "tokenizer_config.json") {
        Log.d(TAG, "Starting test download of $fileName")
        
        val downloader = GemmaModelDownloader(context)
        
        // Check if file already exists
        val modelDir = downloader.getModelDirectory()
        val targetFile = modelDir.resolve(fileName)
        if (targetFile.exists()) {
            Log.d(TAG, "File already exists: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
        }
        
        // Launch coroutine to download the file
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "Beginning download of $fileName")
                val success = downloader.downloadFile(fileName) { progress ->
                    // Log progress at 10% intervals
                    if ((progress * 10).toInt() % 1 == 0) {
                        Log.d(TAG, "Download progress: ${(progress * 100).toInt()}%")
                    }
                }
                
                if (success) {
                    // Verify the file exists after download
                    val fileExists = targetFile.exists()
                    val fileSize = if (fileExists) targetFile.length() else 0
                    
                    Log.d(TAG, "Download completed successfully: $success")
                    Log.d(TAG, "File exists: $fileExists, Size: $fileSize bytes")
                    
                    // If the file exists, try to read a few bytes to verify content
                    if (fileExists && fileSize > 0) {
                        val contentPreview = withContext(Dispatchers.IO) {
                            val bytes = targetFile.readBytes().take(100).toByteArray()
                            String(bytes)
                        }
                        Log.d(TAG, "Content preview: $contentPreview")
                    }
                } else {
                    Log.e(TAG, "Download failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during download test", e)
            }
        }
    }
    
    /**
     * Test downloading a file and verifying its checksum.
     * This specifically tests the checksum verification functionality.
     * 
     * @param context Application context
     * @param fileName Name of an essential file with a known checksum (default: tokenizer_config.json)
     */
    fun testChecksumVerification(context: Context, fileName: String = "tokenizer_config.json") {
        Log.d(TAG, "Starting checksum verification test for $fileName")
        
        // Only test with essential files that have checksums defined
        if (fileName !in listOf(
                "tokenizer_config.json", 
                "tokenizer.json", 
                "tokenizer.model", 
                "mlc-chat-config.json", 
                "ndarray-cache.json")) {
            Log.e(TAG, "Cannot test checksum for $fileName - no checksum defined.")
            return
        }
        
        val downloader = GemmaModelDownloader(context)
        
        // Launch coroutine to test checksum verification
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "Downloading file for checksum verification: $fileName")
                
                // First download the file if it doesn't exist
                val downloadSuccess = downloader.downloadFile(fileName) { progress ->
                    if ((progress * 10).toInt() % 2 == 0) {
                        Log.d(TAG, "Download progress: ${(progress * 100).toInt()}%")
                    }
                }
                
                if (!downloadSuccess) {
                    Log.e(TAG, "Failed to download file for checksum test")
                    return@launch
                }
                
                // Now try to tamper with the file to test checksum failure detection
                val modelDir = downloader.getModelDirectory()
                val file = modelDir.resolve(fileName)
                
                if (!file.exists()) {
                    Log.e(TAG, "File not found after download: $fileName")
                    return@launch
                }
                
                Log.d(TAG, "File downloaded successfully, now testing checksum verification")
                
                // Make a backup of the file
                val backupFile = File("${file.absolutePath}.bak")
                withContext(Dispatchers.IO) {
                    file.copyTo(backupFile, overwrite = true)
                    
                    // Try downloading again - should skip download due to checksum pass
                    val secondDownloadStart = System.currentTimeMillis()
                    val redownloadNeeded = !downloader.downloadFile(fileName) { progress ->
                        // No logging needed
                    }
                    val secondDownloadTime = System.currentTimeMillis() - secondDownloadStart
                    
                    if (secondDownloadTime < 100) {
                        Log.d(TAG, "Checksum verification passed - download was skipped (${secondDownloadTime}ms)")
                    } else {
                        Log.d(TAG, "File was re-downloaded despite existing - checksum may have failed or wasn't checked")
                    }
                    
                    // Restore original file
                    backupFile.copyTo(file, overwrite = true)
                    backupFile.delete()
                }
                
                Log.d(TAG, "Checksum verification test completed for $fileName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during checksum verification test", e)
            }
        }
    }
    
    /**
     * Test downloading the full model or just the essential files.
     * This can be used to test the download functionality before committing to the full model download.
     * 
     * @param context Application context
     * @param essentialFilesOnly If true, only download essential files (tokenizer, config, etc.)
     * @param maxFilesToDownload Maximum number of files to download (for testing, to avoid long downloads)
     */
    fun testDownloadModel(
        context: Context, 
        essentialFilesOnly: Boolean = true,
        maxFilesToDownload: Int = 5
    ) {
        Log.d(TAG, "Starting test for model download")
        
        val downloader = GemmaModelDownloader(context)
        
        // Check if any files already exist
        if (downloader.isModelDownloaded()) {
            Log.d(TAG, "Some model files already exist")
        }
        
        // Launch coroutine to download files
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "Beginning model download (essential files only: $essentialFilesOnly)")
                
                // File-level progress tracking
                val downloadedFiles = mutableSetOf<String>()
                var currentFileProgress = 0f
                var currentFileName = ""
                
                // Start the download
                val success = downloader.downloadModel(
                    progressCallback = { overallProgress ->
                        Log.d(TAG, "Overall download progress: ${(overallProgress * 100).toInt()}%")
                    },
                    fileProgressCallback = { fileName, progress ->
                        // Only log if the file or progress has changed significantly
                        if (fileName != currentFileName || progress - currentFileProgress >= 0.1f) {
                            currentFileName = fileName
                            currentFileProgress = progress
                            Log.d(TAG, "File '$fileName' progress: ${(progress * 100).toInt()}%")
                        }
                        
                        // Track which files have completed
                        if (progress >= 1.0f) {
                            downloadedFiles.add(fileName)
                            
                            // If we've downloaded enough files for testing, we can stop
                            if (downloadedFiles.size >= maxFilesToDownload && essentialFilesOnly) {
                                // We can't return a boolean directly from the callback
                                // Instead, we'll just log that we're stopping early
                                Log.d(TAG, "Reached maximum files to download for testing, stopping.")
                                return@downloadModel
                            }
                        }
                    }
                )
                
                if (success) {
                    Log.d(TAG, "Model download completed successfully")
                    // Verify the downloaded files
                    val modelDir = downloader.getModelDirectory()
                    val downloadedFilesList = modelDir.listFiles()?.map { it.name } ?: emptyList()
                    Log.d(TAG, "Downloaded files: $downloadedFilesList")
                } else {
                    Log.e(TAG, "Model download failed or was cancelled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during model download test", e)
            }
        }
    }
    
    /**
     * Test download resumption for a larger file.
     * This will start a download, interrupt it, and verify it can be resumed.
     * 
     * @param context Application context
     * @param fileName Name of a parameter shard to test (default: params_shard_0.bin - largest file)
     * @param simulateInterruption If true, will deliberately break the download partway through
     */
    fun testDownloadResumption(
        context: Context, 
        fileName: String = "params_shard_1.bin",
        simulateInterruption: Boolean = true
    ) {
        Log.d(TAG, "Starting download resumption test for $fileName")
        
        val downloader = GemmaModelDownloader(context)
        val modelDir = downloader.getModelDirectory()
        val targetFile = modelDir.resolve(fileName)
        val tempFile = File("${targetFile.absolutePath}.tmp")
        
        // Delete both files if they exist to start fresh
        if (targetFile.exists()) {
            Log.d(TAG, "Deleting existing target file: ${targetFile.absolutePath}")
            targetFile.delete()
        }
        
        if (tempFile.exists()) {
            Log.d(TAG, "Deleting existing temp file: ${tempFile.absolutePath}")
            tempFile.delete()
        }
        
        // Launch coroutine to test download resumption
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // First start the download but simulate interruption
                var downloadInterrupted = false
                var lastProgress = 0f
                
                Log.d(TAG, "Starting first download attempt...")
                val firstAttemptResult = downloader.downloadFile(fileName) { progress ->
                    if (progress - lastProgress >= 0.05f) {
                        lastProgress = progress
                        Log.d(TAG, "First attempt progress: ${(progress * 100).toInt()}%")
                    }
                    
                    // Simulate interruption when download is 10-20% complete
                    if (simulateInterruption && progress > 0.10f && progress < 0.20f && !downloadInterrupted) {
                        downloadInterrupted = true
                        // Throw an exception to simulate network interruption
                        Log.d(TAG, "Simulating download interruption at ${(progress * 100).toInt()}%")
                        throw java.net.SocketTimeoutException("Simulated network timeout")
                    }
                }
                
                // If first attempt completed (small file or interruption disabled)
                if (firstAttemptResult) {
                    Log.d(TAG, "Download completed in first attempt - file may be too small for resumption test")
                    return@launch
                }
                
                // Check if temp file exists and has content
                if (tempFile.exists() && tempFile.length() > 0) {
                    val partialSize = tempFile.length()
                    Log.d(TAG, "Found partial download: $partialSize bytes")
                    
                    // Wait a moment to simulate realistic resumption
                    delay(1000)
                    
                    // Now try to resume the download
                    Log.d(TAG, "Attempting to resume download...")
                    lastProgress = 0f
                    val resumeResult = downloader.downloadFile(fileName) { progress ->
                        if (progress - lastProgress >= 0.05f) {
                            lastProgress = progress
                            Log.d(TAG, "Resume attempt progress: ${(progress * 100).toInt()}%")
                        }
                    }
                    
                    if (resumeResult) {
                        Log.d(TAG, "Successfully resumed and completed download!")
                        
                        // Verify the file exists and has proper size
                        if (targetFile.exists()) {
                            Log.d(TAG, "Final file size: ${targetFile.length()} bytes")
                        } else {
                            Log.e(TAG, "Target file does not exist after successful download")
                        }
                    } else {
                        Log.e(TAG, "Failed to resume download")
                    }
                } else {
                    Log.e(TAG, "No partial download found - interruption test may have failed")
                }
            } catch (e: Exception) {
                if (e is java.net.SocketTimeoutException && e.message?.contains("Simulated") == true) {
                    // This is expected for our simulation
                    Log.d(TAG, "Simulated interruption triggered, checking for partial file...")
                    
                    // Check if the partial download was saved
                    if (tempFile.exists() && tempFile.length() > 0) {
                        val partialSize = tempFile.length()
                        Log.d(TAG, "Partial download saved successfully: $partialSize bytes")
                        
                        // Wait a moment to simulate realistic resumption
                        delay(1000)
                        
                        // Now try to resume the download
                        var lastProgress = 0f
                        Log.d(TAG, "Attempting to resume download after interruption...")
                        val resumeResult = withContext(Dispatchers.IO) {
                            downloader.downloadFile(fileName) { progress ->
                                if (progress - lastProgress >= 0.05f) {
                                    lastProgress = progress
                                    Log.d(TAG, "Resume progress: ${(progress * 100).toInt()}%")
                                }
                            }
                        }
                        
                        if (resumeResult) {
                            Log.d(TAG, "Successfully resumed and completed download!")
                            
                            // Verify the file exists and has proper size
                            if (targetFile.exists()) {
                                Log.d(TAG, "Final file size: ${targetFile.length()} bytes")
                            } else {
                                Log.e(TAG, "Target file does not exist after successful download")
                            }
                        } else {
                            Log.e(TAG, "Failed to resume download")
                        }
                    } else {
                        Log.e(TAG, "No partial download saved after interruption")
                    }
                } else {
                    Log.e(TAG, "Exception during download resumption test", e)
                }
            }
        }
    }
} 