package com.example.studybuddy

import android.app.Application
import androidx.multidex.MultiDexApplication
import android.util.Log
import java.io.File

/**
 * Custom Application class that enables MultiDex support for the application.
 * This is necessary when dealing with large ML models that can cause the app to exceed the 64K method limit.
 */
class StudyBuddyApplication : MultiDexApplication() {
    private val TAG = "StudyBuddyApplication"
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application initialized with MultiDex support")
        
        // Create JIT profile directory to fix "Failed to write jitted method info in log" errors
        createJitProfileDirectory()
        
        // Set up global error handling
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            // You could add crash reporting here
        }
    }
    
    /**
     * Creates the directory needed for ART JIT compiler to write profile information.
     * This fixes the "Failed to write jitted method info in log" error.
     */
    private fun createJitProfileDirectory() {
        try {
            // The base directory where Android stores JIT profiles
            val jitDir = File(filesDir.parentFile, "cache/jit")
            if (!jitDir.exists()) {
                val created = jitDir.mkdirs()
                Log.d(TAG, "Created JIT profile directory: $created")
            }
            
            // Create a dummy profile file to prevent the error
            val profileFile = File(jitDir, "jit_profile.txt")
            if (!profileFile.exists()) {
                profileFile.createNewFile()
                Log.d(TAG, "Created dummy JIT profile file")
            }
            
            // Ensure permissions are set correctly
            jitDir.setReadable(true, false)
            jitDir.setWritable(true, false)
            profileFile.setReadable(true, false)
            profileFile.setWritable(true, false)
            
            Log.d(TAG, "JIT profile directory setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating JIT profile directory", e)
        }
    }
} 