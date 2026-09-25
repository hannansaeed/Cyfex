package com.example.shizuku

import android.os.Build

object CommandBuilder {

    fun buildProcessListCommand(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "ps -A -o PID,PPID,USER,NAME,VSZ,RSS,STAT"
        } else {
            "ps"
        }
    }

    fun buildPackageListCommand(): String {
        return "pm list packages -f -u -3"
    }

    fun buildPackageDumpCommand(packageName: String): String {
        return "dumpsys package $packageName"
    }

    fun buildNetworkConnectionsCommand(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "ss -ntup || cat /proc/net/tcp /proc/net/tcp6"
        } else {
            "netstat -ntup || cat /proc/net/tcp"
        }
    }

    fun buildTopCommand(): String {
        return "top -n 1 -b -m 20"
    }

    fun buildMemoryInfoCommand(packageName: String): String {
        return "dumpsys meminfo $packageName"
    }

    fun buildCpuInfoCommand(): String {
        return "dumpsys cpuinfo"
    }

    fun buildKillProcessCommand(pid: Int): String {
        return "kill -9 $pid"
    }
}
