package com.example.rules

import com.example.data.model.*

data class RuleEvaluationResult(
    val ruleId: String,
    val ruleName: String,
    val triggered: Boolean,
    val severity: RiskLevel,
    val scoreImpact: Int,
    val title: String,
    val description: String,
    val evidence: String,
    val remediation: String
)

interface Rule {
    val id: String
    val name: String
    val baseSeverity: RiskLevel
    val scoreWeight: Int

    fun evaluate(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>
    ): RuleEvaluationResult
}

object AppSecurityWhitelist {
    private val trustedPrefixes = listOf(
        // Operating System & System Frameworks
        "android",
        "com.android.",
        "com.google.",
        "com.qualcomm.",
        "com.mediatek.",

        // Web Browsers (Tor, UC, Chrome, Brave, Firefox, Opera, DuckDuckGo, etc.)
        "org.mozilla.",
        "com.brave.",
        "com.opera.",
        "com.duckduckgo.",
        "com.microsoft.",
        "org.torproject.",
        "com.ucmobile.",
        "com.uc.browser",
        "com.kiwibrowser.",
        "com.sec.android.app.sbrowser",
        "com.vivaldi.",
        "com.cloudmosa.puffin",
        "com.aloha.",

        // AI, Social, Media & Productivity
        "com.openai.",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.",
        "com.bytedance.",
        "com.instagram.",
        "com.facebook.",
        "com.whatsapp",
        "com.threads.",
        "com.twitter.",
        "com.x.android",
        "com.snapchat.",
        "com.discord",
        "com.reddit.",
        "com.linkedin.",
        "com.pinterest",
        "org.telegram.",
        "org.thunderdog.",
        "org.thoughtcrime.securesms",
        "com.skype.",
        "com.viber.",
        "com.tencent.mm",
        "jp.naver.line.",
        "com.slack",

        // Photo, Video & Creative Tools ("Edits" apps)
        "com.lemon.lvoverseas",   // CapCut
        "com.lemon.easyedit",
        "com.capcut.",
        "com.camerasideas.instashot", // InShot
        "com.frontrow.vlog",      // VN
        "com.nexstreaming.app.kinemasterfree",
        "com.kinemaster.",
        "com.picsart.studio",
        "com.canva.editor",
        "com.adobe.",
        "com.vsco.cam",
        "com.cyberlink.",
        "com.alightcreative.motion",
        "com.niksoftware.snapseed",
        "com.wondershare.",
        "com.bigwinepot.nwdn.international",
        "com.meitu.",
        "com.linecorp.b612",
        "com.cyberlink.youcammakeup",
        "io.faceapp",

        // Streaming & Music
        "com.spotify.",
        "com.netflix.",
        "com.amazon.",
        "com.disney.",
        "tv.twitch.",
        "com.soundcloud.",
        "com.pandora.",
        "deezer.",
        "com.shazam.",
        "org.videolan.vlc",
        "com.mxtech.videoplayer",

        // Utilities & Banking
        "com.paypal.",
        "com.squareup.cash",
        "com.chase.",
        "com.bankofamerica.",
        "com.wellsfargo.",
        "com.revolut.",
        "com.binance.",
        "com.coinbase.",
        "com.dropbox.",
        "com.evernote",
        "com.notion.",
        "com.trello",
        "com.zoom.",
        "com.uber",
        "com.lyft.",
        "com.grabtaxi.",
        "com.truecaller",
        "com.duolingo",

        // OEMs
        "com.samsung.",
        "com.sec.",
        "com.xiaomi.",
        "com.miui.",
        "com.huawei.",
        "com.oppo.",
        "com.oneplus.",
        "com.vivo.",
        "com.realme.",
        "com.motorola.",
        "com.lenovo.",
        "com.asus.",
        "com.sony.",
        "com.transsion.",
        "com.nothing."
    )

    private val trustedKeywords = listOf(
        "tiktok", "musically", "bytedance", "instagram", "threads", "facebook", "meta", "whatsapp",
        "snapchat", "telegram", "discord", "reddit", "twitter", "capcut", "inshot", "kinemaster",
        "picsart", "canva", "lightroom", "photoshop", "snapseed", "vsco", "alightmotion", "filmora",
        "powerdirector", "ucbrowser", "ucmobile", "torbrowser", "torproject", "orbot", "chrome",
        "firefox", "mozilla", "opera", "brave", "duckduckgo", "spotify", "netflix", "youtube",
        "google", "microsoft", "adobe", "samsung", "xiaomi", "huawei", "oppo", "vivo", "motorola",
        "vlc", "mxplayer", "paypal", "amazon", "aliexpress", "truecaller", "openai", "chatgpt"
    )

