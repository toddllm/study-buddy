package com.example.studybuddy

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.studybuddy.ml.GemmaModelDownloaderTest
import com.example.studybuddy.ui.ModelDownloadScreen
import com.example.studybuddy.ui.ModelScreen
import com.example.studybuddy.ui.theme.StudyBuddyTheme
import androidx.activity.addCallback
import androidx.multidex.MultiDex
import java.io.File

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable multidex for large apps
        MultiDex.install(this)
        
        // Handle back button to exit app
        onBackPressedDispatcher.addCallback(this) {
            finishAffinity()
        }
        
        // Test loading the library directly
        testLoadLibrary()
        
        enableEdgeToEdge()
        
        setContent {
            StudyBuddyTheme {
                // State to track whether the download screen should be shown
                var showDownloadScreen by remember { mutableStateOf(true) }
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showDownloadScreen) {
                        // Show download screen first
                        ModelDownloadScreen(
                            onDownloadComplete = { showDownloadScreen = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    } else {
                        // Show main app screen after download
                        ModelScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
    
    private fun testLoadLibrary() {
        // Add library test code
        val tag = "LibraryTest"
        
        try {
            // Check the native library directory
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val libraryName = "libgemma-2-2b-it-q4f16_1.so"
            val libraryFile = File(nativeLibDir, libraryName)
            
            Log.d(tag, "Native lib directory: $nativeLibDir")
            Log.d(tag, "Library exists: ${libraryFile.exists()}")
            
            if (libraryFile.exists()) {
                // Try to load the library using System.load
                try {
                    System.load(libraryFile.absolutePath)
                    Log.d(tag, "Successfully loaded library: ${libraryFile.absolutePath}")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(tag, "Failed to load library: ${e.message}")
                }
            }
            
            // Try to load using loadLibrary (which uses the LD_LIBRARY_PATH)
            try {
                System.loadLibrary("gemma-2-2b-it-q4f16_1")
                Log.d(tag, "Successfully loaded library using loadLibrary")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(tag, "Failed to load library using loadLibrary: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error testing library: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up any resources
        try {
            // Add any cleanup code here
            Log.d(TAG, "MainActivity destroyed, cleaning up resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    StudyBuddyTheme {
        ModelScreen()
    }
}