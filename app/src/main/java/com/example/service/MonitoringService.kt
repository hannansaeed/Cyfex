package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.ThreatMonitorApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
        } catch (e: Throwable) {
            Log.e("MonitoringService", "Failed to create notification channel", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITORING) {
            serviceScope.launch {
                try {
                    val repo = (application as ThreatMonitorApp).repository
                    repo.setMonitoringSessionState(false)
                    repo.foregroundMonitorCollector.stopRealtimeHardwareListeners()
                } catch (e: Throwable) {
                    Log.e("MonitoringService", "Error stopping monitoring session", e)
                }
            }
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Throwable) {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        try {
            createNotificationChannel()
            val notification = buildMonitoringNotification("Cyfex: Active Foreground Telemetry & Sensor Audit")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (e: Throwable) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e("MonitoringService", "startForeground failed safely", e)
        }

        serviceScope.launch {
            try {
                val repo = (application as ThreatMonitorApp).repository
                repo.setMonitoringSessionState(true)
                repo.foregroundMonitorCollector.startRealtimeHardwareListeners()
            } catch (e: Throwable) {
                Log.e("MonitoringService", "Error initializing listeners", e)
            }
        }

        startPeriodicMonitoring()
        return START_STICKY
    }

    private fun startPeriodicMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    val repo = (application as ThreatMonitorApp).repository

                    // 1. Audit and log hardware/sensor accesses safely
                    repo.foregroundMonitorCollector.auditAndLogSensorAccesses()

                    // 2. High CPU anomaly detection
                    val procs = repo.processCollector.collectProcesses()
                    val criticalProc = procs.firstOrNull { it.cpuPercent > 80.0 }
                    if (criticalProc != null) {
                        notifyAnomaly(
                            "High CPU Anomaly Detected",
                            "${criticalProc.processName} consuming ${"%.1f".format(criticalProc.cpuPercent)}% CPU"
                        )
                    }
                } catch (e: Throwable) {
                    Log.w("MonitoringService", "Periodic monitoring check error", e)
                }
                delay(15_000L) // Check every 15 seconds
            }
        }
    }

    private fun buildMonitoringNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MonitoringService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cyfex Active Monitoring")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Monitoring", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyAnomaly(title: String, message: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(ANOMALY_NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            Log.e("MonitoringService", "Failed to notify anomaly", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cyfex Active Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status of the Shizuku security monitoring service"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.launch {
            try {
                val repo = (application as ThreatMonitorApp).repository
                repo.setMonitoringSessionState(false)
                repo.foregroundMonitorCollector.stopRealtimeHardwareListeners()
            } catch (e: Throwable) {
                // Ignore
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "threat_monitor_service_channel"
        const val NOTIFICATION_ID = 1010
        const val ANOMALY_NOTIFICATION_ID = 1011
        const val ACTION_STOP_MONITORING = "com.example.threatmonitor.ACTION_STOP"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, MonitoringService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e("MonitoringService", "startService failed", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, MonitoringService::class.java).apply {
                    action = ACTION_STOP_MONITORING
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.e("MonitoringService", "stopService failed", e)
            }
        }
    }
}
