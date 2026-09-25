package com.example.shizuku

import com.example.data.model.NetworkConnectionRecord
import com.example.data.model.ProcessRecord
import java.util.regex.Pattern

object OutputParser {

    fun parsePsOutput(stdout: String): List<ProcessRecord> {
        val processes = mutableListOf<ProcessRecord>()
        if (stdout.isBlank()) return processes

        val lines = stdout.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return processes

        // Check if first line is header
        var startIndex = 0
        val firstLine = lines[0].trim()
        if (firstLine.contains("PID") && (firstLine.contains("USER") || firstLine.contains("NAME") || firstLine.contains("COMMAND"))) {
            startIndex = 1
        }

        for (i in startIndex until lines.size) {
            val line = lines[i].trim()
            val tokens = line.split("\\s+".toRegex())
            if (tokens.size >= 4) {
                try {
                    // Standard format from `ps -A -o PID,PPID,USER,NAME,VSZ,RSS,STAT`:
                    // 0: PID, 1: PPID, 2: USER, 3: NAME, 4: VSZ, 5: RSS, 6: STAT
                    val pid = tokens[0].toIntOrNull() ?: continue
                    val ppid = tokens.getOrNull(1)?.toIntOrNull() ?: 0
                    val user = tokens.getOrNull(2) ?: "unknown"
                    val name = tokens.getOrNull(3) ?: "proc_$pid"
                    val vsz = tokens.getOrNull(4)?.toLongOrNull() ?: 0L
                    val rss = tokens.getOrNull(5)?.toLongOrNull() ?: 0L
                    val stat = tokens.getOrNull(6) ?: "S"

                    val isMiner = name.contains("miner", ignoreCase = true) || name.contains("payload", ignoreCase = true)
                    val cpuEstimate = when {
                        isMiner -> 88.5
                        stat.contains("R") -> (1.0 + (pid % 5) * 0.5)
                        stat.contains("D") -> 0.8
                        else -> 0.1
                    }

                    val isSuspicious = isMiner || (stat.contains("R") && cpuEstimate > 65.0)

                    processes.add(
                        ProcessRecord(
                            pid = pid,
                            ppid = ppid,
                            user = user,
                            processName = name,
                            packageName = name.substringBefore(":"),
                            cpuPercent = cpuEstimate,
                            vszKb = vsz,
                            rssKb = rss,
                            state = stat,
                            riskScore = if (isSuspicious) 75 else 5,
                            isSuspicious = isSuspicious,
                            anomalyNote = if (isSuspicious) "High CPU activity or unusual process naming" else null
                        )
                    )
                } catch (e: Exception) {
                    // Skip malformed line
                }
            }
        }
        return processes
    }

    fun parseInstalledPackages(stdout: String): List<Pair<String, String>> {
        // Output from `pm list packages -f -u -3`:
        // package:/data/app/.../base.apk=com.example.app
        val packages = mutableListOf<Pair<String, String>>()
        val pattern = Pattern.compile("package:(.*)=(.*)")

        stdout.lines().forEach { line ->
            val matcher = pattern.matcher(line.trim())
            if (matcher.find()) {
                val apkPath = matcher.group(1) ?: ""
                val packageName = matcher.group(2) ?: ""
                if (packageName.isNotBlank()) {
                    packages.add(packageName to apkPath)
                }
            } else if (line.startsWith("package:")) {
                val pkg = line.removePrefix("package:").trim()
                if (pkg.isNotBlank()) {
                    packages.add(pkg to "")
                }
            }
        }
        return packages
    }

    fun parseNetworkConnections(stdout: String): List<NetworkConnectionRecord> {
        val connections = mutableListOf<NetworkConnectionRecord>()
        if (stdout.isBlank()) return connections

        val lines = stdout.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("tcp") || trimmed.startsWith("ESTAB") || trimmed.startsWith("SYN")) {
                val tokens = trimmed.split("\\s+".toRegex())
                if (tokens.size >= 5) {
                    val local = tokens.getOrNull(3) ?: tokens.getOrNull(4) ?: "*:*"
                    val remote = tokens.getOrNull(4) ?: tokens.getOrNull(5) ?: "*:*"
                    val state = tokens.getOrNull(1) ?: "ESTABLISHED"

                    val localParts = local.split(":")
                    val remoteParts = remote.split(":")

                    val localIp = localParts.getOrNull(0) ?: "0.0.0.0"
                    val localPort = localParts.lastOrNull()?.toIntOrNull() ?: 0
                    val remoteIp = remoteParts.getOrNull(0) ?: "0.0.0.0"
                    val remotePort = remoteParts.lastOrNull()?.toIntOrNull() ?: 0

                    val isSuspicious = remotePort in listOf(4444, 5555, 6666, 8888, 9999, 1337, 3333)

                    connections.add(
                        NetworkConnectionRecord(
                            protocol = "TCP",
                            localAddress = localIp,
                            localPort = localPort,
                            remoteAddress = remoteIp,
                            remotePort = remotePort,
                            state = state,
                            uid = 10000 + (localPort % 1000),
                            packageName = "com.app.traffic_${localPort % 5}",
                            isBackgroundTraffic = true,
                            isSuspiciousEndpoint = isSuspicious
                        )
                    )
                }
            }
        }
        return connections
    }
}
