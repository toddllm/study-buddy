package com.example.studybuddy.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.studybuddy.ml.MlcLanguageModel
import com.example.studybuddy.ml.ModelDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "ModelDownloadScreen"

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
    
    // Create model instances
    val mlcModel = remember { MlcLanguageModel(context) }
    val downloadService = remember { ModelDownloadService(context) }
    
    // Collect download progress
    val downloadProgress by downloadService.downloadProgress.collectAsState()
    
    // Check if model is already downloaded
    LaunchedEffect(key1 = Unit) {
        withContext(Dispatchers.IO) {
            try {
                val filesAvailable = mlcModel.areModelFilesAvailable()
                if (filesAvailable) {
                    downloadStatus = "Model files already available"
                    modelInfo = mlcModel.getModelInfo()
                } else {
                    downloadStatus = "Model files not found"
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
            text = "Gemma 2 Model Download",
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Required if downloading gated models") }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Progress indicator
                            if (downloadProgress > 0) {
                                // Show deterministic progress
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    LinearProgressIndicator(
                                        progress = downloadProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = "${(downloadProgress * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                // Show indeterminate progress initially
                                CircularProgressIndicator()
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = if (downloadProgress > 0) 
                                    "Downloading: ${(downloadProgress * 100).roundToInt()}% complete" 
                                else 
                                    "Preparing download...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                isDownloading = true
                                downloadStatus = "Starting download..."
                                
                                coroutineScope.launch {
                                    // Set Hugging Face token if provided
                                    val token = hfToken.takeIf { it.isNotEmpty() }
                                    token?.let { downloadService.setHuggingFaceToken(it) }
                                    
                                    try {
                                        // Download model files
                                        val modelDir = withContext(Dispatchers.IO) {
                                            downloadService.downloadModelFiles()
                                        }
                                        
                                        if (modelDir != null) {
                                            // Try to initialize the model with the downloaded files
                                            val initialized = withContext(Dispatchers.IO) {
                                                mlcModel.initialize(token)
                                            }
                                            
                                            if (initialized) {
                                                downloadStatus = "Model downloaded and initialized successfully!"
                                                modelInfo = mlcModel.getModelInfo()
                                                Toast.makeText(
                                                    context, 
                                                    "Model downloaded and ready to use!", 
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                
                                                // Navigate back
                                                onDownloadComplete()
                                            } else {
                                                downloadStatus = "Model downloaded but failed to initialize"
                                            }
                                        } else {
                                            downloadStatus = "Failed to download model files"
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error downloading model", e)
                                        downloadStatus = "Error: ${e.message}"
                                    } finally {
                                        isDownloading = false
                                    }
                                }
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Create,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Download Model")
                        }
                    }
                }
                
                if (downloadStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = downloadStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (downloadStatus.startsWith("Error")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                
                if (modelInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Model Info"
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = { onDownloadComplete() },
            enabled = !isDownloading
        ) {
            Text("Skip Download")
        }
    }
} 