package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.db.dao.*
import com.example.data.db.entity.*

@Database(
    entities = [
        DeviceEntity::class,
        ApplicationEntity::class,
        ProcessEntity::class,
        BehaviorEventEntity::class,
        FindingEntity::class,
        ScanEntity::class,
        BaselineEntity::class,
        FeatureVectorEntity::class,
        RuleResultEntity::class,
        SensorAccessEventEntity::class,
        MonitoringSessionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun applicationDao(): ApplicationDao
    abstract fun processDao(): ProcessDao
    abstract fun behaviorEventDao(): BehaviorEventDao
    abstract fun findingDao(): FindingDao
    abstract fun scanDao(): ScanDao
    abstract fun baselineDao(): BaselineDao
    abstract fun ruleResultDao(): RuleResultDao
    abstract fun sensorAccessDao(): SensorAccessDao
    abstract fun monitoringSessionDao(): MonitoringSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "threat_monitor.db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
