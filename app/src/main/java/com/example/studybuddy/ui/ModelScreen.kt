package com.example.studybuddy.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.studybuddy.ml.DummyModel
import com.example.studybuddy.ml.TensorFlowModelManager
import com.example.studybuddy.ui.components.MLCLLMChatComponent
import com.example.studybuddy.ui.theme.StudyBuddyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.studybuddy.ml.LLMFactory
import com.example.studybuddy.ml.MlcLanguageModel

private const val TAG = "ModelScreen"

/**
 * Main screen of the app showing the model functionalities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StudyBuddy AI") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        // Tabs to switch between different functionalities
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        val tabs = listOf("MLC-LLM Chat", "Mock Chat", "Study Notes", "Settings")
        
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Content based on selected tab
            when (selectedTabIndex) {
                0 -> {
                    // MLC-LLM Chat Tab
                    val context = LocalContext.current
                    val llmFactory = remember { LLMFactory }
                    val mlcLanguageModel = remember(context) { 
                        llmFactory.createLLM(context) as? MlcLanguageModel
                    }
                    
                    if (mlcLanguageModel != null) {
                        MLCLLMChatComponent(
                            model = mlcLanguageModel,
                            onBackClick = { /* Nothing to do */ }
                        )
                    } else {
                        Text(
                            text = "Failed to create MLC-LLM model",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                1 -> {
                    // Mock Chat Tab (placeholder for now)
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Mock Chat Feature Coming Soon",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                2 -> {
                    // Study Notes Tab (placeholder for now)
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Study Notes Feature Coming Soon",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                3 -> {
                    // Settings Tab (placeholder for now)
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Settings Feature Coming Soon",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ModelScreenPreview() {
    StudyBuddyTheme {
        ModelScreen()
    }
} 