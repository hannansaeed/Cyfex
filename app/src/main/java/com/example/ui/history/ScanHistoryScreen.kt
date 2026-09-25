package com.example.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.entity.ScanEntity
import com.example.ui.components.RiskBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.ThreatMonitorViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScanHistoryScreen(
    viewModel: ThreatMonitorViewModel
) {
    val scans by viewModel.scans.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "TELEMETRY SCAN HISTORY",
            style = MaterialTheme.typography.labelSmall,
            color = CyberPrimary,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Historical device security assessments and threat score progressions.",
            style = MaterialTheme.typography.bodySmall,
            color = CyberTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (scans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 88.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No scan records available.",
                    color = CyberTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(scans, key = { it.scanId }) { scan ->
                    ScanHistoryCard(scan = scan)
                }
            }
        }
    }
}

@Composable
fun ScanHistoryCard(scan: ScanEntity) {
    val timeFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(scan.timestamp) { timeFormat.format(Date(scan.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scan_item_${scan.scanId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                RiskBadge(riskLevel = scan.overallRiskLevel, score = scan.overallRiskScore)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Apps Scanned", fontSize = 10.sp, color = CyberTextSecondary)
                    Text(text = "${scan.totalAppsScanned}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column {
                    Text(text = "Suspicious Procs", fontSize = 10.sp, color = CyberTextSecondary)
                    Text(text = "${scan.suspiciousProcessesCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (scan.suspiciousProcessesCount > 0) RiskCritical else Color.White)
                }
                Column {
                    Text(text = "Findings", fontSize = 10.sp, color = CyberTextSecondary)
                    Text(text = "${scan.activeFindingsCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (scan.activeFindingsCount > 0) RiskHigh else Color.White)
                }
                Column {
                    Text(text = "Duration", fontSize = 10.sp, color = CyberTextSecondary)
                    Text(text = "${scan.durationMs} ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (scan.isShizukuPrivileged) Icons.Default.VerifiedUser else Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (scan.isShizukuPrivileged) CyberTertiary else CyberTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (scan.isShizukuPrivileged) "Privileged Shizuku ADB Shell" else "Standard Fallback Mode",
                    fontSize = 11.sp,
                    color = if (scan.isShizukuPrivileged) CyberTertiary else CyberTextSecondary
                )
            }
        }
    }
}
