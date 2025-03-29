package com.example.studybuddy.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for safer model loading and memory operations
 */
class ModelHelper {
    companion object {
        private const val TAG = "ModelHelper"
        
        /**
         * Log memory information to help diagnose out-of-memory issues
         */
        fun logMemoryInfo(tag: String = TAG, label: String = "Memory Usage") {
            val runtime = Runtime.getRuntime()
            val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val freeMemInMB = runtime.freeMemory() / (1024 * 1024)
            val maxMemInMB = runtime.maxMemory() / (1024 * 1024)
            val totalMemInMB = runtime.totalMemory() / (1024 * 1024)
            
            val nativeHeapAllocatedInMB = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            val nativeHeapSizeInMB = Debug.getNativeHeapSize() / (1024 * 1024)
            
            Log.d(tag, "$label:")
            Log.d(tag, "  Java Heap: $usedMemInMB MB used, $freeMemInMB MB free, $totalMemInMB MB total, $maxMemInMB MB max")
            Log.d(tag, "  Native Heap: $nativeHeapAllocatedInMB MB allocated, $nativeHeapSizeInMB MB size")
        }
        
        /**
         * Safely extract a TensorFlow Lite model file from assets to internal storage
         * Uses a temp directory first to avoid corrupted files if extraction fails
         */
        fun extractModelSafely(context: Context, assetName: String): File? {
            val startTime = SystemClock.elapsedRealtime()
            val destFile = File(context.filesDir, assetName)
            
            try {
                // Skip if the file already exists and has appropriate size
                if (destFile.exists() && destFile.length() > 1024) {
                    Log.d(TAG, "Model $assetName already exists (${destFile.length() / 1024} KB)")
                    return destFile
                }
                
                // Create a temporary file for extraction
                val tempFile = File(context.cacheDir, "temp_$assetName")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                
                // Make sure we have enough space
                val assetFileDescriptor = context.assets.openFd(assetName)
                val modelSizeBytes = assetFileDescriptor.length
                assetFileDescriptor.close()
                
                val internalStorage = StatFs(context.filesDir.path)
                val availableBytes = internalStorage.availableBytes
                
                if (availableBytes < modelSizeBytes * 2) { // 2x for safety
                    Log.e(TAG, "Not enough storage space: need ${modelSizeBytes / 1024} KB, " +
                         "available ${availableBytes / 1024} KB")
                    return null
                }
                
                // Copy from assets to temp file
                context.assets.open(assetName).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(4 * 1024)
                        var bytesRead: Int
                        var totalBytes = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            
                            // Periodically log progress for large files
                            if (totalBytes % (1024 * 1024) == 0L) { // Every 1MB
                                Log.d(TAG, "Extracted ${totalBytes / 1024} KB of $assetName")
                            }
                        }
                    }
                }
                
                // If temp file exists and has correct size, move it to destination
                if (tempFile.exists() && tempFile.length() > 0) {
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    
                    val success = tempFile.renameTo(destFile)
                    if (!success) {
                        // If rename fails, try copy and delete
                        tempFile.copyTo(destFile, overwrite = true)
                        tempFile.delete()
                    }
                    
                    val endTime = SystemClock.elapsedRealtime()
                    Log.d(TAG, "Successfully extracted $assetName (${destFile.length() / 1024} KB) " +
                         "in ${endTime - startTime}ms")
                    return destFile
                } else {
                    Log.e(TAG, "Temp file extraction failed for $assetName")
                    return null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting model $assetName", e)
                if (destFile.exists() && destFile.length() > 0) {
                    // If the file exists, just return it even if extraction failed
                    return destFile
                }
                return null
            }
        }
        
        /**
         * Save error logs to external storage for debugging
         */
        fun saveErrorLog(context: Context, errorMsg: String, exceptionStack: String) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val logDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                val logFile = File(logDir, "studybuddy_error_$timestamp.txt")
                
                FileOutputStream(logFile).use { output ->
                    output.write("ERROR LOG - $timestamp\n\n".toByteArray())
                    output.write("Message: $errorMsg\n\n".toByteArray())
                    output.write("Stack Trace:\n$exceptionStack\n\n".toByteArray())
                    output.write("Memory Info:\n".toByteArray())
                    
                    val runtime = Runtime.getRuntime()
                    val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                    val maxMemInMB = runtime.maxMemory() / (1024 * 1024)
                    output.write("  Used Memory: $usedMemInMB MB\n".toByteArray())
                    output.write("  Max Memory: $maxMemInMB MB\n".toByteArray())
                }
                
                Log.d(TAG, "Error log saved to ${logFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save error log", e)
            }
        }
        
        /**
         * Pre-process a bitmap to make it more suitable for classification
         * - Resize to appropriate dimensions
         * - Center crop if necessary
         * - Convert to RGB if needed
         */
        fun processBitmapForClassification(bitmap: Bitmap, targetSize: Int): Bitmap {
            try {
                // Handle transparency by rendering onto white background
                val hasAlpha = bitmap.hasAlpha()
                
                val srcWidth = bitmap.width
                val srcHeight = bitmap.height
                
                // Calculate scaling
                val scaleFactor = targetSize.toFloat() / Math.min(srcWidth, srcHeight)
                val scaledWidth = (srcWidth * scaleFactor).toInt()
                val scaledHeight = (srcHeight * scaleFactor).toInt()
                
                // Create an intermediate scaled bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                
                // Create the target bitmap (square)
                val resultBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                
                // Fill with white background if source has alpha
                val canvas = Canvas(resultBitmap)
                if (hasAlpha) {
                    canvas.drawColor(Color.WHITE)
                }
                
                // Calculate centering
                val left = (targetSize - scaledWidth) / 2
                val top = (targetSize - scaledHeight) / 2
                
                // Draw the scaled bitmap centered
                canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
                
                // Clean up the intermediate bitmap
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                
                return resultBitmap
            } catch (OutOfMemoryError: Error) {
                Log.e(TAG, "Out of memory while processing bitmap", OutOfMemoryError)
                
                // Return a simple small bitmap as fallback
                try {
                    val fallbackBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.RGB_565)
                    val canvas = Canvas(fallbackBitmap)
                    canvas.drawColor(Color.GRAY) // Gray indicates error
                    return fallbackBitmap
                } catch (e: Exception) {
                    // If even the fallback fails, return the original
                    return bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing bitmap", e)
                return bitmap
            }
        }
        
        /**
         * Force garbage collection and trim memory
         */
        fun forceGarbageCollection() {
            logMemoryInfo(label = "Before GC")
            System.gc()
            System.runFinalization()
            logMemoryInfo(label = "After GC")
        }
    }
} 