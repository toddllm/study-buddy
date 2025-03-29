package com.example.studybuddy.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility class for handling permissions
 */
object PermissionUtils {
    
    /**
     * Check if we have the required permissions for network operations
     */
    fun hasNetworkPermissions(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.INTERNET) &&
               hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)
    }
    
    /**
     * Check if we have the required permissions for camera
     */
    fun hasCameraPermissions(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.CAMERA)
    }
    
    /**
     * Check if we have the required permissions for storage
     */
    fun hasStoragePermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses more granular permissions
            hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ still uses READ_EXTERNAL_STORAGE but with scoped storage
            hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Pre-Android 10 needs both read and write
            hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
            hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    
    /**
     * Get required storage permissions based on API level
     */
    fun getStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
    
    /**
     * Get camera permissions
     */
    fun getCameraPermissions(): Array<String> {
        return arrayOf(Manifest.permission.CAMERA)
    }
    
    /**
     * Check if a permission is granted
     */
    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
} 