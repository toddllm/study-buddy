package com.example.studybuddy.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.BuildCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import android.os.Environment

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionHandler(
    onPermissionGranted: @Composable () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    if (cameraPermissionState.status.isGranted) {
        onPermissionGranted()
    } else {
        PermissionDeniedContent(
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
            isPermissionPermanentlyDenied = !cameraPermissionState.status.shouldShowRationale,
            permissionText = "Camera permission is needed to capture images for analysis."
        )
    }
}

@Composable
fun StoragePermissionHandler(
    onPermissionGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    val needsManageExternalStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val (hasStoragePermission, setHasStoragePermission) = remember { mutableStateOf(
        if (needsManageExternalStorage) Environment.isExternalStorageManager() else true
    )}
    
    if (needsManageExternalStorage) {
        // Android 11+ requires MANAGE_EXTERNAL_STORAGE for direct file access
        if (hasStoragePermission) {
            onPermissionGranted()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "This app needs permission to manage files to access the model file in the Downloads folder.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        // Launch intent to go to the "Allow management of all files" settings screen
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Grant File Access Permission")
                }
            }
        }
    } else {
        // For devices before Android 11, use the regular permission system
        RegularStoragePermissionHandler(onPermissionGranted)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RegularStoragePermissionHandler(
    onPermissionGranted: @Composable () -> Unit
) {
    // For Android 10 and below, use regular storage permissions
    val readPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    val writePermissionState = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    
    if (readPermissionState.status.isGranted && 
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || writePermissionState.status.isGranted)) {
        onPermissionGranted()
    } else {
        PermissionDeniedContent(
            onRequestPermission = { 
                readPermissionState.launchPermissionRequest()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    writePermissionState.launchPermissionRequest()
                }
            },
            isPermissionPermanentlyDenied = 
                !readPermissionState.status.shouldShowRationale || 
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !writePermissionState.status.shouldShowRationale),
            permissionText = "Storage access permission is needed to load the model file from your Downloads folder."
        )
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
    isPermissionPermanentlyDenied: Boolean = false,
    permissionText: String = "Permission is needed for this feature."
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isPermissionPermanentlyDenied) {
                "Permission permanently denied. You need to enable it in app settings."
            } else {
                permissionText
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (isPermissionPermanentlyDenied) {
                    // Open app settings
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                } else {
                    onRequestPermission()
                }
            }
        ) {
            Text(
                text = if (isPermissionPermanentlyDenied) {
                    "Open Settings"
                } else {
                    "Grant Permission"
                }
            )
        }
    }
} 