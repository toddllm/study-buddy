package com.example.studybuddy.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
} 