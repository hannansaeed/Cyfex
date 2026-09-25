package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RiskLevel(val label: String, val scoreThreshold: Int) {
    SAFE("Safe", 0),
    LOW("Low Risk", 20),
    MEDIUM("Medium Risk", 45),
    HIGH("High Risk", 70),
    CRITICAL("Critical Threat", 85);

    companion object {
        fun fromScore(score: Int): RiskLevel = when {
            score >= CRITICAL.scoreThreshold -> CRITICAL
            score >= HIGH.scoreThreshold -> HIGH
            score >= MEDIUM.scoreThreshold -> MEDIUM
            score >= LOW.scoreThreshold -> LOW
            else -> SAFE
        }
    }
}

enum class ConfidenceLevel(val label: String) {
    HIGH("High Confidence"),
    MEDIUM("Medium Confidence"),
    LOW("Low Confidence")
}

enum class SignalCategory(val displayName: String) {
    APK_STRUCTURE("APK Structure"),
    CODE_ANALYSIS("DEX & Code Analysis"),
    INTEGRITY_SIGNATURE("Integrity & Signature"),
    RUNTIME_BEHAVIOR("Runtime Behavior"),
    NETWORK_BEHAVIOR("Network Behavior"),
    DYNAMIC_CODE_LOADING("Dynamic Code Loading"),
    REPUTATION_CORRELATION("Reputation & Heuristics")
}

data class ThreatSignal(
    val category: SignalCategory,
    val name: String,
    val evidence: String,
    val weight: Int,
    val isViolation: Boolean,
    val isInformational: Boolean = false,
    val description: String = ""
)

data class ScoringStep(
    val stepNumber: Int,
    val phase: String,
    val finding: String,
    val weightDelta: Int,
    val runningScore: Int,
    val note: String
)

data class ComprehensiveRiskEvaluation(
    val totalScore: Int,
    val riskLevel: RiskLevel,
    val confidenceLevel: ConfidenceLevel,
    val reasoning: String,
    val signals: List<ThreatSignal>,
    val scoringChain: List<ScoringStep>,
    val executableOrigin: String,
    val signerOrganization: String,
    val categoryBreakdown: Map<SignalCategory, CategoryStatus>
)

data class CategoryStatus(
    val statusText: String,
    val isClean: Boolean,
    val violationsCount: Int
)

data class DeviceTelemetryInfo(
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val kernelVersion: String,
    val buildId: String,
    val securityPatchLevel: String,
    val isWirelessDebuggingSupported: Boolean,
    val isShizukuInstalled: Boolean,
    val isShizukuRunning: Boolean,
    val isShizukuGranted: Boolean
)

data class ProcessRecord(
    val pid: Int,
    val ppid: Int,
    val user: String,
    val processName: String,
    val packageName: String,
    val cpuPercent: Double,
    val vszKb: Long,
    val rssKb: Long,
    val state: String,
    val timestamp: Long = System.currentTimeMillis(),
    val riskScore: Int = 0,
    val isSuspicious: Boolean = false,
    val anomalyNote: String? = null
)

data class AppComponentInfo(
    val activitiesCount: Int,
    val servicesCount: Int,
    val receiversCount: Int,
    val providersCount: Int,
    val exportedActivities: Int,
    val exportedServices: Int,
    val exportedReceivers: Int,
    val exportedProviders: Int
)

data class AppStaticMetrics(
    val apkSizeMb: Double,
    val sha256Hash: String,
    val signingCertHash: String,
    val signerSubject: String = "CN=Android, O=Developer",
    val signerOrganization: String = "Verified Developer",
    val dexCount: Int,
    val nativeLibsCount: Int,
    val nativeLibNames: List<String> = emptyList(),
    val isDebuggable: Boolean,
    val minSdk: Int,
    val targetSdk: Int,
    val hasDynamicCodeLoadingIndicators: Boolean = false,
    val hasReflectionIndicators: Boolean = false,
    val hasSuspiciousUrls: Boolean = false,
    val hasObfuscationMarkers: Boolean = false,
    val obfuscationType: String = "R8/ProGuard Standard",
    val dclRiskScore: Int = 0,
    val dclMagicHeaderValid: Boolean = true,
    val dclEntropyScore: Double = 0.0,
    val dclExternalStorageRef: Boolean = false,
    val dclIsEncryptedOrPacked: Boolean = false,
    val dclDetails: String = "",
    val executableOrigin: String = "Embedded in APK"
)

data class AppSecurityTelemetry(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val uid: Int,
    val isSystemApp: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val firstSeenTime: Long = installTime,
    val staticMetrics: AppStaticMetrics,
    val components: AppComponentInfo,
    val requestedPermissions: List<String>,
    val grantedPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val overallRiskScore: Int,
    val riskLevel: RiskLevel,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.HIGH,
    val staticScore: Int,
    val runtimeScore: Int,
    val networkScore: Int,
    val anomalyScore: Double,
    val mlMaliciousProb: Double,
    val findingsCount: Int = 0,
    val baselineDeviation: Double = 0.0,
    val reasoning: String = "",
    val executableOrigin: String = "Embedded in APK"
) {
    val formattedInstallTime: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(installTime))

    val formattedUpdateTime: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(updateTime))
}

data class AppLiveUsage(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val cpuPercent: Double,
    val ramRssMb: Double,
    val ramVszMb: Double,
    val estimatedBatteryPerHour: Double, // Estimated %/hr drain
    val networkSpeedKbps: Double,
    val totalNetworkRxBytes: Long,
    val totalNetworkTxBytes: Long,
    val requestedPermissions: List<String>,
    val grantedPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val activePids: List<Int>,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetworkConnectionRecord(
    val protocol: String,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val state: String,
    val uid: Int,
    val packageName: String,
    val isBackgroundTraffic: Boolean = true,
    val isSuspiciousEndpoint: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val localIp: String get() = localAddress
    val remoteIp: String get() = remoteAddress
}

data class ThreatFindingItem(
    val id: String,
    val ruleId: String,
    val packageName: String,
    val appName: String,
    val category: String,
    val severity: RiskLevel,
    val title: String,
    val description: String,
    val evidence: String,
    val timestamp: Long,
    val remediationSuggestion: String
)

data class BehavioralEventItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val eventType: String,
    val severity: RiskLevel,
    val description: String,
    val evidence: String = "",
    val telemetryEvidence: String = evidence,
    val timestamp: Long = System.currentTimeMillis()
)

data class BaselineMetrics(
    val packageName: String,
    val avgCpuPercent: Double,
    val maxCpuPercent: Double,
    val avgMemoryMb: Double,
    val maxMemoryMb: Double,
    val avgConnections: Double,
    val sampleCount: Int
)

data class SecurityScanSummary(
    val scanId: String,
    val timestamp: Long,
    val durationMs: Long,
    val totalAppsScanned: Int,
    val highRiskAppsCount: Int,
    val suspiciousProcessesCount: Int,
    val activeFindingsCount: Int,
    val overallDeviceRiskScore: Int,
    val overallRiskLevel: RiskLevel,
    val isShizukuPrivileged: Boolean,
    val staticAnalysisWeight: Int,
    val runtimeBehaviorWeight: Int,
    val networkBehaviorWeight: Int,
    val anomalyWeight: Int,
    val ruleFindingsWeight: Int
)

data class RiskScoreBreakdown(
    val staticScore: Int,
    val runtimeScore: Int,
    val networkScore: Int,
    val anomalyScore: Int,
    val ruleScore: Int,
    val totalScore: Int,
    val riskLevel: RiskLevel
)
