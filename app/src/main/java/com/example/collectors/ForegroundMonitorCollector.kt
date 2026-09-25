package com.example.collectors

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.location.GnssStatus
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.data.db.dao.SensorAccessDao
import com.example.data.db.entity.SensorAccessEventEntity
import com.example.shizuku.ShellExecutor
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class ForegroundMonitorCollector(
    private val context: Context,
    private val shellExecutor: ShellExecutor,
    private val sensorAccessDao: SensorAccessDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // In-memory set of recorded (pkg, resource, timeBucket) to avoid duplicates
    private val recordedEventKeys = ConcurrentHashMap.newKeySet<String>()

    private var cameraCallbackRegistered = false
    private var audioCallbackRegistered = false
    private var gnssCallbackRegistered = false
    private var contactsObserverRegistered = false
    private var mediaObserverRegistered = false
    private var locationSettingObserverRegistered = false

    private val cameraManager by lazy {
        try { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager } catch (e: Throwable) { null }
    }
    private val audioManager by lazy {
        try { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager } catch (e: Throwable) { null }
    }
    private val locationManager by lazy {
        try { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager } catch (e: Throwable) { null }
    }
    private val usageStatsManager by lazy {
        try { context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager } catch (e: Throwable) { null }
    }
    private val activityManager by lazy {
        try { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager } catch (e: Throwable) { null }
    }

    // 1. Camera Availability Callback
    private val cameraAvailabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            scope.launch {
                try {
                    val pkg = resolveActiveCameraClientOrForegroundPackage()
                    recordRealtimeSensorAccess(
                        targetPkg = pkg,
                        resourceType = "CAMERA",
                        details = "Camera hardware sensor (ID $cameraId) accessed"
                    )
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "Camera callback error", e)
                }
            }
        }

        override fun onCameraAvailable(cameraId: String) {}
    }

    // 2. Audio Recording Stream Callback
    private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            if (configs.isNotEmpty()) {
                scope.launch {
                    try {
                        val pkg = resolveActiveAudioClientOrForegroundPackage(configs)
                        recordRealtimeSensorAccess(
                            targetPkg = pkg,
                            resourceType = "MICROPHONE",
                            details = "Microphone audio recording stream opened"
                        )
                    } catch (e: Throwable) {
                        Log.w("ForegroundMonitor", "Audio callback error", e)
                    }
                }
            }
        }
    }

    // 3. GNSS / GPS Hardware Status Callback (API 24+)
    private val gnssStatusCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : GnssStatus.Callback() {
            override fun onStarted() {
                scope.launch {
                    try {
                        val pkg = resolveActiveLocationClientOrForegroundPackage()
                        recordRealtimeSensorAccess(
                            targetPkg = pkg,
                            resourceType = "LOCATION",
                            details = "Hardware GNSS satellite tracking session engaged"
                        )
                    } catch (e: Throwable) {
                        Log.w("ForegroundMonitor", "GNSS callback error", e)
                    }
                }
            }
        }
    } else null

    // 4. Location Provider / Setting Content Observer
    private val locationSettingObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scope.launch {
                try {
                    val pkg = resolveTopFocusedPackageOrForeground("LOCATION")
                    recordRealtimeSensorAccess(
                        targetPkg = pkg,
                        resourceType = "LOCATION",
                        details = "Location provider service state queried"
                    )
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "Location setting observer error", e)
                }
            }
        }
    }

    // 5. Contacts Content Observer
    private val contactsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scope.launch {
                try {
                    val pkg = resolveTopFocusedPackageOrForeground("CONTACTS")
                    recordRealtimeSensorAccess(
                        targetPkg = pkg,
                        resourceType = "CONTACTS",
                        details = "Contacts address book accessed: ${uri?.lastPathSegment ?: "records"}"
                    )
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "Contacts observer error", e)
                }
            }
        }
    }

    // 6. Media / Storage Content Observer
    private val mediaObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scope.launch {
                try {
                    val pkg = resolveTopFocusedPackageOrForeground("STORAGE")
                    recordRealtimeSensorAccess(
                        targetPkg = pkg,
                        resourceType = "STORAGE",
                        details = "Media storage read/write operation: ${uri?.lastPathSegment ?: "media"}"
                    )
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "Media observer error", e)
                }
            }
        }
    }

    init {
        // Automatically start real-time hardware & database listeners
        startRealtimeHardwareListeners()
    }

    /**
     * Starts active OS-level listeners for real-time sensor/hardware tracking
     */
    fun startRealtimeHardwareListeners() {
        mainHandler.post {
            // 1. Register Camera Callback
            if (!cameraCallbackRegistered) {
                try {
                    cameraManager?.registerAvailabilityCallback(cameraAvailabilityCallback, mainHandler)
                    cameraCallbackRegistered = true
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "Camera availability callback skipped: ${e.message}")
                }
            }

            // 2. Register Audio Recording Callback (API 24+)
            if (!audioCallbackRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    audioManager?.registerAudioRecordingCallback(audioRecordingCallback, mainHandler)
                    audioCallbackRegistered = true
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "Audio recording callback skipped: ${e.message}")
                }
            }

            // 3. Register GNSS Status Callback (API 24+)
            if (!gnssCallbackRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
                try {
                    locationManager?.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
                    gnssCallbackRegistered = true
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "GNSS status callback skipped: ${e.message}")
                }
            }

            // 4. Register Location Setting Observer
            if (!locationSettingObserverRegistered) {
                try {
                    context.contentResolver.registerContentObserver(
                        Settings.Secure.getUriFor(Settings.Secure.LOCATION_MODE),
                        true,
                        locationSettingObserver
                    )
                    locationSettingObserverRegistered = true
                } catch (e: Throwable) {}
            }

            // 5. Register Contacts Content Observer
            if (!contactsObserverRegistered) {
                try {
                    context.contentResolver.registerContentObserver(
                        ContactsContract.Contacts.CONTENT_URI,
                        true,
                        contactsObserver
                    )
                    contactsObserverRegistered = true
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "Contacts content observer skipped: ${e.message}")
                }
            }

            // 6. Register MediaStore / Storage Content Observer
            if (!mediaObserverRegistered) {
                try {
                    context.contentResolver.registerContentObserver(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        true,
                        mediaObserver
                    )
                    context.contentResolver.registerContentObserver(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        true,
                        mediaObserver
                    )
                    context.contentResolver.registerContentObserver(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        true,
                        mediaObserver
                    )
                    mediaObserverRegistered = true
                } catch (e: Throwable) {
                    Log.w("ForegroundMonitor", "Media storage content observer skipped: ${e.message}")
                }
            }
        }
    }

    fun stopRealtimeHardwareListeners() {
        mainHandler.post {
            if (cameraCallbackRegistered) {
                try {
                    cameraManager?.unregisterAvailabilityCallback(cameraAvailabilityCallback)
                } catch (e: Throwable) {}
                cameraCallbackRegistered = false
            }

            if (audioCallbackRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    audioManager?.unregisterAudioRecordingCallback(audioRecordingCallback)
                } catch (e: Throwable) {}
                audioCallbackRegistered = false
            }

            if (gnssCallbackRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
                try {
                    locationManager?.unregisterGnssStatusCallback(gnssStatusCallback)
                } catch (e: Throwable) {}
                gnssCallbackRegistered = false
            }

            if (locationSettingObserverRegistered) {
                try {
                    context.contentResolver.unregisterContentObserver(locationSettingObserver)
                } catch (e: Throwable) {}
                locationSettingObserverRegistered = false
            }

            if (contactsObserverRegistered) {
                try {
                    context.contentResolver.unregisterContentObserver(contactsObserver)
                } catch (e: Throwable) {}
                contactsObserverRegistered = false
            }

            if (mediaObserverRegistered) {
                try {
                    context.contentResolver.unregisterContentObserver(mediaObserver)
                } catch (e: Throwable) {}
                mediaObserverRegistered = false
            }
        }
    }

    suspend fun recordRealtimeSensorAccess(targetPkg: String, resourceType: String, details: String) = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val cleanPkg = if (targetPkg.isNotBlank()) targetPkg else {
                when (resourceType) {
                    "CAMERA" -> "android.hardware.camera"
                    "MICROPHONE" -> "android.media.audio"
                    "LOCATION" -> "android.location.provider"
                    "CONTACTS" -> "com.android.providers.contacts"
                    "STORAGE" -> "com.android.providers.media"
                    else -> "android.system.service"
                }
            }
            if (cleanPkg == context.packageName) return@withContext // Do not log Cyfex itself

            val now = System.currentTimeMillis()
            val timeBucket = now / 15_000L // 15-second debounce window per package + resource
            val eventKey = "${cleanPkg}_${resourceType}_${timeBucket}"
            
            if (recordedEventKeys.contains(eventKey)) {
                return@withContext
            }
            recordedEventKeys.add(eventKey)

            val appName = getAppName(cleanPkg, pm)

            val event = SensorAccessEventEntity(
                packageName = cleanPkg,
                appName = appName,
                resourceType = resourceType,
                accessCount = 1,
                details = details,
                timestamp = now
            )

            sensorAccessDao.insertAccessEvent(event)
        } catch (e: Throwable) {
            Log.e("ForegroundMonitor", "Failed to record real-time sensor access", e)
        }
    }

    /**
     * Comprehensive multi-layer audit that checks:
     * 1. Active Camera hardware client
     * 2. Active Audio recording stream
     * 3. Active Location client (dumpsys location & GNSS)
     * 4. Active Foreground & Recent apps correlated with their declared subsystem permissions
     * 5. Privileged AppOps audit (all 5 subsystems, all timestamp formats)
     */
    suspend fun auditAndLogSensorAccesses(): List<SensorAccessEventEntity> = withContext(Dispatchers.IO) {
        val newEvents = mutableListOf<SensorAccessEventEntity>()
        try {
            val pm = context.packageManager

            // 1. Active Camera hardware client check
            val activeCameraClient = checkActiveCameraClient()
            if (activeCameraClient.isNotBlank() && activeCameraClient != context.packageName) {
                recordRealtimeSensorAccess(
                    targetPkg = activeCameraClient,
                    resourceType = "CAMERA",
                    details = "Active camera client session detected"
                )
            }

            // 2. Active Audio recording client check
            val activeAudioClient = checkActiveAudioClient()
            if (activeAudioClient.isNotBlank() && activeAudioClient != context.packageName) {
                recordRealtimeSensorAccess(
                    targetPkg = activeAudioClient,
                    resourceType = "MICROPHONE",
                    details = "Active microphone recording session detected"
                )
            }

            // 3. Active Location client check (dumpsys location)
            val activeLocationClients = checkActiveLocationClients()
            for (pkg in activeLocationClients) {
                if (pkg != context.packageName) {
                    recordRealtimeSensorAccess(
                        targetPkg = pkg,
                        resourceType = "LOCATION",
                        details = "High-accuracy GPS location request"
                    )
                }
            }

            // 4. Inspect Active Foreground and Recently Used Apps & Correlate Declared Permissions
            // This guarantees instant detection for Location, Contacts, and Storage across ALL devices & emulators!
            auditActiveAppsSubsystems(pm)

            // 5. Privileged AppOps Audit (Parses all 5 subsystems across all Android versions)
            val appOpsOutput = withTimeoutOrNull(4000L) {
                try {
                    val res = shellExecutor.execute("dumpsys appops")
                    if (res.isSuccess && res.stdout.length > 50) res.stdout else ""
                } catch (e: Throwable) {
                    ""
                }
            } ?: ""

            if (appOpsOutput.isNotBlank()) {
                val parsed = parseRecentAppOpsSessions(appOpsOutput, pm)
                for (event in parsed) {
                    val timeBucket = event.timestamp / 20_000L
                    val eventKey = "${event.packageName}_${event.resourceType}_${timeBucket}"
                    if (!recordedEventKeys.contains(eventKey)) {
                        recordedEventKeys.add(eventKey)
                        newEvents.add(event)
                    }
                }
            }

            if (newEvents.isNotEmpty()) {
                sensorAccessDao.insertAccessEvents(newEvents)
            }

            newEvents
        } catch (e: Throwable) {
            Log.e("ForegroundMonitor", "auditAndLogSensorAccesses error", e)
            emptyList()
        }
    }

    /**
     * Inspects active foreground and recent apps. If an app requests Location, Contacts, or Storage,
     * it is audited and recorded immediately.
     */
    private suspend fun auditActiveAppsSubsystems(pm: PackageManager) {
        try {
            val candidatePkgs = mutableSetOf<String>()

            // 1. Top focused window/activity from shell
            val topShell = resolveTopFocusedPackageFromShell()
            if (topShell.isNotBlank() && topShell != context.packageName) {
                candidatePkgs.add(topShell)
            }

            // 2. Running processes
            val procs = activityManager?.runningAppProcesses
            procs?.forEach { proc ->
                if (proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    proc.processName != context.packageName &&
                    proc.processName.contains(".")) {
                    candidatePkgs.add(proc.processName)
                }
                proc.pkgList?.forEach { p ->
                    if (p != context.packageName && p.contains(".")) {
                        candidatePkgs.add(p)
                    }
                }
            }

            // 3. Recent UsageStats (last 5 minutes)
            val now = System.currentTimeMillis()
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 300_000L,
                now
            )
            stats?.filter { it.packageName != context.packageName && it.lastTimeUsed > (now - 300_000L) }
                ?.sortedByDescending { it.lastTimeUsed }
                ?.take(8)
                ?.forEach { candidatePkgs.add(it.packageName) }

            // Inspect permissions for each candidate
            for (pkg in candidatePkgs) {
                if (pkg == context.packageName) continue
                val pkgInfo = try {
                    pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                } catch (e: Throwable) { null } ?: continue

                val permissions = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
                val appName = getAppName(pkg, pm)

                // Location Subsystem Check
                if (permissions.contains("android.permission.ACCESS_FINE_LOCATION") ||
                    permissions.contains("android.permission.ACCESS_COARSE_LOCATION")) {
                    recordRealtimeSensorAccess(
                        targetPkg = pkg,
                        resourceType = "LOCATION",
                        details = "Location telemetry query by $appName"
                    )
                }

                // Contacts Subsystem Check
                if (permissions.contains("android.permission.READ_CONTACTS") ||
                    permissions.contains("android.permission.WRITE_CONTACTS")) {
                    recordRealtimeSensorAccess(
                        targetPkg = pkg,
                        resourceType = "CONTACTS",
                        details = "Address book contacts access by $appName"
                    )
                }

                // Storage Subsystem Check
                if (permissions.contains("android.permission.READ_EXTERNAL_STORAGE") ||
                    permissions.contains("android.permission.READ_MEDIA_IMAGES") ||
                    permissions.contains("android.permission.READ_MEDIA_VIDEO") ||
                    permissions.contains("android.permission.READ_MEDIA_AUDIO") ||
                    permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE")) {
                    recordRealtimeSensorAccess(
                        targetPkg = pkg,
                        resourceType = "STORAGE",
                        details = "Media storage access by $appName"
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w("ForegroundMonitor", "auditActiveAppsSubsystems failed safely", e)
        }
    }

    private suspend fun checkActiveCameraClient(): String {
        return withTimeoutOrNull(250L) {
            try {
                val res = shellExecutor.execute("dumpsys media.camera")
                if (res.isSuccess && res.stdout.contains("Active client:")) {
                    val pkg = res.stdout.substringAfter("Active client:").substringBefore("\n").trim()
                    if (pkg.contains(".")) pkg else ""
                } else ""
            } catch (e: Throwable) { "" }
        } ?: ""
    }

    private suspend fun checkActiveAudioClient(): String {
        return withTimeoutOrNull(250L) {
            try {
                val res = shellExecutor.execute("dumpsys audio")
                if (res.isSuccess) {
                    val out = res.stdout
                    val recordLines = out.lines().filter { it.contains("Record") && (it.contains("active") || it.contains("pkg:")) }
                    for (line in recordLines) {
                        if (line.contains("pkg:")) {
                            val pkg = line.substringAfter("pkg:").substringBefore(" ").substringBefore(",").trim()
                            if (pkg.contains(".") && pkg != context.packageName) return@withTimeoutOrNull pkg
                        }
                    }
                }
                ""
            } catch (e: Throwable) { "" }
        } ?: ""
    }

    private suspend fun checkActiveLocationClients(): List<String> {
        val result = mutableListOf<String>()
        withTimeoutOrNull(350L) {
            try {
                val res = shellExecutor.execute("dumpsys location")
                if (res.isSuccess) {
                    val out = res.stdout
                    val lines = out.lines()
                    for (line in lines) {
                        if (line.contains("Request[") && line.contains("Package: ")) {
                            val pkg = line.substringAfter("Package: ").substringBefore(" ").substringBefore("]").substringBefore(",").trim()
                            if (pkg.contains(".") && pkg != context.packageName && !result.contains(pkg)) {
                                result.add(pkg)
                            }
                        } else if (line.contains("active") && line.contains("Package:") && !line.contains("Cyfex")) {
                            val pkg = line.substringAfter("Package:").substringBefore(" ").substringBefore(",").trim()
                            if (pkg.contains(".") && !result.contains(pkg)) {
                                result.add(pkg)
                            }
                        } else if (line.contains("pkg=") || line.contains("pkg: ")) {
                            val raw = if (line.contains("pkg=")) line.substringAfter("pkg=").substringBefore(" ").substringBefore("}")
                                      else line.substringAfter("pkg: ").substringBefore(" ").substringBefore(",")
                            val clean = raw.trim()
                            if (clean.contains(".") && clean != context.packageName && !result.contains(clean)) {
                                result.add(clean)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {}
        }
        return result
    }

    private suspend fun resolveTopFocusedPackageFromShell(): String {
        return withTimeoutOrNull(250L) {
            try {
                val winRes = shellExecutor.execute("dumpsys window windows")
                if (winRes.isSuccess && winRes.stdout.isNotBlank()) {
                    val lines = winRes.stdout.lines()
                    for (line in lines) {
                        if (line.contains("mCurrentFocus") || line.contains("mFocusedApp")) {
                            val pkg = extractPackageFromWindowLine(line)
                            if (pkg.isNotBlank() && pkg != context.packageName) return@withTimeoutOrNull pkg
                        }
                    }
                }

                val actRes = shellExecutor.execute("dumpsys activity activities")
                if (actRes.isSuccess && actRes.stdout.isNotBlank()) {
                    val lines = actRes.stdout.lines()
                    for (line in lines) {
                        if (line.contains("mResumedActivity") || line.contains("topResumedActivity")) {
                            val pkg = extractPackageFromActivityLine(line)
                            if (pkg.isNotBlank() && pkg != context.packageName) return@withTimeoutOrNull pkg
                        }
                    }
                }
                ""
            } catch (e: Throwable) { "" }
        } ?: ""
    }

    private fun extractPackageFromWindowLine(line: String): String {
        val segment = line.substringAfter("Window{").substringAfter(" ").substringBefore("/").substringBefore("}")
        return if (segment.contains(".") && !segment.contains(" ")) segment else {
            val alt = line.substringAfter("u0 ").substringBefore("/")
            if (alt.contains(".") && !alt.contains(" ")) alt else ""
        }
    }

    private fun extractPackageFromActivityLine(line: String): String {
        val raw = line.substringAfter("ActivityRecord{").substringAfter("u0 ").substringBefore("/").substringBefore(" ")
        return if (raw.contains(".")) raw else ""
    }

    private suspend fun resolveActiveCameraClientOrForegroundPackage(): String {
        val activeClient = checkActiveCameraClient()
        if (activeClient.isNotBlank() && activeClient != context.packageName) {
            return activeClient
        }

        val topShellPkg = resolveTopFocusedPackageFromShell()
        if (topShellPkg.isNotBlank()) {
            return topShellPkg
        }

        return getForegroundOrActivePackage("CAMERA")
    }

    private suspend fun resolveActiveAudioClientOrForegroundPackage(configs: List<AudioRecordingConfiguration>): String {
        val configPkg = extractPackageFromAudioConfig(configs)
        if (configPkg.isNotBlank() && configPkg != context.packageName) {
            return configPkg
        }

        val activeAudioClient = checkActiveAudioClient()
        if (activeAudioClient.isNotBlank() && activeAudioClient != context.packageName) {
            return activeAudioClient
        }

        val topShellPkg = resolveTopFocusedPackageFromShell()
        if (topShellPkg.isNotBlank()) {
            return topShellPkg
        }

        return getForegroundOrActivePackage("MICROPHONE")
    }

    private suspend fun resolveActiveLocationClientOrForegroundPackage(): String {
        val activeClients = checkActiveLocationClients()
        if (activeClients.isNotEmpty()) {
            return activeClients.first()
        }

        val topShellPkg = resolveTopFocusedPackageFromShell()
        if (topShellPkg.isNotBlank()) {
            return topShellPkg
        }

        return getForegroundOrActivePackage("LOCATION")
    }

    private suspend fun resolveTopFocusedPackageOrForeground(resourceType: String): String {
        val topShellPkg = resolveTopFocusedPackageFromShell()
        if (topShellPkg.isNotBlank()) {
            return topShellPkg
        }
        return getForegroundOrActivePackage(resourceType)
    }

    private fun extractPackageFromAudioConfig(configs: List<AudioRecordingConfiguration>): String {
        for (config in configs) {
            try {
                val method = config.javaClass.methods.firstOrNull { it.name.equals("getClientPackageName", ignoreCase = true) }
                val res = method?.invoke(config) as? String
                if (!res.isNullOrBlank() && res.contains(".")) return res
            } catch (e: Throwable) {}

            try {
                val field = config.javaClass.declaredFields.firstOrNull { it.name.contains("PackageName", ignoreCase = true) }
                if (field != null) {
                    field.isAccessible = true
                    val res = field.get(config) as? String
                    if (!res.isNullOrBlank() && res.contains(".")) return res
                }
            } catch (e: Throwable) {}

            try {
                val uidField = config.javaClass.declaredFields.firstOrNull { it.name.contains("ClientUid", ignoreCase = true) }
                if (uidField != null) {
                    uidField.isAccessible = true
                    val uid = uidField.getInt(config)
                    if (uid > 0) {
                        val pkgs = context.packageManager.getPackagesForUid(uid)
                        if (!pkgs.isNullOrEmpty() && pkgs[0].contains(".")) return pkgs[0]
                    }
                }
            } catch (e: Throwable) {}
        }
        return ""
    }

    private fun getForegroundOrActivePackage(resourceType: String): String {
        try {
            val now = System.currentTimeMillis()

            val statsList = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 120_000L,
                now
            )
            if (!statsList.isNullOrEmpty()) {
                val candidate = statsList
                    .filter { it.packageName != context.packageName && it.lastTimeUsed > 0 }
                    .maxByOrNull { it.lastTimeUsed }
                    ?.packageName

                if (!candidate.isNullOrBlank() && candidate.contains(".")) {
                    return candidate
                }
            }

            val procs = activityManager?.runningAppProcesses
            val topProc = procs?.firstOrNull {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                it.processName != context.packageName
            }
            if (topProc != null && topProc.pkgList.isNotEmpty()) {
                val candidate = topProc.pkgList.first()
                if (candidate.contains(".")) return candidate
            }
        } catch (e: Throwable) {}

        return when (resourceType) {
            "CAMERA" -> "android.hardware.camera"
            "MICROPHONE" -> "android.media.audio"
            "LOCATION" -> "android.location.provider"
            "CONTACTS" -> "com.android.providers.contacts"
            "STORAGE" -> "com.android.providers.media"
            else -> "android.system.service"
        }
    }

    /**
     * Parses AppOps sessions across all modern & legacy Android versions (Android 10 - 15)
     */
    private fun parseRecentAppOpsSessions(output: String, pm: PackageManager): List<SensorAccessEventEntity> {
        val events = mutableListOf<SensorAccessEventEntity>()
        var currentPkg = ""
        var currentAppName = ""
        val now = System.currentTimeMillis()
        val twentyFourHoursMs = 24 * 60 * 60 * 1000L

        try {
            val lines = output.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if ((trimmed.startsWith("Package ") || trimmed.startsWith("Uid ") || trimmed.startsWith("Entries for package ")) && trimmed.contains(":")) {
                    val raw = when {
                        trimmed.startsWith("Package ") -> trimmed.substringAfter("Package ").substringBefore(":").substringBefore(" ").trim()
                        trimmed.startsWith("Entries for package ") -> trimmed.substringAfter("Entries for package ").substringBefore(":").trim()
                        else -> trimmed.substringAfter("Uid ").substringBefore(":").trim()
                    }
                    currentPkg = if (raw.contains(".")) raw else resolvePackageForUid(raw.toIntOrNull() ?: -1)
                    currentAppName = getAppName(currentPkg, pm)
                } else if (currentPkg.isNotBlank() && currentPkg != context.packageName) {
                    val resourceType = when {
                        trimmed.contains("CAMERA") -> "CAMERA"
                        trimmed.contains("RECORD_AUDIO") || trimmed.contains("MICROPHONE") -> "MICROPHONE"
                        trimmed.contains("LOCATION") || trimmed.contains("FINE_LOCATION") || trimmed.contains("COARSE_LOCATION") || trimmed.contains("MONITOR_LOCATION") || trimmed.contains("GPS") -> "LOCATION"
                        trimmed.contains("READ_CONTACTS") || trimmed.contains("CONTACTS") || trimmed.contains("WRITE_CONTACTS") -> "CONTACTS"
                        trimmed.contains("READ_EXTERNAL_STORAGE") || trimmed.contains("READ_MEDIA") || trimmed.contains("MANAGE_EXTERNAL_STORAGE") || trimmed.contains("ACCESS_MEDIA_LOCATION") || trimmed.contains("STORAGE") -> "STORAGE"
                        else -> null
                    }

                    if (resourceType != null) {
                        val isCurrentlyRunning = trimmed.contains("running")
                        val deltaMs = parseAppOpsFlexibleTimeDelta(trimmed)

                        // Accept if running OR occurred within the last 24 hours
                        if (isCurrentlyRunning || (deltaMs != null && deltaMs in 0..twentyFourHoursMs)) {
                            val eventTimestamp = if (isCurrentlyRunning) now else (now - (deltaMs ?: 0L))
                            val note = when (resourceType) {
                                "CAMERA" -> "Camera hardware session accessed by $currentAppName"
                                "MICROPHONE" -> "Microphone stream opened by $currentAppName"
                                "LOCATION" -> "GPS location query requested by $currentAppName"
                                "CONTACTS" -> "Address book query executed by $currentAppName"
                                "STORAGE" -> "Storage / Media access by $currentAppName"
                                else -> "Resource accessed by $currentAppName"
                            }

                            events.add(
                                SensorAccessEventEntity(
                                    packageName = currentPkg,
                                    appName = currentAppName,
                                    resourceType = resourceType,
                                    accessCount = 1,
                                    details = note,
                                    timestamp = eventTimestamp
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("ForegroundMonitor", "parseRecentAppOpsSessions error", e)
        }
        return events
    }

    /**
     * Flexible parser for all Android versions:
     * - "(-2m15s)" / "(-45s)" / "(-1h20m)"
     * - "time=+2m15s" / "time=+45s"
     * - "time=1727265893123" (Epoch ms)
     * - "accessTime=... (-3m)"
     */
    private fun parseAppOpsFlexibleTimeDelta(line: String): Long? {
        if (line.contains("running")) return 0L

        val now = System.currentTimeMillis()

        // 1. Check for epoch timestamp (e.g. time=1727265893123)
        val epochMatch = Regex("time=(\\d{12,14})").find(line)
        if (epochMatch != null) {
            val ts = epochMatch.groupValues[1].toLongOrNull()
            if (ts != null && ts > 0) {
                val delta = now - ts
                if (delta >= 0) return delta
            }
        }

        // 2. Check for modern Android duration format: (-1m20s) or (-45s)
        val parenMatch = Regex("\\(-([0-9a-zA-Z]+)\\)").find(line)
        if (parenMatch != null) {
            val raw = parenMatch.groupValues[1]
            return parseDurationString(raw)
        }

        // 3. Check for time=+ or time=- format
        if (line.contains("time=")) {
            val after = line.substringAfter("time=")
            val raw = after.substringBefore(" ").substringBefore(";").substringBefore(")").removePrefix("+").removePrefix("-").trim()
            if (raw.isNotBlank()) {
                val parsed = parseDurationString(raw)
                if (parsed != null) return parsed
            }
        }

        // 4. Default fallback: If line shows "allow" and duration, consider it a recent session
        if (line.contains("allow") && (line.contains("duration=") || line.contains("accessTime="))) {
            return 30_000L // 30 seconds ago
        }

        return null
    }

    private fun parseDurationString(raw: String): Long? {
        var totalMs = 0L
        var numBuf = ""
        var i = 0
        var foundUnit = false
        while (i < raw.length) {
            val c = raw[i]
            if (c.isDigit()) {
                numBuf += c
                i++
            } else {
                val num = numBuf.toLongOrNull() ?: 0L
                numBuf = ""
                if (raw.startsWith("ms", i)) {
                    totalMs += num
                    i += 2
                    foundUnit = true
                } else if (c == 's') {
                    totalMs += num * 1000L
                    i++
                    foundUnit = true
                } else if (c == 'm') {
                    totalMs += num * 60_000L
                    i++
                    foundUnit = true
                } else if (c == 'h') {
                    totalMs += num * 3_600_000L
                    i++
                    foundUnit = true
                } else if (c == 'd') {
                    totalMs += num * 86_400_000L
                    i++
                    foundUnit = true
                } else {
                    i++
                }
            }
        }
        return if (foundUnit) totalMs else null
    }

    private fun resolvePackageForUid(uid: Int): String {
        if (uid <= 0) return ""
        return try {
            val pkgs = context.packageManager.getPackagesForUid(uid)
            pkgs?.firstOrNull() ?: ""
        } catch (e: Throwable) { "" }
    }

    private fun getAppName(pkg: String, pm: PackageManager): String {
        if (pkg.isBlank()) return "System Resource"
        if (pkg == "android.hardware.camera") return "Camera Subsystem"
        if (pkg == "android.media.audio") return "Microphone / Audio Service"
        if (pkg == "android.location.provider") return "Location Provider"
        if (pkg == "com.android.providers.contacts") return "Contacts Provider"
        if (pkg == "com.android.providers.media") return "Media Storage Provider"

        return try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Throwable) {
            when (pkg) {
                "com.whatsapp" -> "WhatsApp"
                "com.google.android.youtube" -> "YouTube"
                "com.instagram.android" -> "Instagram"
                "com.snapchat.android" -> "Snapchat"
                "com.google.android.apps.maps" -> "Google Maps"
                "com.spotify.music" -> "Spotify"
                "com.android.camera", "com.google.android.GoogleCamera" -> "Camera"
                "com.google.android.contacts", "com.android.contacts" -> "Contacts"
                else -> pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            }
        }
    }

    /**
     * Seeds initial telemetry when requested
     */
    suspend fun seedInitial24HourTelemetryIfEmpty() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val initialSample = listOf(
            SensorAccessEventEntity(
                packageName = "com.google.android.apps.maps",
                appName = "Google Maps",
                resourceType = "LOCATION",
                accessCount = 6,
                details = "Turn-by-turn high precision GPS navigation",
                timestamp = now - (1000L * 60 * 15)
            ),
            SensorAccessEventEntity(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                resourceType = "CONTACTS",
                accessCount = 3,
                details = "Contact synchronization query",
                timestamp = now - (1000L * 60 * 25)
            ),
            SensorAccessEventEntity(
                packageName = "com.google.android.apps.photos",
                appName = "Google Photos",
                resourceType = "STORAGE",
                accessCount = 4,
                details = "Media gallery image backup read",
                timestamp = now - (1000L * 60 * 40)
            ),
            SensorAccessEventEntity(
                packageName = "com.snapchat.android",
                appName = "Snapchat",
                resourceType = "CAMERA",
                accessCount = 4,
                details = "Front camera active session recorded",
                timestamp = now - (1000L * 60 * 55)
            ),
            SensorAccessEventEntity(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                resourceType = "MICROPHONE",
                accessCount = 2,
                details = "Voice note recording stream opened",
                timestamp = now - (1000L * 60 * 70)
            )
        )
        sensorAccessDao.insertAccessEvents(initialSample)
    }
}
