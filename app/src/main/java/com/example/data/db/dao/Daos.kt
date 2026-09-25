package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationDao {
    @Query("SELECT * FROM applications ORDER BY riskScore DESC")
    fun getAllApplications(): Flow<List<ApplicationEntity>>

    @Query("SELECT * FROM applications WHERE packageName = :pkg LIMIT 1")
    suspend fun getApplication(pkg: String): ApplicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplications(apps: List<ApplicationEntity>)

    @Query("DELETE FROM applications")
    suspend fun clearApplications()
}

@Dao
interface ProcessDao {
    @Query("SELECT * FROM processes ORDER BY cpuPercent DESC")
    fun getLatestProcesses(): Flow<List<ProcessEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcesses(processes: List<ProcessEntity>)

    @Query("DELETE FROM processes")
    suspend fun clearProcesses()
}

@Dao
interface BehaviorEventDao {
    @Query("SELECT * FROM behavior_events ORDER BY timestamp DESC LIMIT 200")
    fun getAllEvents(): Flow<List<BehaviorEventEntity>>

    @Query("SELECT * FROM behavior_events WHERE packageName = :pkg ORDER BY timestamp DESC")
    fun getEventsForPackage(pkg: String): Flow<List<BehaviorEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: BehaviorEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<BehaviorEventEntity>)

    @Query("DELETE FROM behavior_events")
    suspend fun clearEvents()
}

@Dao
interface FindingDao {
    @Query("SELECT * FROM findings ORDER BY timestamp DESC")
    fun getAllFindings(): Flow<List<FindingEntity>>

    @Query("SELECT * FROM findings WHERE packageName = :pkg ORDER BY timestamp DESC")
    fun getFindingsForPackage(pkg: String): Flow<List<FindingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<FindingEntity>)

    @Query("DELETE FROM findings")
    suspend fun clearFindings()
}

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT 1")
    fun getLatestScan(): Flow<ScanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity)

    @Query("DELETE FROM scans")
    suspend fun clearScans()
}

@Dao
interface BaselineDao {
    @Query("SELECT * FROM baselines WHERE packageName = :pkg LIMIT 1")
    suspend fun getBaseline(pkg: String): BaselineEntity?

    @Query("SELECT * FROM baselines")
    fun getAllBaselines(): Flow<List<BaselineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(baseline: BaselineEntity)

    @Query("DELETE FROM baselines")
    suspend fun clearBaselines()
}

@Dao
interface RuleResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRuleResults(results: List<RuleResultEntity>)

    @Query("SELECT * FROM rule_results ORDER BY timestamp DESC LIMIT 100")
    fun getRecentRuleResults(): Flow<List<RuleResultEntity>>
}
