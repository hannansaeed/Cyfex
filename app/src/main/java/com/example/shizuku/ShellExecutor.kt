package com.example.shizuku

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isPrivilegedShizuku: Boolean
) {
    val isSuccess: Boolean get() = exitCode == 0
}

class ShellExecutor(private val shizukuManager: ShizukuManager) {

    private val resultCache = ConcurrentHashMap<String, Pair<Long, ShellResult>>()
    private val cacheTtlMs = 2500L

    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        val cached = resultCache[command]
        if (cached != null && (now - cached.first) < cacheTtlMs) {
            return@withContext cached.second
        }

        val isPrivileged = shizukuManager.hasPermission()
        if (isPrivileged) {
            try {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }

                val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as java.lang.Process
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                val result = ShellResult(
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                    isPrivilegedShizuku = true
                )
                resultCache[command] = Pair(now, result)
                return@withContext result
            } catch (t: Throwable) {
                Log.e(TAG, "Shizuku privileged execution error: ${t.message}")
            }
        }

        // Unprivileged sandbox: Avoid running commands known to cause SELinux audit rate-limiting
        if (command.contains("/proc/net") || command.contains("dumpsys") || command.contains("ps -A")) {
            val emptyResult = ShellResult(
                exitCode = 1,
                stdout = "",
                stderr = "Requires Shizuku authorization",
                isPrivilegedShizuku = false
            )
            resultCache[command] = Pair(now, emptyResult)
            return@withContext emptyResult
        }

        // Standard unprivileged fallback for safe commands
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            val result = ShellResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                isPrivilegedShizuku = false
            )
            resultCache[command] = Pair(now, result)
            result
        } catch (e: Throwable) {
            val errResult = ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Execution error",
                isPrivilegedShizuku = false
            )
            resultCache[command] = Pair(now, errResult)
            errResult
        }
    }

    fun clearCache() {
        resultCache.clear()
    }

    companion object {
        const val TAG = "ShellExecutor"
    }
}
