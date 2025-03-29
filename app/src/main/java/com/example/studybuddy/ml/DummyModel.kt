package com.example.studybuddy.ml

import android.graphics.Bitmap
import android.util.Log

/**
 * A simple dummy model that simulates responses without actually loading the ML model.
 * This is useful for testing the UI and flow without the large model file.
 */
class DummyModel {
    private val TAG = "DummyModel"

    /**
     * Simulates a model response for text input
     */
    fun generateTextResponse(input: String): String {
        Log.d(TAG, "Generating text response for: $input")
        
        // Wait a bit to simulate processing time
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Sleep interrupted", e)
        }
        
        // Generate a relevant response based on the input
        return when {
            input.contains("hello", ignoreCase = true) -> "Hello! How can I help with your studies today?"
            input.contains("math", ignoreCase = true) -> "Mathematics is a broad field. Are you looking for help with algebra, calculus, geometry, or something else?"
            input.contains("history", ignoreCase = true) -> "I can help with historical topics. What period or event are you interested in learning about?"
            input.contains("science", ignoreCase = true) -> "Science encompasses physics, chemistry, biology, and more. What specific area would you like to explore?"
            input.contains("explain", ignoreCase = true) -> "I'll do my best to explain that concept clearly. Let me break it down step by step..."
            input.length < 10 -> "Could you provide more details about what you'd like to learn?"
            else -> "I understand you're asking about '${input.take(30)}${if (input.length > 30) "..." else ""}'. Let me help you understand this topic better. " +
                   "This would be where the AI model would generate a detailed, educational response about your question."
        }
    }
    
    /**
     * Simulates a model response for image + text input
     */
    fun generateImageResponse(image: Bitmap, prompt: String): String {
        Log.d(TAG, "Generating image+text response for prompt: $prompt")
        
        // Wait a bit to simulate processing time
        try {
            Thread.sleep(1500)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Sleep interrupted", e)
        }
        
        // Generate a relevant response based on the input
        return when {
            prompt.contains("what", ignoreCase = true) -> 
                "Looking at the image you've shared, I can see what appears to be a ${getRandomObject()}. " +
                "If you're asking what this is, it's commonly used for ${getRandomPurpose()}. " +
                "Would you like me to explain more about how it works or its significance?"
                
            prompt.contains("explain", ignoreCase = true) -> 
                "The image you've shared shows a ${getRandomObject()}. Let me explain its key features:\n\n" +
                "1. It has a distinctive ${getRandomAttribute()} that makes it recognizable\n" +
                "2. It's commonly found in ${getRandomLocation()}\n" +
                "3. It's important because ${getRandomImportance()}\n\n" +
                "Is there a specific aspect about it you'd like me to elaborate on?"
                
            prompt.contains("history", ignoreCase = true) -> 
                "The item in your image has an interesting history. " +
                "It was first developed in ${getRandomYear()} and has evolved significantly since then. " +
                "Originally, it was used for ${getRandomPurpose()}, but today it serves multiple purposes " +
                "including ${getRandomModernUse()}."
                
            else -> 
                "I've analyzed the image you shared along with your prompt: \"$prompt\"\n\n" +
                "In the image, I can see what appears to be a ${getRandomObject()}. " +
                "This is typically found in ${getRandomLocation()} and serves the purpose of ${getRandomPurpose()}.\n\n" +
                "Is there something specific about this you'd like to learn more about?"
        }
    }
    
    // Helper methods to generate random components for responses
    
    private fun getRandomObject(): String {
        val objects = listOf(
            "textbook", "scientific diagram", "mathematical equation", "historical document", 
            "periodic table", "molecule model", "geometric shape", "graph", "map", 
            "architecture drawing", "educational poster", "laboratory apparatus"
        )
        return objects.random()
    }
    
    private fun getRandomPurpose(): String {
        val purposes = listOf(
            "educational demonstrations", "explaining complex concepts", "historical reference",
            "scientific research", "mathematical problem-solving", "visual learning aids",
            "conceptual understanding", "practical experimentation", "theoretical illustration"
        )
        return purposes.random()
    }
    
    private fun getRandomAttribute(): String {
        val attributes = listOf(
            "shape", "color pattern", "structural design", "labeled components", 
            "numerical sequence", "graphical representation", "textual annotation"
        )
        return attributes.random()
    }
    
    private fun getRandomLocation(): String {
        val locations = listOf(
            "academic textbooks", "research papers", "educational institutions", 
            "scientific journals", "historical archives", "learning centers",
            "laboratory manuals", "STEM classrooms", "reference materials"
        )
        return locations.random()
    }
    
    private fun getRandomImportance(): String {
        val importances = listOf(
            "it demonstrates fundamental principles in this field of study",
            "it helps visualize abstract concepts that are difficult to explain with words alone",
            "it serves as a standardized reference that experts rely on",
            "it connects theoretical knowledge with practical applications",
            "it represents a breakthrough in how we understand this subject"
        )
        return importances.random()
    }
    
    private fun getRandomYear(): String {
        return (1800..1990).random().toString()
    }
    
    private fun getRandomModernUse(): String {
        val modernUses = listOf(
            "digital education", "online learning platforms", "virtual simulations",
            "cross-disciplinary research", "standardized testing", "interactive learning modules",
            "educational software applications", "distance learning resources"
        )
        return modernUses.random()
    }
} 