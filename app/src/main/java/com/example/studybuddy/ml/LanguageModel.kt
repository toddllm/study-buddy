package com.example.studybuddy.ml

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.Delegates

/**
 * Base class for all language models.
 */
abstract class LanguageModel(protected val context: Context) {
    
    // State variables for UI
    protected val _initialized = mutableStateOf(false)
    val initialized: State<Boolean> = _initialized
    
    protected val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error
    
    // Model configuration
    var temperature by Delegates.observable(0.7f) { _, _, newValue ->
        onTemperatureChanged(newValue)
    }
    
    var topP by Delegates.observable(0.95f) { _, _, newValue ->
        onTopPChanged(newValue)
    }
    
    /**
     * Initialize the model.
     */
    abstract suspend fun initialize()
    
    /**
     * Generate text for a given prompt. Blocks until completion.
     */
    abstract suspend fun generateText(prompt: String): String
    
    /**
     * Stream text for a given prompt. Non-blocking, returns tokens via callback.
     */
    abstract fun streamText(prompt: String, onToken: (String) -> Unit, onError: (String) -> Unit = {})
    
    /**
     * Shut down the model and release resources.
     */
    abstract suspend fun shutdown()
    
    /**
     * Handle temperature changes.
     */
    protected open fun onTemperatureChanged(newValue: Float) {
        // Default implementation does nothing
    }
    
    /**
     * Handle top-p changes.
     */
    protected open fun onTopPChanged(newValue: Float) {
        // Default implementation does nothing
    }
} 