package com.example.risk

import com.example.data.model.AppSecurityTelemetry
import com.example.data.model.NetworkConnectionRecord
import com.example.data.model.ProcessRecord
import com.example.data.model.RiskLevel
import com.example.rules.AppSecurityWhitelist
import com.example.rules.RuleEvaluationResult

data class RiskBreakdown(
    val staticScore: Int,      // Max 25
    val runtimeScore: Int,     // Max 30
    val networkScore: Int,     // Max 20
    val anomalyScore: Int,     // Max 15
    val ruleFindingsScore: Int,// Max 10
    val totalScore: Int,       // 0 - 100
    val riskLevel: RiskLevel,
    val explanations: List<String>
)

class RiskEngine {

    fun calculateRisk(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>,
        ruleResults: List<RuleEvaluationResult>,
        rfMaliciousProb: Double,
        ifAnomalyScore: Double
    ): RiskBreakdown {
        val appProcesses = processes.filter { it.packageName == app.packageName }
        val appConnections = network.filter { it.packageName == app.packageName || it.uid == app.uid }
        val isTrusted = app.isSystemApp || AppSecurityWhitelist.isWellKnownTrustedApp(app.packageName, app.appName)

        val triggeredRules = ruleResults.filter { it.triggered }

        // If it's a verified trusted app (WhatsApp, Spotify, YouTube, Chrome, etc.) and no malicious rules are triggered:
        if (isTrusted && triggeredRules.isEmpty()) {
            return RiskBreakdown(
                staticScore = 2,
                runtimeScore = 2,
                networkScore = 1,
                anomalyScore = 0,
                ruleFindingsScore = 0,
                totalScore = 5,
                riskLevel = RiskLevel.SAFE,
                explanations = listOf("[SAFE] Verified publisher application operating within normal mobile profile.")
            )
        }

        // 1. Static Analysis (0 - 25)
        var static = 0
        if (!isTrusted) {
            if (app.dangerousPermissions.size >= 8) static += 8
            else if (app.dangerousPermissions.size >= 4) static += 4

            if (app.components.exportedServices > 2) static += 5
            if (app.staticMetrics.hasDynamicCodeLoadingIndicators) static += 12
        }
        static = static.coerceIn(0, 25)

        // 2. Runtime Behavior (0 - 30)
        var runtime = 0
        val maxCpu = appProcesses.maxOfOrNull { it.cpuPercent } ?: 0.0

        if (maxCpu > 70.0) runtime += 25
        else if (maxCpu > 45.0 && !isTrusted) runtime += 12
        else if (maxCpu > 25.0 && !isTrusted) runtime += 5
        runtime = runtime.coerceIn(0, 30)

        // 3. Network Behavior (0 - 20)
        var net = 0
        val hasSuspiciousPorts = appConnections.any { it.isSuspiciousEndpoint }
        if (hasSuspiciousPorts) {
            net += 20
        }
        net = net.coerceIn(0, 20)

        // 4. Anomaly Detection (0 - 15)
        var anomaly = 0
        if (!isTrusted) {
            val combinedMl = (rfMaliciousProb * 0.6 + ifAnomalyScore * 0.4)
            anomaly = (combinedMl * 15.0).toInt().coerceIn(0, 15)
        }

        // 5. Rule Findings (0 - 10)
        val rulesScore = (triggeredRules.size * 5).coerceIn(0, 10)

        var total = (static + runtime + net + anomaly + rulesScore).coerceIn(0, 100)

        // Sanity Check: If NO rules were triggered and no suspicious ports/payloads found, cap at LOW (max 22)
        if (triggeredRules.isEmpty() && !hasSuspiciousPorts && maxCpu < 60.0) {
            total = total.coerceAtMost(22)
        }

        val level = RiskLevel.fromScore(total)

        // Explanations
        val explanations = mutableListOf<String>()
        triggeredRules.forEach { rule ->
            when (rule.ruleId) {
                "RULE-001" -> explanations.add("[HIGH] Persistent background execution without user notification")
                "RULE-002" -> explanations.add("[CRITICAL] Severe CPU anomaly (${"%.1f".format(maxCpu)}% sustained utilization)")
                "RULE-003" -> explanations.add("[MEDIUM] Unprotected exported IPC services")
                "RULE-004" -> explanations.add("[CRITICAL] Outbound connection to suspicious remote C2 port")
                "RULE-005" -> explanations.add("[CRITICAL] Dynamic code loading / secondary executable DEX detected")
            }
        }

        if (explanations.isEmpty()) {
            explanations.add("[SAFE] Operating within standard expected behavioral boundaries.")
        }

        return RiskBreakdown(
            staticScore = static,
            runtimeScore = runtime,
            networkScore = net,
            anomalyScore = anomaly,
            ruleFindingsScore = rulesScore,
            totalScore = total,
            riskLevel = level,
            explanations = explanations
        )
    }
}
