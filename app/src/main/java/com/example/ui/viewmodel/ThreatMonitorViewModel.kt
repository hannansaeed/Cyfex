package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ThreatMonitorApp
import com.example.data.db.entity.*
import com.example.data.model.AppLiveUsage
import com.example.data.model.DeviceTelemetryInfo
import com.example.service.MonitoringService
import com.example.shizuku.ShizukuStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ThreatMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ThreatMonitorApp).repository
    val shizukuManager = repository.shizukuManager

    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuManager.status

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Float> = repository.scanProgress
    val scanMessage: StateFlow<String> = repository.currentScanMessage

    val applications: StateFlow<List<ApplicationEntity>> = repository.allApplications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val processes: StateFlow<List<ProcessEntity>> = repository.latestProcesses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<BehaviorEventEntity>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val findings: StateFlow<List<FindingEntity>> = repository.allFindings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scans: StateFlow<List<ScanEntity>> = repository.allScans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestScan: StateFlow<ScanEntity?> = repository.latestScan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Dynamic Live Resource Usage updating every 1.5s
    val liveUsage: StateFlow<List<AppLiveUsage>> = repository.liveUsage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 24-Hour Sensor and Hardware Access Audit Events
    val sensorAccessEvents: StateFlow<List<SensorAccessEventEntity>> = repository.sensorAccessEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMonitoringSession: StateFlow<MonitoringSessionEntity?> = repository.activeMonitoringSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _deviceInfo = MutableStateFlow(repository.getDeviceInfo())
    val deviceInfo: StateFlow<DeviceTelemetryInfo> = _deviceInfo.asStateFlow()

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<ApplicationEntity?>(null)
    val selectedAppForDetail: StateFlow<ApplicationEntity?> = _selectedAppForDetail.asStateFlow()

    private val _isDebugModeEnabled = MutableStateFlow(false)
    val isDebugModeEnabled: StateFlow<Boolean> = _isDebugModeEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.activeMonitoringSession.collectLatest { session ->
                _isServiceActive.value = session?.isRunning == true
            }
        }
    }

    fun toggleDebugMode() {
        _isDebugModeEnabled.value = !_isDebugModeEnabled.value
    }

    fun selectApp(app: ApplicationEntity?) {
        _selectedAppForDetail.value = app
    }

    fun triggerScan() {
        viewModelScope.launch {
            repository.performScan()
            refreshDeviceInfo()
        }
    }

    fun refreshDeviceInfo() {
        _deviceInfo.value = repository.getDeviceInfo()
    }

    fun killProcess(pid: Int) {
        viewModelScope.launch {
            repository.killProcess(pid)
        }
    }

    fun injectTestScenario(scenarioName: String) {
        viewModelScope.launch {
            repository.injectControlledScenario(scenarioName)
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
        refreshDeviceInfo()
    }

    fun openShizukuApp() {
        shizukuManager.openShizukuApp()
    }

    fun toggleMonitoringService() {
        val newState = !_isServiceActive.value
        _isServiceActive.value = newState
        val context = getApplication<Application>().applicationContext
        if (newState) {
            MonitoringService.startService(context)
        } else {
            MonitoringService.stopService(context)
        }
    }

    fun refreshSensorAccessAudit() {
        viewModelScope.launch {
            repository.foregroundMonitorCollector.auditAndLogSensorAccesses()
        }
    }

    fun logDirectHardwareProbe(resourceType: String, appName: String, pkg: String) {
        viewModelScope.launch {
            repository.foregroundMonitorCollector.recordRealtimeSensorAccess(
                targetPkg = pkg,
                resourceType = resourceType,
                details = "$resourceType hardware access session logged via direct probe"
            )
        }
    }

    fun populateSampleSensorAccessLogs() {
        viewModelScope.launch {
            repository.populateSampleSensorAccessLogs()
        }
    }

    fun clearAccessLogs() {
        viewModelScope.launch {
            repository.clearSensorAccessEvents()
        }
    }
}
