package com.example.studybuddy.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for performing OCR text recognition on images using ML Kit.
 */
class TextRecognitionService {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val TAG = "TextRecognitionService"
    
    /**
     * Asynchronously processes an image and extracts text using OCR.
     * 
     * @param bitmap The image to extract text from
     * @return A string containing all recognized text from the image
     */
    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { text ->
                Log.d(TAG, "OCR process completed successfully")
                val extractedText = processRecognizedText(text)
                continuation.resume(extractedText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR process failed: ${e.message}", e)
                continuation.resumeWithException(e)
            }
            .addOnCanceledListener {
                Log.w(TAG, "OCR process was canceled")
                continuation.cancel()
            }
    }
    
    /**
     * Process the recognized text to extract a coherent string.
     */
    private fun processRecognizedText(text: Text): String {
        val stringBuilder = StringBuilder()
        
        for (textBlock in text.textBlocks) {
            for (line in textBlock.lines) {
                stringBuilder.append(line.text).append("\n")
            }
            stringBuilder.append("\n")
        }
        
        val result = stringBuilder.toString().trim()
        Log.d(TAG, "Extracted text: $result")
        return result
    }
} 