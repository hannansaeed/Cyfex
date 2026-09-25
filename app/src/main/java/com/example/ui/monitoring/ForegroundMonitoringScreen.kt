package com.example.ui.monitoring

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.entity.SensorAccessEventEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.ThreatMonitorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TimeWindowOption(val label: String, val durationMs: Long) {
    LAST_24_HOURS("Last 24 Hours", 24 * 60 * 60 * 1000L),
    LAST_12_HOURS("Last 12 Hours", 12 * 60 * 60 * 1000L),
    LAST_1_HOUR("Last 1 Hour", 60 * 60 * 1000L),
    ALL("All", Long.MAX_VALUE)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ForegroundMonitoringScreen(
    viewModel: ThreatMonitorViewModel
) {
    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val session by viewModel.activeMonitoringSession.collectAsState()
    val allAccessEvents by viewModel.sensorAccessEvents.collectAsState()

    var selectedTimeWindow by remember { mutableStateOf(TimeWindowOption.LAST_24_HOURS) }
    var selectedResourceFilter by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val timeWindowEvents = remember(allAccessEvents, selectedTimeWindow) {
        val cutoff = if (selectedTimeWindow == TimeWindowOption.ALL) 0L else now - selectedTimeWindow.durationMs
        allAccessEvents.filter { it.timestamp >= cutoff }
    }

    val filteredEvents = remember(timeWindowEvents, selectedResourceFilter) {
        timeWindowEvents.filter { selectedResourceFilter == null || it.resourceType == selectedResourceFilter }
    }

    // Grouping by package for aggregated App Ledger
    val appLedger = remember(filteredEvents) {
        filteredEvents
            .groupBy { it.packageName }
            .map { (pkg, events) ->
                val appName = events.firstOrNull()?.appName ?: pkg
                val resourceCounts = events.groupBy { it.resourceType }.mapValues { entry -> entry.value.sumOf { it.accessCount } }
                val totalCount = resourceCounts.values.sum()
                val latestTime = events.maxOfOrNull { it.timestamp } ?: 0L
                AppAccessLedgerItem(
                    packageName = pkg,
                    appName = appName,
                    resourceCounts = resourceCounts,
                    totalAccessCount = totalCount,
                    latestTimestamp = latestTime
                )
            }
            .sortedByDescending { it.totalAccessCount }
    }

    // Pulse animation for active monitoring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Automatically audit hardware & subsystem accesses upon entering screen and every 3.5s
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshSensorAccessAudit()
            kotlinx.coroutines.delay(3500L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header & Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FOREGROUND TELEMETRY DAEMON",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Foreground Monitoring",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.refreshSensorAccessAudit() },
                    modifier = Modifier.testTag("refresh_access_audit_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Audit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.testTag("clear_access_logs_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Master Service Switch Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isServiceActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isServiceActive) Color(0xFF00E676).copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isServiceActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (isServiceActive) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isServiceActive) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676).copy(alpha = pulseAlpha))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = if (isServiceActive) "Monitoring Active (Background)" else "Monitoring Paused",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = if (isServiceActive) "Auditing hardware accesses & background persistence"
                                    else "Enable to audit Camera, Mic & Location in background",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = isServiceActive,
                            onCheckedChange = { viewModel.toggleMonitoringService() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.testTag("foreground_monitoring_toggle")
                        )
                    }
                }
            }

            // 2. Time Window & Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeWindowOption.values().forEach { option ->
                        val isSelected = selectedTimeWindow == option
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTimeWindow = option },
                            label = { Text(option.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // 3. Sensor Overview Badges Grid (5 Cards)
            item {
                val cameraCount = timeWindowEvents.filter { it.resourceType == "CAMERA" }.sumOf { it.accessCount }
                val micCount = timeWindowEvents.filter { it.resourceType == "MICROPHONE" }.sumOf { it.accessCount }
                val locCount = timeWindowEvents.filter { it.resourceType == "LOCATION" }.sumOf { it.accessCount }
                val contactsCount = timeWindowEvents.filter { it.resourceType == "CONTACTS" }.sumOf { it.accessCount }
                val storageCount = timeWindowEvents.filter { it.resourceType == "STORAGE" }.sumOf { it.accessCount }

                Column {
                    Text(
                        text = "SENSOR ACCESS SUMMARY (${selectedTimeWindow.label.uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD35400),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SensorSummaryCard(
                            label = "Camera",
                            count = cameraCount,
                            icon = Icons.Default.PhotoCamera,
                            color = Color(0xFFFF5722),
                            isSelected = selectedResourceFilter == "CAMERA",
                            onClick = { selectedResourceFilter = if (selectedResourceFilter == "CAMERA") null else "CAMERA" },
                            modifier = Modifier.weight(1f)
                        )
                        SensorSummaryCard(
                            label = "Microphone",
                            count = micCount,
                            icon = Icons.Default.Mic,
                            color = Color(0xFFE91E63),
                            isSelected = selectedResourceFilter == "MICROPHONE",
                            onClick = { selectedResourceFilter = if (selectedResourceFilter == "MICROPHONE") null else "MICROPHONE" },
                            modifier = Modifier.weight(1f)
                        )
                        SensorSummaryCard(
                            label = "Location",
                            count = locCount,
                            icon = Icons.Default.LocationOn,
                            color = Color(0xFF00B0FF),
                            isSelected = selectedResourceFilter == "LOCATION",
                            onClick = { selectedResourceFilter = if (selectedResourceFilter == "LOCATION") null else "LOCATION" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SensorSummaryCard(
                            label = "Contacts",
                            count = contactsCount,
                            icon = Icons.Default.Contacts,
                            color = Color(0xFF9C27B0),
                            isSelected = selectedResourceFilter == "CONTACTS",
                            onClick = { selectedResourceFilter = if (selectedResourceFilter == "CONTACTS") null else "CONTACTS" },
                            modifier = Modifier.weight(1f)
                        )
                        SensorSummaryCard(
                            label = "Storage",
                            count = storageCount,
                            icon = Icons.Default.FolderOpen,
                            color = Color(0xFFFF9800),
                            isSelected = selectedResourceFilter == "STORAGE",
                            onClick = { selectedResourceFilter = if (selectedResourceFilter == "STORAGE") null else "STORAGE" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Per-App Access Summary Ledger
            item {
                Text(
                    text = "APP ACCESS LEDGER (${appLedger.size} APPS DETECTED)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (appLedger.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No sensor access events logged in selected time window.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(appLedger, key = { it.packageName }) { appItem ->
                    AppAccessLedgerCard(item = appItem)
                }
            }

            // 5. Recent Chronological Audit Log
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "RECENT ACCESS LOGS (${filteredEvents.size} EVENTS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            items(filteredEvents.take(50)) { event ->
                AccessEventLogItem(event = event)
            }
        }
    }

    // Confirmation Dialog to Clear Access Logs
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Access Audit History?") },
            text = { Text("This will permanently clear all recorded sensor and hardware access events.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAccessLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = RiskCritical)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class AppAccessLedgerItem(
    val packageName: String,
    val appName: String,
    val resourceCounts: Map<String, Int>,
    val totalAccessCount: Int,
    val latestTimestamp: Long
)

@Composable
fun SensorSummaryCard(
    label: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) color.copy(alpha = 0.25f) else color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) color else color.copy(alpha = 0.3f)
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppAccessLedgerCard(
    item: AppAccessLedgerItem
) {
    val timeFormatted = remember(item.latestTimestamp) {
        if (item.latestTimestamp > 0) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.latestTimestamp))
        } else "N/A"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Last: $timeFormatted",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Resource badges breakdown with FlowRow to wrap cleanly across multiple lines
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.resourceCounts.forEach { (resType, count) ->
                    val (badgeColor, icon) = when (resType) {
                        "CAMERA" -> Pair(Color(0xFFFF5722), Icons.Default.PhotoCamera)
                        "MICROPHONE" -> Pair(Color(0xFFE91E63), Icons.Default.Mic)
                        "LOCATION" -> Pair(Color(0xFF00B0FF), Icons.Default.LocationOn)
                        "CONTACTS" -> Pair(Color(0xFF9C27B0), Icons.Default.Contacts)
                        else -> Pair(Color(0xFFFF9800), Icons.Default.FolderOpen)
                    }

                    Surface(
                        color = badgeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$resType x$count",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccessEventLogItem(
    event: SensorAccessEventEntity
) {
    val timeFormatted = remember(event.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
    }

    val (icon, color) = when (event.resourceType) {
        "CAMERA" -> Pair(Icons.Default.PhotoCamera, Color(0xFFFF5722))
        "MICROPHONE" -> Pair(Icons.Default.Mic, Color(0xFFE91E63))
        "LOCATION" -> Pair(Icons.Default.LocationOn, Color(0xFF00B0FF))
        "CONTACTS" -> Pair(Icons.Default.Contacts, Color(0xFF9C27B0))
        else -> Pair(Icons.Default.FolderOpen, Color(0xFFFF9800))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${event.appName} • ${event.resourceType}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = timeFormatted,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = event.details,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
