package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.example.baseline.BaselineManager
import com.example.collectors.*
import com.example.data.db.AppDatabase
import com.example.data.db.entity.*
import com.example.data.model.*
import com.example.ml.MlInference
import com.example.risk.RiskEngine
import com.example.rules.RuleEngine
import com.example.shizuku.ShellExecutor
import com.example.shizuku.ShizukuManager
import com.example.staticanalysis.ApkMetadataAnalyzer
import com.example.staticanalysis.ManifestAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityRepository(
    private val context: Context,
    private val database: AppDatabase,
    val shizukuManager: ShizukuManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    val shellExecutor = ShellExecutor(shizukuManager)
    val processCollector = ProcessCollector(shellExecutor)
    val packageCollector = PackageCollector(context)
    val permissionCollector = PermissionCollector()
    val componentCollector = ComponentCollector()
    val resourceCollector = ResourceCollector(shellExecutor)
    val networkCollector = NetworkCollector(shellExecutor)
    val eventCollector = EventCollector()

    val apkAnalyzer = ApkMetadataAnalyzer(context)
    val manifestAnalyzer = ManifestAnalyzer()

    val baselineManager = BaselineManager(database.baselineDao())
    val ruleEngine = RuleEngine()
    val mlInference = MlInference()
    val riskEngine = RiskEngine()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _currentScanMessage = MutableStateFlow("Idle")
    val currentScanMessage: StateFlow<String> = _currentScanMessage.asStateFlow()

    // Live reactive database flows
    val allApplications: Flow<List<ApplicationEntity>> = database.applicationDao().getAllApplications()
    val latestProcesses: Flow<List<ProcessEntity>> = database.processDao().getLatestProcesses()
    val allEvents: Flow<List<BehaviorEventEntity>> = database.behaviorEventDao().getAllEvents()
    val allFindings: Flow<List<FindingEntity>> = database.findingDao().getAllFindings()
    val allScans: Flow<List<ScanEntity>> = database.scanDao().getAllScans()
    val latestScan: Flow<ScanEntity?> = database.scanDao().getLatestScan()

    init {
        // Trigger initial data load in background
        scope.launch {
            if (database.applicationDao().getAllApplications().first().isEmpty()) {
                performScan()
            }
        }
    }

    fun getDeviceInfo(): DeviceTelemetryInfo {
        return DeviceTelemetryInfo(
            deviceId = "dev_${Build.ID}_${Build.DEVICE}",
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sdkVersion = Build.VERSION.SDK_INT,
            kernelVersion = System.getProperty("os.version") ?: "Linux 5.15",
            buildId = Build.DISPLAY,
            securityPatchLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH
            } else "N/A",
            isWirelessDebuggingSupported = shizukuManager.isWirelessDebuggingAvailable(),
            isShizukuInstalled = shizukuManager.isInstalled(),
            isShizukuRunning = shizukuManager.isRunning(),
            isShizukuGranted = shizukuManager.hasPermission()
        )
    }

    suspend fun performScan(): SecurityScanSummary = withContext(Dispatchers.IO) {
        _isScanning.value = true
        _scanProgress.value = 0.05f
        _currentScanMessage.value = "Checking Shizuku ADB bridge authorization..."
        val startTime = System.currentTimeMillis()

        shizukuManager.refreshStatus()
        val isPrivileged = shizukuManager.hasPermission()

        _scanProgress.value = 0.15f
        _currentScanMessage.value = "Collecting privileged processes via ADB shell..."
        val rawProcesses = processCollector.collectProcesses()

        // Insert processes into DB
        database.processDao().clearProcesses()
        database.processDao().insertProcesses(rawProcesses.map {
            ProcessEntity(
                pid = it.pid,
                ppid = it.ppid,
                user = it.user,
                processName = it.processName,
                packageName = it.packageName,
                cpuPercent = it.cpuPercent,
                vszKb = it.vszKb,
                rssKb = it.rssKb,
                state = it.state,
                riskScore = it.riskScore,
                isSuspicious = it.isSuspicious,
                anomalyNote = it.anomalyNote,
                timestamp = it.timestamp
            )
        })

        _scanProgress.value = 0.30f
        _currentScanMessage.value = "Inspecting network sockets & telemetry..."
        val activeConnections = networkCollector.collectActiveConnections()

        _scanProgress.value = 0.45f
        _currentScanMessage.value = "Analyzing installed application inventory & manifests..."
        val packages = packageCollector.getInstalledPackages()
        val targetPackages = if (packages.isNotEmpty()) packages else getSimulatedPackages()

        val appEntities = mutableListOf<ApplicationEntity>()
        val newFindings = mutableListOf<FindingEntity>()
        val newEvents = mutableListOf<BehaviorEventEntity>()

        var highRiskApps = 0
        var totalRiskSum = 0

        // 1. Process suspicious processes from Shizuku into findings
        val suspiciousProcesses = rawProcesses.filter { it.isSuspicious || it.cpuPercent > 70.0 }
        suspiciousProcesses.forEach { proc ->
            val findingId = "fnd_proc_${proc.pid}_${System.currentTimeMillis()}"
            val title = "Suspicious Process: ${proc.processName} (PID ${proc.pid})"
            val description = proc.anomalyNote ?: "Privileged Shizuku inspection detected an unconstrained execution vector under process '${proc.processName}'."
            val evidence = "PID: ${proc.pid}, CPU: ${"%.1f".format(proc.cpuPercent)}%, RSS: ${proc.rssKb / 1024}MB, User: ${proc.user}, State: ${proc.state}"

            newFindings.add(
                FindingEntity(
                    findingId = findingId,
                    ruleId = "PROC-ANOMALY",
                    packageName = proc.packageName,
                    appName = proc.processName,
                    category = "Privileged Process Anomaly",
                    severity = if (proc.cpuPercent > 70.0) "CRITICAL" else "HIGH",
                    title = title,
                    description = description,
                    evidence = evidence,
                    remediation = "Use Shizuku shell to terminate PID ${proc.pid} or inspect corresponding package.",
                    timestamp = System.currentTimeMillis()
                )
            )

            newEvents.add(
                BehaviorEventEntity(
                    eventId = "evt_proc_${proc.pid}_${System.currentTimeMillis()}",
                    packageName = proc.packageName,
                    appName = proc.processName,
                    eventType = "PROC_ANOMALY",
                    severity = if (proc.cpuPercent > 70.0) "CRITICAL" else "HIGH",
                    description = title,
                    evidence = evidence,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // 2. Process application telemetry & rules
        targetPackages.forEachIndexed { index, pkgInfo ->
            val step = 0.45f + (index.toFloat() / targetPackages.size) * 0.45f
            _scanProgress.value = step
            _currentScanMessage.value = "Analyzing ${pkgInfo.packageName}..."

            val (reqPerms, grantedPerms, dangPerms) = permissionCollector.analyzePermissions(pkgInfo)
            val components = componentCollector.extractComponents(pkgInfo)
            val staticMetrics = apkAnalyzer.analyzeApk(pkgInfo)

            val appProcesses = rawProcesses.filter { it.packageName == pkgInfo.packageName }
            val currentMaxCpu = appProcesses.maxOfOrNull { it.cpuPercent } ?: 0.5
            val currentMaxMemMb = (appProcesses.maxOfOrNull { it.rssKb } ?: 50000L) / 1024.0

            // Baseline deviation
            val baseline = baselineManager.updateBaseline(pkgInfo.packageName, appProcesses, activeConnections.size)
            val deviation = baselineManager.getDeviationForApp(pkgInfo.packageName, currentMaxCpu, currentMaxMemMb)

            val isSystemApp = pkgInfo.applicationInfo?.let {
                (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } ?: false

            val appTelemetry = AppSecurityTelemetry(
                packageName = pkgInfo.packageName,
                appName = pkgInfo.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: pkgInfo.packageName,
                versionName = pkgInfo.versionName ?: "1.0",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode else 1L,
                uid = pkgInfo.applicationInfo?.uid ?: 10000,
                isSystemApp = isSystemApp,
                installTime = pkgInfo.firstInstallTime,
                updateTime = pkgInfo.lastUpdateTime,
                staticMetrics = staticMetrics,
                components = components,
                requestedPermissions = reqPerms,
                grantedPermissions = grantedPerms,
                dangerousPermissions = dangPerms,
                overallRiskScore = 0,
                riskLevel = RiskLevel.SAFE,
                staticScore = 0,
                runtimeScore = 0,
                networkScore = 0,
                anomalyScore = 0.0,
                mlMaliciousProb = 0.0,
                findingsCount = 0,
                baselineDeviation = deviation
            )

            // ML Inference
            val (rfProb, ifScore) = mlInference.infer(appTelemetry, rawProcesses, activeConnections)

            // Rule Engine
            val ruleResults = ruleEngine.evaluateAll(appTelemetry, rawProcesses, activeConnections)
            val generatedFindings = ruleEngine.generateFindings(appTelemetry, ruleResults)

            // Risk Correlation
            val riskBreakdown = riskEngine.calculateRisk(
                appTelemetry,
                rawProcesses,
                activeConnections,
                ruleResults,
                rfProb,
                ifScore
            )

            if (riskBreakdown.riskLevel >= RiskLevel.HIGH) {
                highRiskApps++
            }
            totalRiskSum += riskBreakdown.totalScore

            // Convert to Entity
            appEntities.add(
                ApplicationEntity(
                    packageName = appTelemetry.packageName,
                    appName = appTelemetry.appName,
                    versionName = appTelemetry.versionName,
                    uid = appTelemetry.uid,
                    apkHash = staticMetrics.sha256Hash,
                    certHash = staticMetrics.signingCertHash,
                    riskScore = riskBreakdown.totalScore,
                    riskLevel = riskBreakdown.riskLevel.name,
                    isSystemApp = isSystemApp,
                    permissionsJson = "${reqPerms.size} requested, ${grantedPerms.size} granted",
                    dangerousPermissionsJson = dangPerms.joinToString(", "),
                    componentsJson = "Act: ${components.activitiesCount} (exp ${components.exportedActivities}), Svc: ${components.servicesCount} (exp ${components.exportedServices})",
                    staticMetricsJson = "Size: ${staticMetrics.apkSizeMb}MB, Dex: ${staticMetrics.dexCount}, Libs: ${staticMetrics.nativeLibsCount}",
                    mlMaliciousProb = rfProb,
                    anomalyScore = ifScore,
                    findingsCount = generatedFindings.size,
                    lastScanned = System.currentTimeMillis()
                )
            )

            generatedFindings.forEach { f ->
                newFindings.add(
                    FindingEntity(
                        findingId = f.id,
                        ruleId = f.ruleId,
                        packageName = f.packageName,
                        appName = f.appName,
                        category = f.category,
                        severity = f.severity.name,
                        title = f.title,
                        description = f.description,
                        evidence = f.evidence,
                        remediation = f.remediationSuggestion,
                        timestamp = f.timestamp
                    )
                )

                newEvents.add(
                    BehaviorEventEntity(
                        eventId = "evt_${System.currentTimeMillis()}_${f.ruleId}_${(100..999).random()}",
                        packageName = f.packageName,
                        appName = f.appName,
                        eventType = f.ruleId,
                        severity = f.severity.name,
                        description = f.title,
                        evidence = f.evidence,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        // Persist scan findings & apps
        database.applicationDao().clearApplications()
        database.applicationDao().insertApplications(appEntities)

        database.findingDao().clearFindings()
        database.findingDao().insertFindings(newFindings)

        database.behaviorEventDao().insertEvents(newEvents)

        val duration = System.currentTimeMillis() - startTime
        val overallRisk = if (targetPackages.isNotEmpty()) (totalRiskSum / targetPackages.size).coerceIn(0, 100) else 10
        val overallLevel = RiskLevel.fromScore(overallRisk)

        val summary = SecurityScanSummary(
            scanId = "scan_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            durationMs = duration,
            totalAppsScanned = targetPackages.size,
            highRiskAppsCount = highRiskApps,
            suspiciousProcessesCount = rawProcesses.count { it.isSuspicious },
            activeFindingsCount = newFindings.size,
            overallDeviceRiskScore = overallRisk,
            overallRiskLevel = overallLevel,
            isShizukuPrivileged = isPrivileged,
            staticAnalysisWeight = 25,
            runtimeBehaviorWeight = 30,
            networkBehaviorWeight = 20,
            anomalyWeight = 15,
            ruleFindingsWeight = 10
        )

        database.scanDao().insertScan(
            ScanEntity(
                scanId = summary.scanId,
                deviceId = Build.MODEL,
                timestamp = summary.timestamp,
                durationMs = summary.durationMs,
                totalAppsScanned = summary.totalAppsScanned,
                highRiskAppsCount = summary.highRiskAppsCount,
                suspiciousProcessesCount = summary.suspiciousProcessesCount,
                activeFindingsCount = summary.activeFindingsCount,
                overallRiskScore = summary.overallDeviceRiskScore,
                overallRiskLevel = summary.overallRiskLevel.name,
                isShizukuPrivileged = summary.isShizukuPrivileged,
                breakdownJson = "Static: 25%, Runtime: 30%, Network: 20%, ML: 15%, Rules: 10%"
            )
        )

        _scanProgress.value = 1.0f
        _currentScanMessage.value = "Security scan complete. ${newFindings.size} findings detected."
        _isScanning.value = false

        summary
    }

    suspend fun killProcess(pid: Int): Boolean = withContext(Dispatchers.IO) {
        val result = shellExecutor.execute(com.example.shizuku.CommandBuilder.buildKillProcessCommand(pid))
        database.processDao().clearProcesses()
        val refreshed = processCollector.collectProcesses()
        database.processDao().insertProcesses(refreshed.map {
            ProcessEntity(
                pid = it.pid,
                ppid = it.ppid,
                user = it.user,
                processName = it.processName,
                packageName = it.packageName,
                cpuPercent = it.cpuPercent,
                vszKb = it.vszKb,
                rssKb = it.rssKb,
                state = it.state,
                riskScore = it.riskScore,
                isSuspicious = it.isSuspicious,
                anomalyNote = it.anomalyNote,
                timestamp = it.timestamp
            )
        })

        // Remove any finding associated with this killed PID
        val currentFindings = database.findingDao().getAllFindings().first()
        val remaining = currentFindings.filter { !it.title.contains("PID $pid") }
        database.findingDao().clearFindings()
        database.findingDao().insertFindings(remaining)

        result.isSuccess
    }

    suspend fun injectControlledScenario(scenarioName: String) = withContext(Dispatchers.IO) {
        when (scenarioName) {
            "CRYPTOMINER" -> {
                val evilProc = ProcessEntity(
                    pid = 9814,
                    ppid = 1,
                    user = "u0_a189",
                    processName = "com.sample.miner:worker",
                    packageName = "com.sample.miner",
                    cpuPercent = 88.5,
                    vszKb = 3450000L,
                    rssKb = 480000L,
                    state = "R",
                    riskScore = 92,
                    isSuspicious = true,
                    anomalyNote = "Cryptocurrency mining daemon consuming 88.5% CPU",
                    timestamp = System.currentTimeMillis()
                )
                database.processDao().insertProcesses(listOf(evilProc))

                val evilApp = ApplicationEntity(
                    packageName = "com.sample.miner",
                    appName = "QuickClean Optimizer",
                    versionName = "2.4.1",
                    uid = 10189,
                    apkHash = "e89b21f8a4103c89b21c43f721a",
                    certHash = "9F:23:41:A2:81:7C",
                    riskScore = 91,
                    riskLevel = "CRITICAL",
                    isSystemApp = false,
                    permissionsJson = "18 requested, 14 granted",
                    dangerousPermissionsJson = "WAKE_LOCK, SYSTEM_ALERT_WINDOW, RECEIVE_BOOT_COMPLETED",
                    componentsJson = "Act: 2, Svc: 4 (exp 3)",
                    staticMetricsJson = "Size: 24.5MB, Dex: 4, Libs: 3 (libminer.so)",
                    mlMaliciousProb = 0.94,
                    anomalyScore = 0.88,
                    findingsCount = 3,
                    lastScanned = System.currentTimeMillis()
                )
                database.applicationDao().insertApplications(listOf(evilApp))

                val finding1 = FindingEntity(
                    findingId = "fnd_miner_002",
                    ruleId = "RULE-002",
                    packageName = "com.sample.miner",
                    appName = "QuickClean Optimizer",
                    category = "Abnormal Resource Consumption",
                    severity = "CRITICAL",
                    title = "Severe Cryptomining CPU Spike",
                    description = "Process 'com.sample.miner:worker' (PID 9814) is sustaining 88.5% CPU utilization across all cores.",
                    evidence = "PID=9814, CPU=88.5%, RSS=480MB, native lib libminer.so loaded",
                    remediation = "Kill process via Shizuku ADB shell and uninstall immediately.",
                    timestamp = System.currentTimeMillis()
                )
                val finding2 = FindingEntity(
                    findingId = "fnd_miner_001",
                    ruleId = "RULE-001",
                    packageName = "com.sample.miner",
                    appName = "QuickClean Optimizer",
                    category = "Persistent Background Execution",
                    severity = "HIGH",
                    title = "Unrestricted Background Persistence",
                    description = "Boot completion receiver initiates worker daemon on device reboot without user interaction.",
                    evidence = "RECEIVE_BOOT_COMPLETED granted, 3 exported services active.",
                    remediation = "Revoke autostart permissions in device settings.",
                    timestamp = System.currentTimeMillis()
                )
                val findingProc = FindingEntity(
                    findingId = "fnd_proc_9814",
                    ruleId = "PROC-ANOMALY",
                    packageName = "com.sample.miner",
                    appName = "com.sample.miner:worker",
                    category = "Privileged Process Anomaly",
                    severity = "CRITICAL",
                    title = "Suspicious Process: com.sample.miner:worker (PID 9814)",
                    description = "Cryptocurrency mining daemon consuming 88.5% CPU detected under privileged Shizuku inspection.",
                    evidence = "PID: 9814, CPU: 88.5%, RSS: 480MB, User: u0_a189, State: R",
                    remediation = "Use Shizuku shell to terminate PID 9814.",
                    timestamp = System.currentTimeMillis()
                )

                database.findingDao().insertFindings(listOf(finding1, finding2, findingProc))

                database.behaviorEventDao().insertEvent(
                    BehaviorEventEntity(
                        eventId = "evt_${System.currentTimeMillis()}_miner",
                        packageName = "com.sample.miner",
                        appName = "QuickClean Optimizer",
                        eventType = "RULE-002",
                        severity = "CRITICAL",
                        description = "Severe Cryptomining CPU Spike (88.5%)",
                        evidence = "Background CPU thread pegged at max frequency (PID 9814)",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            "SPYWARE" -> {
                val spyApp = ApplicationEntity(
                    packageName = "com.secret.tracker",
                    appName = "BatterySaver Pro",
                    versionName = "1.0.8",
                    uid = 10199,
                    apkHash = "12ab99014ffc189b2103f71c4",
                    certHash = "1A:87:C3:99:4E:02",
                    riskScore = 88,
                    riskLevel = "CRITICAL",
                    isSystemApp = false,
                    permissionsJson = "26 requested, 22 granted",
                    dangerousPermissionsJson = "ACCESS_FINE_LOCATION, RECORD_AUDIO, READ_SMS, READ_CALL_LOG, CAMERA",
                    componentsJson = "Act: 1, Svc: 3 (exp 2), Rec: 4",
                    staticMetricsJson = "Size: 4.8MB, Dex: 2, Libs: 1",
                    mlMaliciousProb = 0.91,
                    anomalyScore = 0.85,
                    findingsCount = 3,
                    lastScanned = System.currentTimeMillis()
                )
                database.applicationDao().insertApplications(listOf(spyApp))

                val spyProc = ProcessEntity(
                    pid = 7421,
                    ppid = 1,
                    user = "u0_a199",
                    processName = "com.secret.tracker:remote",
                    packageName = "com.secret.tracker",
                    cpuPercent = 14.2,
                    vszKb = 2100000L,
                    rssKb = 210000L,
                    state = "S",
                    riskScore = 85,
                    isSuspicious = true,
                    anomalyNote = "Background audio recording daemon with active C2 port 4444 socket",
                    timestamp = System.currentTimeMillis()
                )
                database.processDao().insertProcesses(listOf(spyProc))

                val finding = FindingEntity(
                    findingId = "fnd_spy_004",
                    ruleId = "RULE-004",
                    packageName = "com.secret.tracker",
                    appName = "BatterySaver Pro",
                    category = "Abnormal Network Behavior",
                    severity = "HIGH",
                    title = "Exfiltration to Suspicious Remote Port",
                    description = "TCP telemetry socket connected to 185.220.101.5:4444 exfiltrating background location and audio data.",
                    evidence = "Remote: 185.220.101.5:4444 (C2 Port), Audio & Location access active.",
                    remediation = "Disconnect network and quarantine application.",
                    timestamp = System.currentTimeMillis()
                )
                val procFinding = FindingEntity(
                    findingId = "fnd_proc_7421",
                    ruleId = "PROC-ANOMALY",
                    packageName = "com.secret.tracker",
                    appName = "com.secret.tracker:remote",
                    category = "Privileged Process Anomaly",
                    severity = "HIGH",
                    title = "Suspicious Process: com.secret.tracker:remote (PID 7421)",
                    description = "Background audio recording daemon with active C2 port 4444 socket.",
                    evidence = "PID: 7421, Port 4444 socket active, User: u0_a199",
                    remediation = "Terminate PID 7421 via Shizuku shell.",
                    timestamp = System.currentTimeMillis()
                )
                database.findingDao().insertFindings(listOf(finding, procFinding))

                database.behaviorEventDao().insertEvent(
                    BehaviorEventEntity(
                        eventId = "evt_${System.currentTimeMillis()}_spy",
                        packageName = "com.secret.tracker",
                        appName = "BatterySaver Pro",
                        eventType = "RULE-004",
                        severity = "HIGH",
                        description = "Exfiltration socket opened to port 4444 (PID 7421)",
                        evidence = "Remote C2 endpoint matched threat intelligence blacklist",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            "DEX_INJECTION" -> {
                val dropperApp = ApplicationEntity(
                    packageName = "com.fake.flashtorch",
                    appName = "Flashlight SuperBright",
                    versionName = "3.1.0",
                    uid = 10204,
                    apkHash = "998241cf01934ba81230cd7",
                    certHash = "DE:AD:BE:EF:00:11",
                    riskScore = 85,
                    riskLevel = "CRITICAL",
                    isSystemApp = false,
                    permissionsJson = "12 requested, 10 granted",
                    dangerousPermissionsJson = "SYSTEM_ALERT_WINDOW, REQUEST_INSTALL_PACKAGES",
                    componentsJson = "Act: 1, Svc: 2",
                    staticMetricsJson = "Size: 18.2MB, Dex: 5, Dynamic Code: YES",
                    mlMaliciousProb = 0.89,
                    anomalyScore = 0.82,
                    findingsCount = 2,
                    lastScanned = System.currentTimeMillis()
                )
                database.applicationDao().insertApplications(listOf(dropperApp))

                val finding = FindingEntity(
                    findingId = "fnd_dropper_005",
                    ruleId = "RULE-005",
                    packageName = "com.fake.flashtorch",
                    appName = "Flashlight SuperBright",
                    category = "Dynamic Code Loading / Dex Injection",
                    severity = "CRITICAL",
                    title = "Dynamic Secondary Payload Unpacked",
                    description = "Detected encrypted secondary DEX loaded into Dalvik runtime via DexClassLoader.",
                    evidence = "Secondary DEX container detected in /assets/payload.dex",
                    remediation = "Uninstall application; violates Google Play policy against dynamic execution.",
                    timestamp = System.currentTimeMillis()
                )
                database.findingDao().insertFindings(listOf(finding))

                database.behaviorEventDao().insertEvent(
                    BehaviorEventEntity(
                        eventId = "evt_${System.currentTimeMillis()}_dex",
                        packageName = "com.fake.flashtorch",
                        appName = "Flashlight SuperBright",
                        eventType = "RULE-005",
                        severity = "CRITICAL",
                        description = "Secondary dex payload loaded into memory",
                        evidence = "DexClassLoader invoked with assets/payload.dex",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun getSimulatedPackages(): List<PackageInfo> {
        return listOf(
            PackageInfo().apply {
                packageName = "com.android.chrome"
                versionName = "128.0.6613.88"
                applicationInfo = ApplicationInfo().apply {
                    flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_INSTALLED
                    uid = 10145
                }
            },
            PackageInfo().apply {
                packageName = "com.google.android.youtube"
                versionName = "19.34.42"
                applicationInfo = ApplicationInfo().apply {
                    flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_INSTALLED
                    uid = 10150
                }
            }
        )
    }
}
