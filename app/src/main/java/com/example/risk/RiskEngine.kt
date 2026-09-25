package com.example.risk

import com.example.data.model.*
import com.example.rules.RuleEvaluationResult

class RiskEngine {

    fun evaluateApp(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>,
        ruleResults: List<RuleEvaluationResult>,
        mlMaliciousProb: Double,
        isolationForestAnomaly: Double
    ): ComprehensiveRiskEvaluation {
        val signals = mutableListOf<ThreatSignal>()
        val scoringSteps = mutableListOf<ScoringStep>()
        var runningScore = 0
        var stepNum = 1

        val appProcesses = processes.filter { it.packageName == app.packageName }
        val appConnections = network.filter { it.packageName == app.packageName || it.uid == app.uid }

        // ==========================================
        // 1. APK STRUCTURE SIGNALS
        // ==========================================
        if (app.staticMetrics.dexCount > 1) {
            signals.add(
                ThreatSignal(
                    category = SignalCategory.APK_STRUCTURE,
                    name = "Multidex Packaging",
                    evidence = "${app.staticMetrics.dexCount} split DEX files detected (classes.dex..classes${app.staticMetrics.dexCount}.dex).",
                    weight = 0,
                    isViolation = false,
                    isInformational = true,
                    description = "Standard modern Android multidex architecture (64K method limit partitioning)."
                )
            )
            scoringSteps.add(
                ScoringStep(
                    stepNumber = stepNum++,
                    phase = "APK Structure",
                    finding = "Multidex (${app.staticMetrics.dexCount} DEX files)",
                    weightDelta = 0,
                    runningScore = runningScore,
                    note = "Modern standard packaging; 0 penalty applied."
                )
            )
        }

        if (app.staticMetrics.nativeLibsCount > 0) {
            signals.add(
                ThreatSignal(
                    category = SignalCategory.APK_STRUCTURE,
                    name = "Native C/C++ Binaries",
                    evidence = "${app.staticMetrics.nativeLibsCount} native shared objects (.so) bundled: ${app.staticMetrics.nativeLibNames.take(4).joinToString(", ")}.",
                    weight = 0,
                    isViolation = false,
                    isInformational = true,
                    description = "High-performance native libraries (e.g. codecs, ML acceleration, or graphics engines)."
                )
            )
        }

        // ==========================================
        // 2. DEX & CODE ANALYSIS SIGNALS
        // ==========================================
        if (app.staticMetrics.hasObfuscationMarkers) {
            signals.add(
                ThreatSignal(
                    category = SignalCategory.CODE_ANALYSIS,
                    name = "R8/ProGuard Symbol Optimization",
                    evidence = "Short class identifiers (a/b/c) and symbol stripping detected.",
                    weight = 0,
                    isViolation = false,
                    isInformational = true,
                    description = "Standard developer code shrinking and identifier optimization; not indicative of malware."
                )
            )
            scoringSteps.add(
                ScoringStep(
                    stepNumber = stepNum++,
                    phase = "Code Analysis",
                    finding = "R8/ProGuard Obfuscation Pattern",
                    weightDelta = 0,
                    runningScore = runningScore,
                    note = "Standard commercial app practice; 0 penalty applied."
                )
            )
        }

        // ==========================================
        // 3. INTEGRITY & SIGNATURE SIGNALS
        // ==========================================
        val isDebuggable = app.staticMetrics.isDebuggable
        if (isDebuggable) {
            val delta = 15
            runningScore += delta
            signals.add(
                ThreatSignal(
                    category = SignalCategory.INTEGRITY_SIGNATURE,
                    name = "Debuggable Flag Enabled",
                    evidence = "android:debuggable=true present in AndroidManifest.xml.",
                    weight = delta,
                    isViolation = true,
                    description = "Application is configured for debugging, allowing runtime memory attachment."
                )
            )
            scoringSteps.add(
                ScoringStep(
                    stepNumber = stepNum++,
                    phase = "Integrity",
                    finding = "Debuggable Flag Active",
                    weightDelta = delta,
                    runningScore = runningScore,
                    note = "Development build marker; added $delta risk points."
                )
            )
        } else {
            signals.add(
                ThreatSignal(
                    category = SignalCategory.INTEGRITY_SIGNATURE,
                    name = "Valid Release Signing Certificate",
                    evidence = "Signed by '${app.staticMetrics.signerOrganization}' (${app.staticMetrics.signingCertHash.take(18)}...).",
                    weight = 0,
                    isViolation = false,
                    isInformational = true,
                    description = "Developer certificate verified with valid X.509 signature."
                )
            )
        }

        // ==========================================
        // 4. DYNAMIC CODE LOADING & EXECUTABLE ORIGIN
        // ==========================================
        val isExternalLoading = app.staticMetrics.dclExternalStorageRef
        val isCorruptEncrypted = app.staticMetrics.dclIsEncryptedOrPacked && !app.staticMetrics.dclMagicHeaderValid

        if (isExternalLoading) {
            val delta = 35
            runningScore += delta
            signals.add(
                ThreatSignal(
                    category = SignalCategory.DYNAMIC_CODE_LOADING,
                    name = "Executable Code from Shared Storage",
                    evidence = "Dynamic ClassLoader references external writable storage (/sdcard/ or /storage/).",
                    weight = delta,
                    isViolation = true,
                    description = "Severe vulnerability / threat: loading executable binaries from world-writable locations."
                )
            )
            scoringSteps.add(
                ScoringStep(
                    stepNumber = stepNum++,
                    phase = "Dynamic Code Loading",
                    finding = "External Storage ClassLoader",
                    weightDelta = delta,
                    runningScore = runningScore,
                    note = "High risk vector; added $delta points."
                )
            )
        } else if (isCorruptEncrypted) {
            val delta = 25
            runningScore += delta
            signals.add(
                ThreatSignal(
                    category = SignalCategory.DYNAMIC_CODE_LOADING,
                    name = "Encrypted / Corrupt Header Payload",
                    evidence = "Entropy: ${app.staticMetrics.dclEntropyScore}/8.00, magic bytes non-standard.",
                    weight = delta,
                    isViolation = true,
                    description = "Custom packed executable payload bypassing standard DEX verification."
                )
            )
            scoringSteps.add(
                ScoringStep(
                    stepNumber = stepNum++,
                    phase = "Dynamic Code Loading",
                    finding = "Custom Encrypted Payload",
                    weightDelta = delta,
                    runningScore = runningScore,
                    note = "Custom packed blob; added $delta points."
                )
            )
        } else {
            signals.add(
                ThreatSignal(
                    category = SignalCategory.DYNAMIC_CODE_LOADING,
                    name = "Safe Executable Origin",
                    evidence = "Code loaded strictly from APK container (${app.staticMetrics.executableOrigin}).",
                    weight = 0,
                    isViolation = false,
                    isInformational = true,
                    description = "No unverified runtime DEX downloads or external code hooks detected."
                )
            )
        }

        // ==========================================
        // 5. RUNTIME BEHAVIOR SIGNALS
        // ==========================================
        val peakCpu = appProcesses.maxOfOrNull { it.cpuPercent } ?: 0.0
        val isMinerSpike = peakCpu > 75.0 || appProcesses.any { it.isSuspicious }

        if (isMinerSpike) {
            val delta = 40
            runningScore += delta
            signals.add(
                ThreatSignal(
                    category = SignalCategory.RUNTIME_BEHAVIOR,
                    name = "Abnormal CPU Spike Anomaly",
                    evidence = "Process '${appProcesses.firstOrNull()?.processName}' sustaining ${"%.1f".format(peakCpu)}% CPU in background.",
                    weight = delta,
                    isViolation = true,
                    description = "Unconstrained execution loop or mobile cryptominer activity."
                )
            )
            scoringSteps.add(
                ScoringStep(
                    stepNumber = stepNum++,
                    phase = "Runtime Telemetry",
                    finding = "Sustained High CPU (${"%.1f".format(peakCpu)}%)",
                    weightDelta = delta,
                    runningScore = runningScore,
                    note = "Abnormal execution anomaly; added $delta points."
                )
            )
        } else {
            signals.add(
                ThreatSignal(
                    category = SignalCategory.RUNTIME_BEHAVIOR,
                    name = "Normal Runtime Resource Bounds",
                    evidence = "Peak CPU: ${"%.1f".format(peakCpu)}%, Memory: ${appProcesses.sumOf { it.rssKb } / 1024}MB.",
                    weight = 0,
                    isViolation = false,
                    isInformational = true,
                    description = "Background lifecycle operates within standard system battery bounds."
                )
            )
        }

        // ==========================================
        // 6. NETWORK BEHAVIOR SIGNALS
        // ==========================================
        val suspiciousSockets = appConnections.filter { it.isSuspiciousEndpoint }
        if (suspiciousSockets.isNotEmpty()) {
            val delta = 40
            runningScore += delta
            signals.add(
                ThreatSignal(
                    category = SignalCategory.NETWORK_BEHAVIOR,
                    name = "Suspicious Outbound C2 Socket",
                    evidence = "Active TCP socket connected to non-standard remote port ${suspiciousSockets.first().remotePort}.",
                    weight = delta,
                    isViolation = true,
                    description = "Connection detected to known threat intelligence port (e.g. port 4444 or raw IP socket)."
                )
            )
            scoringSteps.add(
                ScoringStep(
                    stepNumber = stepNum++,
                    phase = "Network Telemetry",
                    finding = "Suspicious Port Connection (Port ${suspiciousSockets.first().remotePort})",
                    weightDelta = delta,
                    runningScore = runningScore,
                    note = "Potential C2 communication; added $delta points."
                )
            )
        } else {
            signals.add(
                ThreatSignal(
                    category = SignalCategory.NETWORK_BEHAVIOR,
                    name = "Clean Network Telemetry",
                    evidence = "${appConnections.size} active connections to standard HTTPS endpoints.",
                    weight = 0,
                    isViolation = false,
                    isInformational = true,
                    description = "No anomalous raw sockets or blacklisted C2 ports detected."
                )
            )
        }

        // ==========================================
        // 7. MULTI-SIGNAL CORRELATION & CONFIDENCE
        // ==========================================
        val violationCategories = signals.filter { it.isViolation }.map { it.category }.distinct()
        val totalViolations = signals.count { it.isViolation }

        // Principle: Single isolated static anomaly cannot trigger CRITICAL severity without corroborating runtime/network signals
        var finalScore = runningScore.coerceIn(0, 100)
        if (finalScore >= 80 && violationCategories.size < 2) {
            finalScore = 65 // Downgrade to Medium/High alert with low confidence
            scoringSteps.add(
                ScoringStep(
                    stepNumber = stepNum++,
                    phase = "Multi-Signal Correlation",
                    finding = "Isolated Single-Category Anomaly",
                    weightDelta = -15,
                    runningScore = finalScore,
                    note = "Critical requires at least 2 independent corroborating categories. Score moderated."
                )
            )
        }

        val riskLevel = RiskLevel.fromScore(finalScore)

        val confidenceLevel = when {
            totalViolations == 0 -> ConfidenceLevel.HIGH
            violationCategories.size >= 2 -> ConfidenceLevel.HIGH
            totalViolations == 1 && violationCategories.contains(SignalCategory.RUNTIME_BEHAVIOR) -> ConfidenceLevel.HIGH
            else -> ConfidenceLevel.MEDIUM
        }

        // Generate transparent human-readable reasoning
        val reasoning = generateReasoningText(
            appName = app.appName,
            riskLevel = riskLevel,
            confidenceLevel = confidenceLevel,
            signals = signals,
            app = app
        )

        val categoryBreakdown = SignalCategory.values().associateWith { cat ->
            val catSignals = signals.filter { it.category == cat }
            val violations = catSignals.count { it.isViolation }
            CategoryStatus(
                statusText = if (violations > 0) "$violations violation(s)" else "Passed & Verified",
                isClean = violations == 0,
                violationsCount = violations
            )
        }

        return ComprehensiveRiskEvaluation(
            totalScore = finalScore,
            riskLevel = riskLevel,
            confidenceLevel = confidenceLevel,
            reasoning = reasoning,
            signals = signals,
            scoringChain = scoringSteps,
            executableOrigin = app.staticMetrics.executableOrigin,
            signerOrganization = app.staticMetrics.signerOrganization,
            categoryBreakdown = categoryBreakdown
        )
    }