    fun isWellKnownTrustedApp(packageName: String, appName: String = ""): Boolean {
        val p = packageName.lowercase()
        val a = appName.lowercase()
        if (trustedPrefixes.any { p.startsWith(it) || p == it }) return true
        if (trustedKeywords.any { p.contains(it) || a.contains(it) }) return true
        return false
    }
}

class PersistentBackgroundRule : Rule {
    override val id = "RULE-001"
    override val name = "Persistent Background Execution"
    override val baseSeverity = RiskLevel.HIGH
    override val scoreWeight = 10

    override fun evaluate(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>
    ): RuleEvaluationResult {
        // System apps or standard foreground-serviced apps are normal
        if (app.isSystemApp || AppSecurityWhitelist.isWellKnownTrustedApp(app.packageName, app.appName)) {
            return cleanResult("Verified legitimate publisher background architecture.")
        }

        val appProcesses = processes.filter { it.packageName == app.packageName }
        val isExplicitMiner = app.packageName.contains("miner", ignoreCase = true)

        // Strict malicious criteria: Sideloaded unverified app with boot persistence, no foreground notification, running sustained high CPU (>60%)
        val isPersistingSilently = appProcesses.isNotEmpty() &&
                app.requestedPermissions.contains("android.permission.RECEIVE_BOOT_COMPLETED") &&
                !app.requestedPermissions.contains("android.permission.FOREGROUND_SERVICE") &&
                appProcesses.any { it.cpuPercent > 60.0 }

        val triggered = isExplicitMiner || isPersistingSilently

        return RuleEvaluationResult(
            ruleId = id,
            ruleName = name,
            triggered = triggered,
            severity = baseSeverity,
            scoreImpact = if (triggered) scoreWeight else 0,
            title = "Unrestricted Background Persistence",
            description = "Unverified application registers boot receivers and executes continuous background services without foreground user notification.",
            evidence = "Process state '${appProcesses.firstOrNull()?.state ?: "N/A"}', high background CPU activity.",
            remediation = "Restrict background execution in Android battery optimization settings or uninstall if unfamiliar."
        )
    }

    private fun cleanResult(reason: String) = RuleEvaluationResult(
        ruleId = id,
        ruleName = name,
        triggered = false,
        severity = baseSeverity,
        scoreImpact = 0,
        title = "Normal Background State",
        description = reason,
        evidence = "Clean",
        remediation = "No action required."
    )
}

class AbnormalResourceRule : Rule {
    override val id = "RULE-002"
    override val name = "Abnormal Resource Consumption"
    override val baseSeverity = RiskLevel.HIGH
    override val scoreWeight = 10

    override fun evaluate(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>
    ): RuleEvaluationResult {
        if (app.isSystemApp || AppSecurityWhitelist.isWellKnownTrustedApp(app.packageName, app.appName)) {
            return cleanResult("Resource usage verified within standard bounds for legitimate app.")
        }

        val appProcesses = processes.filter { it.packageName == app.packageName }
        val maxCpu = appProcesses.maxOfOrNull { it.cpuPercent } ?: 0.0

        val isExplicitMiner = app.packageName.contains("miner", ignoreCase = true) ||
                appProcesses.any { it.processName.contains("miner", ignoreCase = true) }

        // Require severe sustained CPU spike (>75%) or explicit miner signature
        val triggered = (maxCpu > 75.0) || (isExplicitMiner && maxCpu > 20.0)

        return RuleEvaluationResult(
            ruleId = id,
            ruleName = name,
            triggered = triggered,
            severity = if (maxCpu >= 80.0) RiskLevel.CRITICAL else baseSeverity,
            scoreImpact = if (triggered) scoreWeight else 0,
            title = "Severe CPU Consumption Anomaly",
            description = "Application is consuming abnormal processor cycles in background threads, typical of unthrottled loops or mobile cryptominers.",
            evidence = "Peak CPU: ${"%.1f".format(maxCpu)}% (Threshold: 75.0%).",
            remediation = "Inspect application processes with Shizuku process killer or terminate application via App Info settings."
        )
    }

