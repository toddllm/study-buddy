package com.example.studybuddy

import android.app.Application
import androidx.multidex.MultiDexApplication
import android.util.Log

/**
 * Custom Application class that enables MultiDex support for the application.
 * This is necessary when dealing with large ML models that can cause the app to exceed the 64K method limit.
 */
class StudyBuddyApplication : MultiDexApplication() {
    private val TAG = "StudyBuddyApplication"
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application initialized with MultiDex support")
        
        // Set up global error handling
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            // You could add crash reporting here
        }
    }
} 