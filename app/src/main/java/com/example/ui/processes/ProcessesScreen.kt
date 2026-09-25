package com.example.ui.processes

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.entity.ProcessEntity
import com.example.ui.components.RiskBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.ThreatMonitorViewModel
import kotlinx.coroutines.delay

enum class ProcessSort(val label: String) {
    CPU("CPU %"),
    MEMORY("Memory (RSS)"),
    PID("PID"),
    NAME("Name (A-Z)"),
    STATUS("Status / State")
}

@Composable
fun ProcessesScreen(
    viewModel: ThreatMonitorViewModel
) {
    val processes by viewModel.processes.collectAsState()
    var processToKill by remember { mutableStateOf<ProcessEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(ProcessSort.CPU) }
    var isLiveRefreshing by remember { mutableStateOf(false) }

    // Live Telemetry Refresh Effect
    LaunchedEffect(isLiveRefreshing) {
        while (isLiveRefreshing) {
            delay(2500)
            viewModel.refreshDeviceInfo()
        }
    }

    val filteredProcesses = remember(processes, searchQuery, selectedSort) {
        val list = if (searchQuery.isBlank()) processes
        else processes.filter {
            it.processName.contains(searchQuery, ignoreCase = true) ||
                    it.pid.toString().contains(searchQuery) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }

        when (selectedSort) {
            ProcessSort.CPU -> list.sortedByDescending { it.cpuPercent }
            ProcessSort.MEMORY -> list.sortedByDescending { it.rssKb }
            ProcessSort.PID -> list.sortedBy { it.pid }
            ProcessSort.NAME -> list.sortedBy { it.processName.lowercase() }
            ProcessSort.STATUS -> list.sortedWith(
                compareByDescending<ProcessEntity> { it.isSuspicious }
                    .thenByDescending { it.state.contains("R") }
                    .thenByDescending { it.state.contains("S") }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by PID or process name...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("process_search_field")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Sort Options & Live Monitor Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(ProcessSort.values()) { sort ->
                    FilterChip(
                        selected = selectedSort == sort,
                        onClick = { selectedSort = sort },
                        label = { Text(sort.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (selectedSort == sort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            enabled = true,
                            selected = selectedSort == sort
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Live Update Indicator
            FilledTonalButton(
                onClick = { isLiveRefreshing = !isLiveRefreshing },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isLiveRefreshing) RiskLow.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isLiveRefreshing) RiskLow else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isLiveRefreshing) RiskLow else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isLiveRefreshing) "LIVE" else "PAUSED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACTIVE TELEMETRY (${filteredProcesses.size} PROCESSES)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "SORTED: ${selectedSort.label.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredProcesses, key = { it.pid }) { proc ->
                ProcessItemCard(
                    process = proc,
                    onKill = { processToKill = proc }
                )
            }
        }

        // Kill Confirmation Dialog
        if (processToKill != null) {
            AlertDialog(
                onDismissRequest = { processToKill = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = RiskCritical,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Terminate Process PID ${processToKill!!.pid}?",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to send SIGKILL to privileged process '${processToKill!!.processName}' via Shizuku shell?\n\nThis will force-stop the background daemon.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.killProcess(processToKill!!.pid)
                            processToKill = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskCritical)
                    ) {
                        Text("Force Kill", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { processToKill = null }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun ProcessItemCard(
    process: ProcessEntity,
    onKill: () -> Unit
) {
    val isHighCpu = process.cpuPercent > 30.0

    // Human-readable process state dot & label
    val (statusDotColor, statusLabel) = when {
        process.isSuspicious -> Pair(RiskCritical, "ANOMALY")
        process.state.contains("R") -> Pair(RiskLow, "RUNNING")
        process.state.contains("S") -> Pair(MaterialTheme.colorScheme.primary, "ACTIVE")
        process.state.contains("D") -> Pair(RiskMedium, "I/O WAIT")
        process.state.contains("Z") -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "ZOMBIE")
        else -> Pair(RiskLow, "ACTIVE")
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (process.isSuspicious) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (process.isSuspicious) RiskCritical.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("process_item_${process.pid}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PID ${process.pid}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = process.processName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Indicator badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(statusDotColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(statusDotColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusDotColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(text = "CPU", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${"%.1f".format(process.cpuPercent)}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHighCpu) RiskCritical else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column {
                        Text(text = "RSS MEM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${process.rssKb / 1024} MB",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column {
                        Text(text = "USER", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = process.user,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column {
                        Text(text = "STATE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = process.state,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onKill,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = RiskCritical.copy(alpha = 0.15f),
                        contentColor = RiskCritical
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Kill",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (process.anomalyNote != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = RiskCritical.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RiskCritical.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = RiskCritical,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = process.anomalyNote,
                            fontSize = 11.sp,
                            color = RiskCritical
                        )
                    }
                }
            }
        }
    }
}
