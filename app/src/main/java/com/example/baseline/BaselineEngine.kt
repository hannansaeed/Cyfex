package com.example.baseline

import com.example.data.db.dao.BaselineDao
import com.example.data.db.entity.BaselineEntity
import com.example.data.model.BaselineMetrics
import com.example.data.model.ProcessRecord
import kotlin.math.abs
import kotlin.math.sqrt

class DeviationCalculator {

    fun calculateDeviation(
        currentCpu: Double,
        currentMemoryMb: Double,
        baseline: BaselineEntity?
    ): Double {
        if (baseline == null || baseline.sampleCount < 2) {
            // No established baseline yet
            return 0.0
        }

        val cpuDiff = (currentCpu - baseline.avgCpuPercent).coerceAtLeast(0.0)
        val memDiff = (currentMemoryMb - baseline.avgMemoryMb).coerceAtLeast(0.0)

        // Estimated standard deviation approximation
        val cpuStd = (baseline.maxCpuPercent - baseline.avgCpuPercent).coerceAtLeast(2.0) / 2.0
        val memStd = (baseline.avgMemoryMb * 0.25).coerceAtLeast(10.0)

        val zCpu = cpuDiff / cpuStd
        val zMem = memDiff / memStd

        return (zCpu * 0.7 + zMem * 0.3).coerceIn(0.0, 10.0)
    }
}

class BaselineManager(
    private val baselineDao: BaselineDao,
    private val deviationCalculator: DeviationCalculator = DeviationCalculator()
) {

    suspend fun updateBaseline(
        packageName: String,
        observedProcesses: List<ProcessRecord>,
        networkCount: Int
    ): BaselineEntity {
        val existing = baselineDao.getBaseline(packageName)
        val currentCpu = observedProcesses.maxOfOrNull { it.cpuPercent } ?: 0.5
        val currentMem = (observedProcesses.maxOfOrNull { it.rssKb } ?: 50000L) / 1024.0

        val newEntity = if (existing == null) {
            BaselineEntity(
                packageName = packageName,
                avgCpuPercent = currentCpu,
                maxCpuPercent = currentCpu * 1.5,
                avgMemoryMb = currentMem,
                processSpawnRate = 1.0,
                networkFreq = networkCount.toDouble(),
                sampleCount = 1,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            val count = existing.sampleCount + 1
            val newAvgCpu = (existing.avgCpuPercent * existing.sampleCount + currentCpu) / count
            val newMaxCpu = maxOf(existing.maxCpuPercent, currentCpu)
            val newAvgMem = (existing.avgMemoryMb * existing.sampleCount + currentMem) / count

            existing.copy(
                avgCpuPercent = newAvgCpu,
                maxCpuPercent = newMaxCpu,
                avgMemoryMb = newAvgMem,
                sampleCount = count,
                lastUpdated = System.currentTimeMillis()
            )
        }

        baselineDao.insertBaseline(newEntity)
        return newEntity
    }

    suspend fun getDeviationForApp(
        packageName: String,
        currentCpu: Double,
        currentMemMb: Double
    ): Double {
        val baseline = baselineDao.getBaseline(packageName)
        return deviationCalculator.calculateDeviation(currentCpu, currentMemMb, baseline)
    }

    suspend fun clearBaselines() {
        baselineDao.clearBaselines()
    }
}
