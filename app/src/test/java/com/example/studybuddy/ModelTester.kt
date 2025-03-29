package com.example.studybuddy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Utility class for testing TensorFlow Lite models directly, 
 * outside of the Android app environment.
 */
class ModelTester {
    
    // Constants for models
    private val IMAGE_SIZE = 224
    private val MAX_RESULTS = 5
    
    /**
     * Test MobileNet image classification model
     * @param modelPath Path to the mobilenet_v1.tflite model file
     * @param imagePath Path to the test image file
     * @return List of classification results with labels and confidence scores
     */
    fun testImageClassification(modelPath: String, imagePath: String): List<ClassificationResult> {
        // Load model
        val modelBuffer = loadModelFile(modelPath)
        val options = Interpreter.Options()
        val interpreter = Interpreter(modelBuffer, options)
        
        // Load and preprocess image
        val bitmap = BitmapFactory.decodeFile(imagePath)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        
        // Create input tensor
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        
        // Get input and output shapes
        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        
        // Create output buffer
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        
        // Run inference
        interpreter.run(processedImage.buffer, outputBuffer.buffer)
        
        // Process results - imagenet labels would be needed for real implementation
        val results = mutableListOf<ClassificationResult>()
        val confidences = outputBuffer.floatArray
        
        // For demo purposes, we'll just output the top confidences with index as label
        for (i in 0 until Math.min(MAX_RESULTS, confidences.size)) {
            var maxIdx = 0
            var maxVal = confidences[0]
            
            for (j in 1 until confidences.size) {
                if (confidences[j] > maxVal) {
                    maxVal = confidences[j]
                    maxIdx = j
                }
            }
            
            results.add(ClassificationResult("Class $maxIdx", maxVal))
            confidences[maxIdx] = -1f // Mark as processed
        }
        
        // Clean up
        interpreter.close()
        
        return results
    }
    
    /**
     * Test MobileBERT text classification model
     * @param modelPath Path to the text_classification.tflite model file
     * @param text Text to classify
     * @return List of category predictions
     */
    fun testTextClassification(modelPath: String, text: String): List<ClassificationResult> {
        // Load model
        val modelBuffer = loadModelFile(modelPath)
        val options = Interpreter.Options()
        val interpreter = Interpreter(modelBuffer, options)
        
        // Text preprocessing would go here in a real implementation
        // This is a simplified version
        
        // Define input and output shapes
        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        
        // Create input and output buffers
        val inputBuffer = ByteBuffer.allocateDirect(inputShape[0] * inputShape[1] * 4) // Float32 = 4 bytes
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        
        // For demo purposes, we'll just return dummy results
        // In a real implementation, we would process the text, convert to features, and run inference
        
        // Clean up
        interpreter.close()
        
        // Return dummy results
        return listOf(
            ClassificationResult("Business", 0.7f),
            ClassificationResult("Technology", 0.2f),
            ClassificationResult("Politics", 0.1f)
        )
    }
    
    /**
     * Load a TensorFlow Lite model file
     */
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val file = File(modelPath)
        val fileChannel = FileInputStream(file).channel
        val startOffset = 0L
        val declaredLength = file.length()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Data class to hold classification results
     */
    data class ClassificationResult(val label: String, val confidence: Float)
    
    companion object {
        /**
         * Main method for testing directly
         */
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 3) {
                println("Usage: ModelTester <model_type> <model_path> <test_input>")
                println("  model_type: 'image' or 'text'")
                println("  model_path: path to the .tflite model file")
                println("  test_input: path to image file or text to classify")
                return
            }
            
            val modelType = args[0]
            val modelPath = args[1]
            val testInput = args[2]
            
            val tester = ModelTester()
            
            when (modelType) {
                "image" -> {
                    println("Testing image classification...")
                    val results = tester.testImageClassification(modelPath, testInput)
                    println("Classification results:")
                    results.forEachIndexed { index, result ->
                        println("  ${index + 1}. ${result.label}: ${result.confidence * 100}%")
                    }
                }
                "text" -> {
                    println("Testing text classification...")
                    val results = tester.testTextClassification(modelPath, testInput)
                    println("Classification results:")
                    results.forEachIndexed { index, result ->
                        println("  ${index + 1}. ${result.label}: ${result.confidence * 100}%")
                    }
                }
                else -> {
                    println("Unknown model type: $modelType")
                    println("Supported types: 'image' or 'text'")
                }
            }
        }
    }
} 