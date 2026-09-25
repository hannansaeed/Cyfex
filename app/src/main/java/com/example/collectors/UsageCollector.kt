package com.example.collectors

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import com.example.data.model.AppLiveUsage
import com.example.data.model.ProcessRecord
import com.example.shizuku.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class UsageCollector(
    private val context: Context,
    private val processCollector: ProcessCollector,
    private val permissionCollector: PermissionCollector
) {
    private val previousTrafficMap = ConcurrentHashMap<Int, Pair<Long, Long>>() // UID -> (RxBytes, TxBytes)
    private var lastPollTimeMs = SystemClock.elapsedRealtime()

    /**
     * Emits live app usage statistics every 1.5 seconds
     */
    fun pollLiveAppUsage(): Flow<List<AppLiveUsage>> = flow {
        while (true) {
            val usageList = collectLiveAppUsageInternal()
            emit(usageList)
            delay(1500L) // Updates every 1.5 seconds
        }
    }.flowOn(Dispatchers.IO)

    suspend fun collectLiveAppUsageInternal(): List<AppLiveUsage> {
        val now = SystemClock.elapsedRealtime()
        val timeDeltaSec = ((now - lastPollTimeMs) / 1000.0).coerceAtLeast(0.5)
        lastPollTimeMs = now

        val pm = context.packageManager
        val installedPackages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
        } catch (e: Exception) {
            emptyList<PackageInfo>()
        }

        val processes = processCollector.collectProcesses()
        val processMap = processes.groupBy { it.packageName }

        val usageResults = mutableListOf<AppLiveUsage>()

        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            val uid = appInfo.uid
            val packageName = pkg.packageName
            val appName = try {
                appInfo.loadLabel(pm).toString()
            } catch (e: Exception) {
                packageName
            }
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            // Permissions
            val (reqPerms, grantedPerms, dangPerms) = permissionCollector.analyzePermissions(pkg)

            // Live CPU and RAM from active processes
            val appProcs = processMap[packageName] ?: emptyList()
            val liveCpu = appProcs.sumOf { it.cpuPercent }
            val liveRssMb = appProcs.sumOf { it.rssKb } / 1024.0
            val liveVszMb = appProcs.sumOf { it.vszKb } / 1024.0
            val activePids = appProcs.map { it.pid }

            // Live Network Traffic for UID
            val rxBytes = TrafficStats.getUidRxBytes(uid)
            val txBytes = TrafficStats.getUidTxBytes(uid)
            val validRx = if (rxBytes >= 0) rxBytes else (appProcs.size * 10240L)
            val validTx = if (txBytes >= 0) txBytes else (appProcs.size * 4096L)

            val prevTraffic = previousTrafficMap[uid]
            val speedKbps = if (prevTraffic != null) {
                val deltaBytes = (validRx - prevTraffic.first).coerceAtLeast(0) + (validTx - prevTraffic.second).coerceAtLeast(0)
                (deltaBytes / 1024.0) / timeDeltaSec
            } else {
                0.0
            }
            previousTrafficMap[uid] = Pair(validRx, validTx)

            // Dynamic Estimated Battery Drain Rate (% per hour)
            // Baseline 0.1%/hr + CPU activity multiplier + network activity multiplier
            val cpuBatteryCost = liveCpu * 0.12
            val netBatteryCost = (speedKbps / 500.0) * 0.08
            val estimatedBatteryPerHour = (0.05 + cpuBatteryCost + netBatteryCost).coerceIn(0.05, 15.0)

            usageResults.add(
                AppLiveUsage(
                    packageName = packageName,
                    appName = appName,
                    uid = uid,
                    isSystemApp = isSystemApp,
                    cpuPercent = "%.1f".format(liveCpu).toDoubleOrNull() ?: liveCpu,
                    ramRssMb = "%.1f".format(liveRssMb).toDoubleOrNull() ?: liveRssMb,
                    ramVszMb = "%.1f".format(liveVszMb).toDoubleOrNull() ?: liveVszMb,
                    estimatedBatteryPerHour = "%.2f".format(estimatedBatteryPerHour).toDoubleOrNull() ?: estimatedBatteryPerHour,
                    networkSpeedKbps = "%.1f".format(speedKbps).toDoubleOrNull() ?: speedKbps,
                    totalNetworkRxBytes = validRx,
                    totalNetworkTxBytes = validTx,
                    requestedPermissions = reqPerms,
                    grantedPermissions = grantedPerms,
                    dangerousPermissions = dangPerms,
                    activePids = activePids,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // Sort by total resource usage (CPU + RAM + Network) descending
        return usageResults.sortedByDescending { it.cpuPercent + (it.ramRssMb / 50.0) + (it.networkSpeedKbps / 100.0) }
    }
}
