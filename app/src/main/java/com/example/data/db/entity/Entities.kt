package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val securityPatch: String,
    val lastScanned: Long
)

@Entity(tableName = "applications")
data class ApplicationEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String,
    val uid: Int,
    val apkHash: String,
    val certHash: String,
    val signerOrganization: String = "Verified Developer",
    val executableOrigin: String = "Embedded in APK",
    val riskScore: Int,
    val riskLevel: String,
    val confidenceLevel: String = "HIGH",
    val riskReasoning: String = "Normal Android application packaging.",
    val scoringChainJson: String = "",
    val signalsBreakdownJson: String = "",
    val isSystemApp: Boolean,
    val installTime: Long = 0L,
    val updateTime: Long = 0L,
    val firstSeenTime: Long = 0L,
    val permissionsJson: String,
    val dangerousPermissionsJson: String,
    val requestedPermissionsList: String = "",
    val grantedPermissionsList: String = "",
    val componentsJson: String,
    val staticMetricsJson: String,
    val mlMaliciousProb: Double,
    val anomalyScore: Double,
    val findingsCount: Int,
    val lastScanned: Long
)

@Entity(tableName = "processes")
data class ProcessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pid: Int,
    val ppid: Int,
    val user: String,
    val processName: String,
    val packageName: String,
    val cpuPercent: Double,
    val vszKb: Long,
    val rssKb: Long,
    val state: String,
    val riskScore: Int,
    val isSuspicious: Boolean,
    val anomalyNote: String?,
    val timestamp: Long
)

@Entity(tableName = "behavior_events")
data class BehaviorEventEntity(
    @PrimaryKey val eventId: String,
    val packageName: String,
    val appName: String,
    val eventType: String,
    val severity: String,
    val description: String,
    val evidence: String,
    val timestamp: Long
)

@Entity(tableName = "findings")
data class FindingEntity(
    @PrimaryKey val findingId: String,
    val ruleId: String,
    val packageName: String,
    val appName: String,
    val category: String,
    val severity: String,
    val title: String,
    val description: String,
    val evidence: String,
    val remediation: String,
    val timestamp: Long
)

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey val scanId: String,
    val deviceId: String,
    val timestamp: Long,
    val durationMs: Long,
    val totalAppsScanned: Int,
    val highRiskAppsCount: Int,
    val suspiciousProcessesCount: Int,
    val activeFindingsCount: Int,
    val overallRiskScore: Int,
    val overallRiskLevel: String,
    val isShizukuPrivileged: Boolean,
    val breakdownJson: String
)

@Entity(tableName = "baselines")
data class BaselineEntity(
    @PrimaryKey val packageName: String,
    val avgCpuPercent: Double,
    val maxCpuPercent: Double,
    val avgMemoryMb: Double,
    val processSpawnRate: Double,
    val networkFreq: Double,
    val sampleCount: Int,
    val lastUpdated: Long
)

@Entity(tableName = "feature_vectors")
data class FeatureVectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val featuresJson: String,
    val timestamp: Long
)

@Entity(tableName = "rule_results")
data class RuleResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: String,
    val packageName: String,
    val triggered: Boolean,
    val scoreImpact: Int,
    val details: String,
    val timestamp: Long
)

@Entity(tableName = "sensor_access_events")
data class SensorAccessEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val resourceType: String, // "CAMERA", "MICROPHONE", "LOCATION", "CONTACTS", "STORAGE", "BACKGROUND_NETWORK"
    val accessCount: Int = 1,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "monitoring_session")
data class MonitoringSessionEntity(
    @PrimaryKey val id: String = "active_session",
    val startTime: Long = System.currentTimeMillis(),
    val isRunning: Boolean = false,
    val totalEventsLogged: Int = 0
)
