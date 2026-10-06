package com.web2app.handlers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/**
 * Handles runtime permissions for location, file/camera, and audio/video.
 * Mirrors PermissionsHandler from the Compose reference app.
 * Works with any Activity that is a ComponentActivity (including AppCompatActivity).
 */
class PermissionsHandler(private val activity: Activity) {

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var mediaPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    private var locationPermissionCallback: ((Boolean) -> Unit)? = null
    private var fileCameraCallback: (() -> Unit)? = null
    private var audioVideoCallback: (() -> Unit)? = null

    init {
        require(activity is ComponentActivity) {
            "Activity must be a ComponentActivity to use ActivityResult API"
        }
        setupPermissionLaunchers()
    }

    private fun setupPermissionLaunchers() {
        val componentActivity = activity as ComponentActivity

        // Generic multiple-permissions launcher (file/camera)
        permissionLauncher = componentActivity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            fileCameraCallback?.invoke()
            fileCameraCallback = null
        }

        // Location permissions launcher
        locationPermissionLauncher = componentActivity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            val fineGranted = perms.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
            val coarseGranted = perms.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
            val allLocation = fineGranted || coarseGranted
            locationPermissionCallback?.invoke(allLocation)
            locationPermissionCallback = null
        }

        // Media permissions launcher (audio/video)
        mediaPermissionLauncher = componentActivity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            audioVideoCallback?.invoke()
            audioVideoCallback = null
        }

        // Notification permission launcher (Android 13+)
        notificationPermissionLauncher = componentActivity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* granted state handled by caller if needed */ }
    }

    fun requestFileAndCameraPermissions(onResult: (() -> Unit)? = null) {
        fileCameraCallback = onResult
        // File uploads go through the system file/photo picker (onShowFileChooser),
        // which needs no READ_MEDIA/storage permission. Only CAMERA is requested here,
        // for in-page camera capture. On API <=32 legacy storage is still requested.
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissions += Manifest.permission.CAMERA
        if (checkPermissions(permissions.toTypedArray())) {
            onResult?.invoke()
            return
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    fun requestLocationPermissions(callback: (Boolean) -> Unit) {
        locationPermissionCallback = callback
        val foregroundPerms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (!checkPermissions(foregroundPerms)) {
            locationPermissionLauncher.launch(foregroundPerms)
            return
        }
        callback(true)
    }

    fun requestAudioVideoPermissions(onResult: (() -> Unit)? = null) {
        audioVideoCallback = onResult
        // Live capture (getUserMedia) needs mic + camera; selecting existing media goes
        // through the system picker, so no READ_MEDIA permission is requested.
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkPermissions(permissions.toTypedArray())) {
            onResult?.invoke()
            return
        }
        mediaPermissionLauncher.launch(permissions.toTypedArray())
    }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            activity.startActivity(this)
        }
    }

    private fun checkPermissions(permissions: Array<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
}