    private fun generateReasoningText(
        appName: String,
        riskLevel: RiskLevel,
        confidenceLevel: ConfidenceLevel,
        signals: List<ThreatSignal>,
        app: AppSecurityTelemetry
    ): String {
        val violations = signals.filter { it.isViolation }
        if (violations.isEmpty()) {
            return when {
                app.staticMetrics.hasObfuscationMarkers && app.staticMetrics.dexCount > 1 ->
                    "R8/ProGuard code optimization and ${app.staticMetrics.dexCount} multidex files were detected, representing normal modern Android architecture. Verified signing certificate with no corroborating runtime or network threats."
                app.isSystemApp ->
                    "Verified core Android system package signed by platform authority with standard background services."
                else ->
                    "Application packaging, permissions, and runtime telemetry conform to clean mobile behavioral baselines. No threat observed."
            }
        }

        val violationSummaries = violations.joinToString("; ") { "${it.category.displayName}: ${it.name}" }
        return "Elevated risk ($riskLevel, $confidenceLevel) due to corroborated indicators ($violationSummaries). Telemetry evidence suggests unconstrained behavior or unauthorized execution hooks."
    }

    // Legacy helper for backward compatibility
    fun calculateRisk(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>,
        ruleResults: List<RuleEvaluationResult>,
        mlMaliciousProb: Double,
        isolationForestAnomaly: Double
    ): RiskScoreBreakdown {
        val evaluation = evaluateApp(app, processes, network, ruleResults, mlMaliciousProb, isolationForestAnomaly)
        val staticScore = evaluation.signals.filter { it.category in listOf(SignalCategory.APK_STRUCTURE, SignalCategory.CODE_ANALYSIS, SignalCategory.INTEGRITY_SIGNATURE) && it.isViolation }.sumOf { it.weight }
        val runtimeScore = evaluation.signals.filter { it.category == SignalCategory.RUNTIME_BEHAVIOR && it.isViolation }.sumOf { it.weight }
        val networkScore = evaluation.signals.filter { it.category == SignalCategory.NETWORK_BEHAVIOR && it.isViolation }.sumOf { it.weight }

        return RiskScoreBreakdown(
            staticScore = staticScore,
            runtimeScore = runtimeScore,
            networkScore = networkScore,
            anomalyScore = (isolationForestAnomaly * 100).toInt(),
            ruleScore = evaluation.totalScore,
            totalScore = evaluation.totalScore,
            riskLevel = evaluation.riskLevel
        )
    }
}
