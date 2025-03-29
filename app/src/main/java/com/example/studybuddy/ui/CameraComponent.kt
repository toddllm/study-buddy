package com.example.studybuddy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A component that provides a camera preview and photo capture functionality.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraComponent(
    onImageCaptured: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var hasCameraPermission by remember { mutableStateOf(cameraPermissionState.status.isGranted) }
    
    // Request permission if not granted
    LaunchedEffect(key1 = Unit) {
        if (!hasCameraPermission) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Update permission state when it changes
    LaunchedEffect(key1 = cameraPermissionState.status) {
        hasCameraPermission = cameraPermissionState.status.isGranted
    }
    
    if (hasCameraPermission) {
        Box(modifier = modifier.fillMaxSize()) {
            val imageCapture = remember { ImageCapture.Builder().build() }
            val mainExecutor = ContextCompat.getMainExecutor(context)
            
            // Camera preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                    
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraComponent", "Camera binding failed", e)
                        }
                    }, mainExecutor)
                    
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Capture button
            Button(
                onClick = {
                    takePhoto(
                        imageCapture = imageCapture,
                        executor = mainExecutor,
                        context = context,
                        onImageCaptured = onImageCaptured
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Text("Capture")
            }
            
            // Clean up
            DisposableEffect(lifecycleOwner) {
                onDispose {
                    try {
                        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                        cameraProvider.unbindAll()
                    } catch (e: Exception) {
                        Log.e("CameraComponent", "Camera unbinding failed", e)
                    }
                }
            }
        }
    } else {
        // If no permission, show message
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission is required")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Request Permission")
            }
        }
    }
}

private fun takePhoto(
    imageCapture: ImageCapture,
    executor: Executor,
    context: Context,
    onImageCaptured: (Bitmap) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // Convert ImageProxy to Bitmap
                val bitmap = image.toBitmap()
                
                // Rotate image if needed
                val rotationDegrees = image.imageInfo.rotationDegrees
                val rotatedBitmap = if (rotationDegrees != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())
                    Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height,
                        matrix, true
                    )
                } else {
                    bitmap
                }
                
                onImageCaptured(rotatedBitmap)
                image.close()
            }
            
            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraComponent", "Photo capture failed", exception)
            }
        }
    )
}

// Extension function to convert ImageProxy to Bitmap
private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val listenableFuture = ProcessCameraProvider.getInstance(this)
    listenableFuture.addListener({
        continuation.resume(listenableFuture.get())
    }, ContextCompat.getMainExecutor(this))
} 