package com.example.studybuddy

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.studybuddy.ui.ModelDownloadScreen
import com.example.studybuddy.ui.ModelScreen
import com.example.studybuddy.ui.theme.StudyBuddyTheme
import androidx.activity.addCallback
import androidx.multidex.MultiDex

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