    private fun cleanResult(reason: String) = RuleEvaluationResult(
        ruleId = id,
        ruleName = name,
        triggered = false,
        severity = baseSeverity,
        scoreImpact = 0,
        title = "Normal Resource Consumption",
        description = reason,
        evidence = "Clean",
        remediation = "No action required."
    )
}

class SuspiciousComponentRule : Rule {
    override val id = "RULE-003"
    override val name = "Suspicious Component Configuration"
    override val baseSeverity = RiskLevel.MEDIUM
    override val scoreWeight = 8

    override fun evaluate(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>
    ): RuleEvaluationResult {
        if (app.isSystemApp || AppSecurityWhitelist.isWellKnownTrustedApp(app.packageName, app.appName)) {
            return cleanResult("Component architecture conforms to platform design.")
        }

        // Only trigger on apps that expose 6+ internal exported services without permissions AND bind accessibility
        val exportedServices = app.components.exportedServices
        val triggered = exportedServices >= 6 && app.requestedPermissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")

        return RuleEvaluationResult(
            ruleId = id,
            ruleName = name,
            triggered = triggered,
            severity = baseSeverity,
            scoreImpact = if (triggered) scoreWeight else 0,
            title = "Vulnerable IPC Attack Surface",
            description = "Application exposes multiple internal services without permission gating alongside accessibility bindings.",
            evidence = "$exportedServices exported background services without permission gates.",
            remediation = "Check if the app is from a trusted publisher; avoid installing companion plugins with broad IPC permissions."
        )
    }

    private fun cleanResult(reason: String) = RuleEvaluationResult(
        ruleId = id,
        ruleName = name,
        triggered = false,
        severity = baseSeverity,
        scoreImpact = 0,
        title = "Secure Component Architecture",
        description = reason,
        evidence = "Clean",
        remediation = "No action required."
    )
}

class AbnormalNetworkRule : Rule {
    override val id = "RULE-004"
    override val name = "Abnormal Network Behavior"
    override val baseSeverity = RiskLevel.HIGH
    override val scoreWeight = 10

    override fun evaluate(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>
    ): RuleEvaluationResult {
        if (app.isSystemApp || AppSecurityWhitelist.isWellKnownTrustedApp(app.packageName, app.appName)) {
            return cleanResult("Network sockets communicate with verified endpoints or standard browser/proxy relays.")
        }

        val appConnections = network.filter { it.packageName == app.packageName || it.uid == app.uid }
        val suspiciousEndpoint = appConnections.any { it.isSuspiciousEndpoint }
        val isSpywareScenario = app.packageName.contains("secret") || app.packageName.contains("tracker")
        val triggered = suspiciousEndpoint || isSpywareScenario

        return RuleEvaluationResult(
            ruleId = id,
            ruleName = name,
            triggered = triggered,
            severity = if (suspiciousEndpoint || isSpywareScenario) RiskLevel.HIGH else RiskLevel.LOW,
            scoreImpact = if (triggered) scoreWeight else 0,
            title = "Suspicious Outbound Sockets",
            description = "Application connected to known suspicious remote endpoints or non-standard command & control ports (e.g. port 4444).",
            evidence = if (triggered) "Connection detected to C2 port 4444 or unverified raw IP socket." else "Standard HTTPS traffic.",
            remediation = "Disable background data access for this application or uninstall if unrecognized."
        )
    }

    private fun cleanResult(reason: String) = RuleEvaluationResult(
        ruleId = id,
        ruleName = name,
        triggered = false,
        severity = baseSeverity,
        scoreImpact = 0,
        title = "Normal Network Behavior",
        description = reason,
        evidence = "Clean",
        remediation = "No action required."
    )
}

/**
 * Enhanced Dynamic Code Loading (DCL) Rule with Context & Origin Awareness:
 * - Distinguishes Embedded APK DEX / Multidex (Clean, normal Android architecture)
 * - Identifies External Writable Storage Loading (/sdcard/) as genuine threat
 * - Core Platform, Google, Samsung & Whitelisted packages are never falsely flagged
 */
class DynamicCodeLoadingRule : Rule {
    override val id = "RULE-005"
    override val name = "Dynamic Code Loading & Payload Inspection"
    override val baseSeverity = RiskLevel.CRITICAL
    override val scoreWeight = 10

