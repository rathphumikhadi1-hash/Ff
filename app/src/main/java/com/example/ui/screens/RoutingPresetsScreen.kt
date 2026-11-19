package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.EqPreset
import com.example.ui.SonicMixUiState
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun RoutingPresetsScreen(
    uiState: SonicMixUiState,
    onToggleBackgroundService: (Boolean) -> Unit,
    onToggleAutoAttach: (Boolean) -> Unit,
    onSelectSession: (Int) -> Unit,
    onApplyPreset: (EqPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onExportPreset: (EqPreset) -> String,
    onImportPresetJson: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    val mixer = uiState.mixerState
    val caps = uiState.hardwareCapabilities

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Background & Multi-App Capture Card
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ConsoleSurface)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.routing_bg_service_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.routing_bg_service_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = mixer.backgroundServiceEnabled,
                    onCheckedChange = onToggleBackgroundService,
                    modifier = Modifier.testTag("bg_service_switch"),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.routing_auto_attach),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = mixer.autoAttachSessions,
                    onCheckedChange = onToggleAutoAttach,
                    modifier = Modifier.testTag("auto_attach_switch"),
                )
            }
        }

        // Active Audio Sessions / Cross-App Routing
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ConsoleSurface)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Text(
                text = stringResource(R.string.routing_active_sessions_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )

            // Global Mix #0 Option
            val isGlobalSelected = uiState.selectedSessionId == 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isGlobalSelected) ElectricCyan.copy(alpha = 0.12f) else ConsoleSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (isGlobalSelected) ElectricCyan else ConsoleBorder,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelectSession(0) }
                    .padding(12.dp)
                    .testTag("session_global_mix"),
            ) {
                Icon(
                    imageVector = Icons.Filled.SurroundSound,
                    contentDescription = null,
                    tint = if (isGlobalSelected) ElectricCyan else TextSecondary,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.routing_global_mix),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.routing_global_mix_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                if (isGlobalSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Active",
                        tint = ElectricMint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (uiState.activeSessions.isEmpty()) {
                Text(
                    text = "กำลังรอจับเซสชันเสียงจากแอปอื่น (Spotify, YouTube, TikTok ฯลฯ) — ขณะนี้ใช้ Global Mix #0 ครอบคลุมทุกแอปอยู่แล้ว",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                uiState.activeSessions.forEach { session ->
                    val isSelected = session.sessionId > 0 && uiState.selectedSessionId == session.sessionId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ConsoleSurfaceElevated)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ElectricMint else ConsoleBorder,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { onSelectSession(session.sessionId) }
                            .padding(12.dp)
                            .testTag("session_item_${session.sessionId}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (session.isAttached) ElectricMint else WarmAmber)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.appLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${session.usageLabel} • ${
                                    if (session.sessionId > 0) "Session #${session.sessionId}" else "Global Route"
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Text(
                            text = if (session.isAttached) "เชื่อมแล้ว" else "แตะเชื่อม",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (session.isAttached) ElectricMint else ElectricCyan,
                        )
                    }
                }
            }
        }

        // Preset Library Manager
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ConsoleSurface)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.routing_presets_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.testTag("import_preset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("นำเข้า JSON")
                }
            }

            val allPresets = uiState.customPresets + uiState.builtInPresets
            allPresets.forEach { preset ->
                val active = preset.id == mixer.activePresetId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) ElectricCyan.copy(alpha = 0.12f) else ConsoleSurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (active) ElectricCyan else ConsoleBorder,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onApplyPreset(preset) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("library_preset_${preset.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AudioFile,
                        contentDescription = null,
                        tint = if (active) ElectricCyan else TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                        )
                        Text(
                            text = "${preset.category} • Preamp ${if (preset.preampDb >= 0) "+" else ""}${preset.preampDb}dB",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    IconButton(
                        onClick = {
                            val json = onExportPreset(preset)
                            clipboard.setText(AnnotatedString(json))
                            onShowMessage("คัดลอก JSON พรีเซ็ต \"${preset.name}\" แล้ว")
                        },
                        modifier = Modifier.testTag("export_preset_${preset.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "คัดลอก JSON",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (!preset.isBuiltIn) {
                        IconButton(
                            onClick = { onDeletePreset(preset.id) },
                            modifier = Modifier.testTag("delete_preset_${preset.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "ลบพรีเซ็ต",
                                tint = PeakCoral,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // Hardware Audio DSP Diagnostics
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ConsoleSurface)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = ElectricMint,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.routing_hardware_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }

            Text(
                text = caps.attachStatusMessage,
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan,
            )

            DiagnosticRow("DynamicsProcessing (10-Band + MBC + Limiter)", caps.hasDynamicsProcessing)
            DiagnosticRow("Hardware Equalizer (${caps.hardwareBandCount} Bands)", caps.hasEqualizer)
            DiagnosticRow("BassBoost Engine", caps.hasBassBoost)
            DiagnosticRow("3D Spatializer / Virtualizer", caps.hasVirtualizer)
            DiagnosticRow("LoudnessEnhancer", caps.hasLoudnessEnhancer)
            DiagnosticRow("PresetReverb", caps.hasReverb)
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("นำเข้าพรีเซ็ต JSON") },
            text = {
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    label = { Text("วางโค้ด JSON ของพรีเซ็ตที่นี่") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_json_input"),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportPresetJson(importJsonText)
                        importJsonText = ""
                        showImportDialog = false
                    },
                    modifier = Modifier.testTag("confirm_import_button"),
                ) {
                    Text("นำเข้า")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("ยกเลิก")
                }
            },
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, supported: Boolean) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (supported) "รองรับ" else "โหมดจำลอง/ไม่รองรับ",
            style = MaterialTheme.typography.labelSmall,
            color = if (supported) ElectricMint else WarmAmber,
        )
    }
}
