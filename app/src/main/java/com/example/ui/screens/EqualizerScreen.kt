package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.EqMode
import com.example.model.EqPreset
import com.example.ui.SonicMixUiState
import com.example.ui.components.SpectrumCurveCanvas
import com.example.ui.components.StudioFader
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun EqualizerScreen(
    uiState: SonicMixUiState,
    isExpanded: Boolean,
    onBandChanged: (Int, Float) -> Unit,
    onModeChanged: (EqMode) -> Unit,
    onApplyPreset: (EqPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onResetFlat: () -> Unit,
    onToggleTestAudio: () -> Unit,
    onRequestVisualizerPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    val mixer = uiState.mixerState
    val allPresets = remember(uiState.customPresets, uiState.builtInPresets) {
        uiState.customPresets + uiState.builtInPresets
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Quick Action Strip: Active Session Badge + Test Audio + Reset
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val sourceLabel = if (uiState.selectedSessionId == 0) {
                "Global Mix #0"
            } else {
                "Session #${uiState.selectedSessionId}"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ConsoleSurface)
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (mixer.masterEnabled) ElectricMint else PeakCoral)
                )
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onToggleTestAudio,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isTestAudioPlaying) WarmAmber else ConsoleSurfaceElevated,
                    contentColor = if (uiState.isTestAudioPlaying) ObsidianBg else ElectricCyan,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("test_audio_button"),
            ) {
                Icon(
                    imageVector = if (uiState.isTestAudioPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (uiState.isTestAudioPlaying) {
                        stringResource(R.string.test_audio_stop)
                    } else {
                        stringResource(R.string.test_audio_play)
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (uiState.isTestAudioPlaying) "หยุด" else "ลองเสียง",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                onClick = onResetFlat,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reset_flat_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.reset_flat),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "Flat", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Visualizer Permission Banner (only when RECORD_AUDIO is not granted)
        AnimatedVisibility(visible = !uiState.hasRecordAudioPermission) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ConsoleSurfaceElevated)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(26.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.visualizer_permission_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.visualizer_permission_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Button(
                    onClick = onRequestVisualizerPermission,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("enable_visualizer_button"),
                ) {
                    Text(text = "เปิดกราฟสด", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Main EQ Curve + Fader Rack (Adaptive 2-column on wide screens, vertical on phones)
        if (isExpanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    SpectrumCurveCanvas(
                        bands = uiState.activeBandGains,
                        bandSpecs = uiState.activeBandSpecs,
                        spectrumBars = uiState.spectrumBars,
                        masterEnabled = mixer.masterEnabled,
                        onBandChanged = onBandChanged,
                    )
                    ModeAndPresetControls(
                        eqMode = mixer.eqMode,
                        activePresetId = mixer.activePresetId,
                        presets = allPresets,
                        onModeChanged = onModeChanged,
                        onApplyPreset = onApplyPreset,
                        onOpenSaveDialog = { showSaveDialog = true },
                    )
                }

                Box(modifier = Modifier.weight(1.15f)) {
                    FaderBankSection(
                        uiState = uiState,
                        onBandChanged = onBandChanged,
                    )
                }
            }
        } else {
            SpectrumCurveCanvas(
                bands = uiState.activeBandGains,
                bandSpecs = uiState.activeBandSpecs,
                spectrumBars = uiState.spectrumBars,
                masterEnabled = mixer.masterEnabled,
                onBandChanged = onBandChanged,
            )

            ModeAndPresetControls(
                eqMode = mixer.eqMode,
                activePresetId = mixer.activePresetId,
                presets = allPresets,
                onModeChanged = onModeChanged,
                onApplyPreset = onApplyPreset,
                onOpenSaveDialog = { showSaveDialog = true },
            )

            FaderBankSection(
                uiState = uiState,
                onBandChanged = onBandChanged,
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.save_preset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ตั้งชื่อพรีเซ็ตเพื่อบันทึกค่า EQ และมิกเซอร์ปัจจุบัน",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        singleLine = true,
                        label = { Text("ชื่อพรีเซ็ต") },
                        placeholder = { Text("เช่น เบสหนักฟังในรถ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preset_name_input"),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePreset(presetNameInput)
                        presetNameInput = ""
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_preset_button"),
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("ยกเลิก")
                }
            },
        )
    }
}

@Composable
private fun ModeAndPresetControls(
    eqMode: EqMode,
    activePresetId: String,
    presets: List<EqPreset>,
    onModeChanged: (EqMode) -> Unit,
    onApplyPreset: (EqPreset) -> Unit,
    onOpenSaveDialog: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = eqMode == EqMode.TEN_BAND,
                    onClick = { onModeChanged(EqMode.TEN_BAND) },
                    label = { Text(stringResource(R.string.eq_mode_10)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                        selectedLabelColor = ElectricCyan,
                        selectedLeadingIconColor = ElectricCyan,
                    ),
                    modifier = Modifier.testTag("mode_10_band_chip"),
                )
                FilterChip(
                    selected = eqMode == EqMode.FIVE_BAND,
                    onClick = { onModeChanged(EqMode.FIVE_BAND) },
                    label = { Text(stringResource(R.string.eq_mode_5)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricMint.copy(alpha = 0.18f),
                        selectedLabelColor = ElectricMint,
                    ),
                    modifier = Modifier.testTag("mode_5_band_chip"),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ConsoleSurfaceElevated)
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenSaveDialog)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("save_preset_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.save_preset),
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "บันทึก",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(presets, key = { it.id }) { preset ->
                val selected = preset.id == activePresetId
                FilterChip(
                    selected = selected,
                    onClick = { onApplyPreset(preset) },
                    label = { Text(preset.name, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.16f),
                        selectedLabelColor = ElectricCyan,
                        containerColor = ConsoleSurface,
                        labelColor = TextSecondary,
                    ),
                    modifier = Modifier.testTag("preset_chip_${preset.id}"),
                )
            }
        }
    }
}

@Composable
private fun FaderBankSection(
    uiState: SonicMixUiState,
    onBandChanged: (Int, Float) -> Unit,
) {
    val specs = uiState.activeBandSpecs
    val gains = uiState.activeBandGains
    val minDb = uiState.hardwareCapabilities.minGainDb
    val maxDb = uiState.hardwareCapabilities.maxGainDb

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ConsoleSurface.copy(alpha = 0.7f))
            .border(1.dp, ConsoleBorder, RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (uiState.mixerState.eqMode == EqMode.TEN_BAND) {
                    "กราฟิกอีควอไลเซอร์ 10 ย่านความถี่"
                } else {
                    "ฮาร์ดแวร์อีควอไลเซอร์ ${specs.size} ย่านความถี่"
                },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Text(
                text = "แตะ 2 ครั้งเพื่อรีเซ็ต 0 dB",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        if (specs.size <= 5) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                specs.forEachIndexed { idx, spec ->
                    StudioFader(
                        bandIndex = idx,
                        frequencyLabel = spec.label,
                        gainDb = gains.getOrElse(idx) { 0f },
                        minDb = minDb,
                        maxDb = maxDb,
                        enabled = uiState.mixerState.masterEnabled,
                        onGainChanged = { onBandChanged(idx, it) },
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(specs.size) { idx ->
                    val spec = specs[idx]
                    StudioFader(
                        bandIndex = idx,
                        frequencyLabel = spec.label,
                        gainDb = gains.getOrElse(idx) { 0f },
                        minDb = minDb,
                        maxDb = maxDb,
                        enabled = uiState.mixerState.masterEnabled,
                        onGainChanged = { onBandChanged(idx, it) },
                    )
                }
            }
        }
    }
}
