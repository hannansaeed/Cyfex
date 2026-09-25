package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.SecurityRepository
import com.example.shizuku.ShizukuManager

class ThreatMonitorApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: SecurityRepository
        private set

    lateinit var shizukuManager: ShizukuManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        shizukuManager = ShizukuManager(this)
        repository = SecurityRepository(this, database, shizukuManager)
    }

    companion object {
        lateinit var instance: ThreatMonitorApp
            private set
    }
}
