package com.example.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus(val title: String, val description: String) {
    NOT_INSTALLED("Shizuku Not Installed", "Install Shizuku Manager from Google Play or GitHub to enable privileged mobile telemetry."),
    NOT_RUNNING("Shizuku Service Inactive", "Shizuku is installed but its ADB service is not running. Start it via Wireless Debugging or PC ADB."),
    PERMISSION_REQUIRED("Permission Needed", "Shizuku service is running. Tap to grant privileged shell permission to Cyfex."),
    READY("Privileged ADB Active", "Shizuku bridge is connected with authorized ADB shell access.")
}

class ShizukuManager(private val context: Context) {

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received")
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died")
        refreshStatus()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            Log.i(TAG, "Shizuku permission result: $grantResult")
            refreshStatus()
        }
    }

    init {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Throwable) {
            Log.e(TAG, "Error registering Shizuku listeners: ${e.message}")
        }
        refreshStatus()
    }

    fun isInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (t: Throwable) {
            false
        }
    }

    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!isRunning()) return false
            val shizukuPerm = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
            val providerPerm = try {
                context.checkSelfPermission(ShizukuProvider_PERMISSION) == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
            shizukuPerm || providerPerm
        } catch (t: Throwable) {
            false
        }
    }

    fun refreshStatus() {
        _status.value = when {
            !isInstalled() -> ShizukuStatus.NOT_INSTALLED
            !isRunning() -> ShizukuStatus.NOT_RUNNING
            !hasPermission() -> ShizukuStatus.PERMISSION_REQUIRED
            else -> ShizukuStatus.READY
        }
    }

    fun requestPermission() {
        try {
            if (isRunning() && !hasPermission()) {
                if (!Shizuku.isPreV11()) {
                    Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", t)
        }
    }

    fun openShizukuApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                openShizukuDownload()
            }
        } catch (e: Exception) {
            openShizukuDownload()
        }
    }

    fun openShizukuDownload() {
        try {
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun isWirelessDebuggingAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    companion object {
        const val TAG = "ShizukuManager"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE_PERMISSION = 7102
        const val ShizukuProvider_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    }
}
