package com.example.studybuddy.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.studybuddy.ml.MlcLanguageModel
import com.example.studybuddy.ml.ModelDownloadService
import com.example.studybuddy.ui.theme.StudyBuddyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ModelDownloadScreen"

/**
 * Model download screen for LLM models
 */
@Composable
fun ModelDownloadScreen(
    onDownloadComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var hfToken by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var showInfoDialog by remember { mutableStateOf(false) }
    var modelInfo by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Create model instances
    val mlcModel = remember { MlcLanguageModel(context) }
    val downloadService = remember { ModelDownloadService(context) }
    
    // Collect download progress
    val downloadProgress by downloadService.downloadProgress.collectAsState()
    
    // Check if model is already downloaded
    LaunchedEffect(key1 = Unit) {
        withContext(Dispatchers.IO) {
            try {
                // First check if model is already downloaded using the new downloader
                val gemmaDownloader = com.example.studybuddy.ml.GemmaModelDownloader(context)
                val modelDownloaded = gemmaDownloader.isModelDownloaded()
                
                // Then check if model files are available through the traditional path
                val filesAvailable = modelDownloaded || mlcModel.areModelFilesAvailable()
                
                if (filesAvailable) {
                    downloadStatus = "Model files already available"
                    
                    // Try to initialize model if files are available
                    try {
                        // Call initialize and get the Boolean result
                        val modelInitialized = mlcModel.initialize(null)
                        
                        if (modelInitialized) {
                            // Model is ready to use, move to main screen
                            withContext(Dispatchers.Main) {
                                onDownloadComplete()
                            }
                        } else {
                            downloadStatus = "Model files found but failed to initialize"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing model", e)
                        downloadStatus = "Error initializing model: ${e.message}"
                    }
                } else {
                    downloadStatus = "Model files not found. Please download the model."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking model files", e)
                downloadStatus = "Error checking model files: ${e.message}"
            }
        }
    }
    
    // Show model info dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Model Information") },
            text = { 
                Text(
                    text = modelInfo,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Show error dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { 
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Show progress dialog during download
    if (isDownloading) {
        Dialog(onDismissRequest = { /* Non-dismissible */ }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Downloading Model",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Progress: ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = downloadStatus,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    
                    if (downloadProgress == 1f) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(onClick = { isDownloading = false }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
    
    // Main UI
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Gemma 2 Model Setup",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "MLC-LLM Model Setup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "This will download the Gemma 2 (2B parameters) model from Hugging Face. " +
                    "The model is about 1.5GB and will be stored on your device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = hfToken,
                    onValueChange = { hfToken = it },
                    label = { Text("Hugging Face Token (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Required for downloading gated models") }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = downloadStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (downloadStatus.contains("Error") || downloadStatus.contains("failed")) 
                                    FontStyle.Italic else FontStyle.Normal,
                    color = if (downloadStatus.contains("Error") || downloadStatus.contains("failed")) 
                               MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { 
                            if (downloadStatus.startsWith("Model files already available")) {
                                // Skip download and proceed
                                onDownloadComplete()
                            } else {
                                coroutineScope.launch {
                                    isDownloading = true
                                    downloadStatus = "Preparing to download model..."
                                    
                                    // Set token if provided
                                    if (hfToken.isNotEmpty()) {
                                        downloadService.setHuggingFaceToken(hfToken)
                                    }
                                    
                                    try {
                                        // Download model files
                                        val modelDir = withContext(Dispatchers.IO) {
                                            // Try to use the new Gemma downloader first
                                            val gemmaDownloader = com.example.studybuddy.ml.GemmaModelDownloader(context)
                                            
                                            // Track download progress
                                            var success: Boolean
                                            success = gemmaDownloader.downloadModel(
                                                progressCallback = { progress ->
                                                    downloadService.updateDownloadProgress(progress)
                                                },
                                                fileProgressCallback = { fileName, progress ->
                                                    val statusMessage = "Downloading $fileName: ${(progress * 100).toInt()}%"
                                                    if (progress % 0.05f < 0.01f) { // Update status ~every 5%
                                                        downloadStatus = statusMessage
                                                    }
                                                }
                                            )
                                            
                                            // Return the model directory if download was successful
                                            if (success) gemmaDownloader.getModelDirectory() else null
                                        } ?: withContext(Dispatchers.IO) {
                                            // Fall back to the original download method if the new one fails
                                            downloadStatus = "Falling back to legacy download method..."
                                            downloadService.downloadModelFiles()
                                        }
                                        
                                        if (modelDir != null) {
                                            downloadStatus = "Download complete. Initializing model..."
                                            
                                            // Try to initialize the model with the downloaded files
                                            val initialized = withContext(Dispatchers.IO) {
                                                mlcModel.initialize(hfToken)
                                            }
                                            
                                            if (initialized) {
                                                downloadStatus = "Model downloaded and initialized successfully!"
                                                modelInfo = mlcModel.getModelInfo()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context, 
                                                        "Model downloaded and ready to use!", 
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    
                                                    // Navigate to main screen
                                                    onDownloadComplete()
                                                }
                                            } else {
                                                downloadStatus = "Model downloaded but failed to initialize"
                                                errorMessage = "The model was downloaded but couldn't be initialized properly."
                                                showErrorDialog = true
                                            }
                                        } else {
                                            downloadStatus = "Failed to download model files"
                                            errorMessage = "Failed to download model files. Check your internet connection and Hugging Face token (if required)."
                                            showErrorDialog = true
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error during model download/init", e)
                                        downloadStatus = "Error: ${e.message}"
                                        errorMessage = "An error occurred: ${e.message}"
                                        showErrorDialog = true
                                    } finally {
                                        isDownloading = false
                                    }
                                }
                            }
                        },
                        enabled = !isDownloading
                    ) {
                        if (downloadStatus.startsWith("Model files already available")) {
                            Text("Continue")
                        } else {
                            Text("Download Model")
                        }
                    }
                    
                    if (modelInfo.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Model Info")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "About Gemma 2",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Gemma 2 is Google's lightweight, state-of-the-art open LLM. " +
                    "The 2B-parameter model is optimized for mobile devices and " +
                    "provides high-quality results for educational purposes.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "The model can answer questions, help with homework, generate creative content, " +
                    "and engage in helpful conversations without requiring an internet connection.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ModelDownloadScreenPreview() {
    StudyBuddyTheme {
        ModelDownloadScreen(
            onDownloadComplete = {}
        )
    }
} 