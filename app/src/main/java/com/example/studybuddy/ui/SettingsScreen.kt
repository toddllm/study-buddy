package com.example.studybuddy.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.studybuddy.ml.GemmaModelDownloaderTest
import com.example.studybuddy.ui.theme.StudyBuddyTheme

/**
 * Settings screen with various app settings and testing options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        val context = LocalContext.current
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model Testing Section
            Text(
                text = "Model Testing",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Button(
                onClick = {
                    // Run the checksum verification test
                    Toast.makeText(context, "Testing checksum verification...", Toast.LENGTH_SHORT).show()
                    GemmaModelDownloaderTest.testChecksumVerification(context)
                }
            ) {
                Text("Test Checksum Verification")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    // Test downloading a single file
                    Toast.makeText(context, "Testing single file download...", Toast.LENGTH_SHORT).show()
                    GemmaModelDownloaderTest.testDownloadSingleFile(context)
                }
            ) {
                Text("Test Single File Download")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    // Test corrupting and re-downloading a file
                    Toast.makeText(context, "Testing model download with max 3 files...", Toast.LENGTH_SHORT).show()
                    GemmaModelDownloaderTest.testDownloadModel(context, essentialFilesOnly = true, maxFilesToDownload = 3)
                }
            ) {
                Text("Test Limited Model Download")
            }
            
            Text(
                text = "Check logcat with tag 'GemmaModelDownloaderTest' for results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    StudyBuddyTheme {
        SettingsScreen(onBackClick = {})
    }
} 