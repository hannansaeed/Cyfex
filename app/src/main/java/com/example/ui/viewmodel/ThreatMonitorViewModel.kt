package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ThreatMonitorApp
import com.example.data.db.entity.*
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

    private val _deviceInfo = MutableStateFlow(repository.getDeviceInfo())
    val deviceInfo: StateFlow<DeviceTelemetryInfo> = _deviceInfo.asStateFlow()

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<ApplicationEntity?>(null)
    val selectedAppForDetail: StateFlow<ApplicationEntity?> = _selectedAppForDetail.asStateFlow()

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
}