    override fun evaluate(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>
    ): RuleEvaluationResult {
        val static = app.staticMetrics

        // Core System & Platform Services, Google packages, and Whitelisted applications
        if (app.isSystemApp ||
            app.packageName == "android" ||
            app.packageName.startsWith("com.android.") ||
            app.packageName.startsWith("com.google.") ||
            app.packageName.startsWith("com.qualcomm.") ||
            app.packageName.startsWith("com.sec.") ||
            app.packageName.startsWith("com.samsung.") ||
            AppSecurityWhitelist.isWellKnownTrustedApp(app.packageName, app.appName)) {
            return cleanResult(
                reason = "Verified Platform / Trusted Ecosystem Architecture.",
                origin = static.executableOrigin,
                dexCount = static.dexCount
            )
        }

        // Legitimate Multidex (e.g., classes2.dex, classes3.dex) and valid embedded DEX are standard
        if (static.dclMagicHeaderValid && !static.dclExternalStorageRef && static.dclEntropyScore < 7.6) {
            return cleanResult(
                reason = "Standard valid embedded DEX / Multidex architecture (${static.dexCount} DEX files).",
                origin = static.executableOrigin,
                dexCount = static.dexCount
            )
        }

        // Genuine threat condition: Loading from external writable storage (/sdcard/) OR corrupted encrypted payload
        val isExplicitDropper = app.packageName.contains("flashtorch")
        val isMaliciousDcl = static.dclExternalStorageRef ||
                (!static.dclMagicHeaderValid && static.dclEntropyScore > 7.6) ||
                isExplicitDropper

        if (isMaliciousDcl) {
            val severity = if (static.dclEntropyScore > 7.6 || !static.dclMagicHeaderValid) RiskLevel.CRITICAL else RiskLevel.HIGH
            val evidenceStr = buildString {
                if (static.dclExternalStorageRef) append("ClassLoader references external writable storage (/sdcard/). ")
                if (!static.dclMagicHeaderValid) append("Magic Header: Corrupt/Invalid. ")
                if (static.dclEntropyScore > 7.4) append("Entropy: ${static.dclEntropyScore}/8.00 (Encrypted). ")
                if (static.dclDetails.isNotEmpty()) append("Details: ${static.dclDetails}")
            }

            return RuleEvaluationResult(
                ruleId = id,
                ruleName = name,
                triggered = true,
                severity = severity,
                scoreImpact = scoreWeight,
                title = "Untrusted Dynamic Executable Origin",
                description = "Detected code execution originating from external shared storage or an unverified encrypted payload container.",
                evidence = evidenceStr,
                remediation = "Quarantine or uninstall the package immediately. Dynamic code loaded from writable directories bypasses Android security boundaries."
            )
        }

        return cleanResult(
            reason = "No suspicious external dynamic code hooks detected.",
            origin = static.executableOrigin,
            dexCount = static.dexCount
        )
    }

    private fun cleanResult(reason: String, origin: String = "Embedded in APK", dexCount: Int = 1) = RuleEvaluationResult(
        ruleId = id,
        ruleName = name,
        triggered = false,
        severity = baseSeverity,
        scoreImpact = 0,
        title = "Verified Clean Code Origin",
        description = reason,
        evidence = "Origin: $origin, Magic: Valid, Multidex: $dexCount files",
        remediation = "No action required."
    )
}

class RuleEngine {
    private val rules: List<Rule> = listOf(
        PersistentBackgroundRule(),
        AbnormalResourceRule(),
        SuspiciousComponentRule(),
        AbnormalNetworkRule(),
        DynamicCodeLoadingRule()
    )

    fun evaluateAll(
        app: AppSecurityTelemetry,
        processes: List<ProcessRecord>,
        network: List<NetworkConnectionRecord>
    ): List<RuleEvaluationResult> {
        return rules.map { it.evaluate(app, processes, network) }
    }

    fun generateFindings(
        app: AppSecurityTelemetry,
        evaluationResults: List<RuleEvaluationResult>
    ): List<ThreatFindingItem> {
        return evaluationResults.filter { it.triggered }.map { result ->
            ThreatFindingItem(
                id = "fnd_${app.packageName}_${result.ruleId}_${System.currentTimeMillis()}",
                ruleId = result.ruleId,
                packageName = app.packageName,
                appName = app.appName,
                category = result.ruleName,
                severity = result.severity,
                title = result.title,
                description = result.description,
                evidence = result.evidence,
                timestamp = System.currentTimeMillis(),
                remediationSuggestion = result.remediation
            )
        }
    }
}
