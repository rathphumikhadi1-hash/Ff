package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ReverbRoomOption
import com.example.ui.SonicMixUiState
import com.example.ui.components.RotaryKnob
import com.example.ui.components.StereoVuMeter
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import kotlin.math.roundToInt

@Composable
fun MixerScreen(
    uiState: SonicMixUiState,
    isExpanded: Boolean,
    onPreampChange: (Float) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit,
    onToggleCompressor: () -> Unit,
    onCompressorDriveChange: (Float) -> Unit,
    onLimiterCeilingChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit,
    onToggleMonoMix: () -> Unit,
    onReverbChange: (Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mixer = uiState.mixerState
    val primaryDevice = uiState.outputDevices.firstOrNull()

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Stereo Output VU Meter Card
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
                    text = stringResource(R.string.mixer_output_meters),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ConsoleSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = if (primaryDevice?.typeLabel?.contains("Speaker") == true ||
                            primaryDevice?.typeLabel?.contains("ลำโพง") == true
                        ) Icons.Filled.Speaker else Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = primaryDevice?.let { "${it.typeLabel} • ${it.sampleRateHz / 1000}kHz" }
                            ?: "Audio Out",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            StereoVuMeter(
                leftDb = uiState.peakLeftDb,
                rightDb = uiState.peakRightDb,
            )
        }

        // Rotary Studio Rack (4-across on Expanded, 2x2 on phones)
        val preampFraction = ((mixer.preampGainDb + 12f) / 24f).coerceIn(0f, 1f)
        val preampText = if (mixer.preampGainDb >= 0f) {
            "+${String.format("%.1f", mixer.preampGainDb)} dB"
        } else {
            "${String.format("%.1f", mixer.preampGainDb)} dB"
        }
        val bassFraction = mixer.bassBoostStrength / 1000f
        val virtFraction = mixer.virtualizerStrength / 1000f
        val loudFraction = mixer.loudnessGainMb / 1500f

        if (isExpanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RotaryKnob(
                    label = stringResource(R.string.fx_preamp),
                    valueText = preampText,
                    fraction = preampFraction,
                    defaultFraction = 0.5f,
                    accentColor = ElectricCyan,
                    enabled = mixer.masterEnabled,
                    testTagName = "knob_preamp",
                    onFractionChange = { onPreampChange(-12f + it * 24f) },
                    modifier = Modifier.weight(1f),
                )
                RotaryKnob(
                    label = stringResource(R.string.fx_bass_boost),
                    valueText = "${(bassFraction * 100).roundToInt()}%",
                    fraction = bassFraction,
                    defaultFraction = 0f,
                    accentColor = ElectricMint,
                    enabled = mixer.masterEnabled,
                    testTagName = "knob_bass_boost",
                    onFractionChange = { onBassBoostChange((it * 1000).roundToInt()) },
                    modifier = Modifier.weight(1f),
                )
                RotaryKnob(
                    label = stringResource(R.string.fx_virtualizer),
                    valueText = "${(virtFraction * 100).roundToInt()}%",
                    fraction = virtFraction,
                    defaultFraction = 0f,
                    accentColor = ElectricCyan,
                    enabled = mixer.masterEnabled,
                    testTagName = "knob_virtualizer",
                    onFractionChange = { onVirtualizerChange((it * 1000).roundToInt()) },
                    modifier = Modifier.weight(1f),
                )
                RotaryKnob(
                    label = stringResource(R.string.fx_loudness),
                    valueText = "+${String.format("%.1f", mixer.loudnessGainMb / 100f)} dB",
                    fraction = loudFraction,
                    defaultFraction = 0f,
                    accentColor = WarmAmber,
                    enabled = mixer.masterEnabled,
                    testTagName = "knob_loudness",
                    onFractionChange = { onLoudnessChange((it * 1500).roundToInt()) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RotaryKnob(
                    label = stringResource(R.string.fx_preamp),
                    valueText = preampText,
                    fraction = preampFraction,
                    defaultFraction = 0.5f,
                    accentColor = ElectricCyan,
                    enabled = mixer.masterEnabled,
                    testTagName = "knob_preamp",
                    onFractionChange = { onPreampChange(-12f + it * 24f) },
                    modifier = Modifier.weight(1f),
                )
                RotaryKnob(
                    label = stringResource(R.string.fx_bass_boost),
                    valueText = "${(bassFraction * 100).roundToInt()}%",
                    fraction = bassFraction,
                    defaultFraction = 0f,
                    accentColor = ElectricMint,
                    enabled = mixer.masterEnabled,
                    testTagName = "knob_bass_boost",
                    onFractionChange = { onBassBoostChange((it * 1000).roundToInt()) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RotaryKnob(
                    label = stringResource(R.string.fx_virtualizer),
                    valueText = "${(virtFraction * 100).roundToInt()}%",
                    fraction = virtFraction,
                    defaultFraction = 0f,
                    accentColor = ElectricCyan,
                    enabled = mixer.masterEnabled,
                    testTagName = "knob_virtualizer",
                    onFractionChange = { onVirtualizerChange((it * 1000).roundToInt()) },
                    modifier = Modifier.weight(1f),
                )
                RotaryKnob(
                    label = stringResource(R.string.fx_loudness),
                    valueText = "+${String.format("%.1f", mixer.loudnessGainMb / 100f)} dB",
                    fraction = loudFraction,
                    defaultFraction = 0f,
                    accentColor = WarmAmber,
                    enabled = mixer.masterEnabled,
                    testTagName = "knob_loudness",
                    onFractionChange = { onLoudnessChange((it * 1500).roundToInt()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Dynamics & Limiter Module
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = WarmAmber,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.fx_dynamics_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.fx_compressor),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
                Switch(
                    checked = mixer.compressorEnabled,
                    onCheckedChange = { onToggleCompressor() },
                    modifier = Modifier.testTag("compressor_switch"),
                )
            }

            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.fx_compressor_drive),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Text(
                        text = "${(mixer.compressorDrive * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (mixer.compressorEnabled) WarmAmber else TextSecondary,
                    )
                }
                Slider(
                    value = mixer.compressorDrive,
                    onValueChange = onCompressorDriveChange,
                    enabled = mixer.compressorEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = WarmAmber,
                        activeTrackColor = WarmAmber,
                    ),
                    modifier = Modifier.testTag("compressor_drive_slider"),
                )
            }

            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.fx_limiter_ceiling),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Text(
                        text = "${String.format("%.1f", mixer.limiterCeilingDb)} dB",
                        style = MaterialTheme.typography.labelMedium,
                        color = PeakCoral,
                    )
                }
                Slider(
                    value = mixer.limiterCeilingDb,
                    onValueChange = onLimiterCeilingChange,
                    valueRange = -6f..0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PeakCoral,
                        activeTrackColor = PeakCoral,
                    ),
                    modifier = Modifier.testTag("limiter_ceiling_slider"),
                )
            }
        }

        // Stereo Balance & Room Reverb Module
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ConsoleSurface)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Text(
                text = stringResource(R.string.fx_stereo_mixer_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )

            // Stereo Balance
            Column {
                val balLabel = when {
                    mixer.stereoBalance < -0.05f -> "L ${(-mixer.stereoBalance * 100).roundToInt()}%"
                    mixer.stereoBalance > 0.05f -> "R ${(mixer.stereoBalance * 100).roundToInt()}%"
                    else -> "Center (0%)"
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.fx_balance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Text(
                        text = balLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                    )
                }
                Slider(
                    value = mixer.stereoBalance,
                    onValueChange = onBalanceChange,
                    valueRange = -1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                    ),
                    modifier = Modifier.testTag("stereo_balance_slider"),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = { onBalanceChange(-1f) }) { Text("L 100%") }
                    TextButton(onClick = { onBalanceChange(0f) }) { Text("Center") }
                    TextButton(onClick = { onBalanceChange(1f) }) { Text("R 100%") }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.fx_mono_downmix),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                )
                Switch(
                    checked = mixer.monoMixEnabled,
                    onCheckedChange = { onToggleMonoMix() },
                    modifier = Modifier.testTag("mono_mix_switch"),
                )
            }

            // Room Reverb Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.fx_reverb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReverbRoomOption.entries) { room ->
                        val selected = mixer.reverbPreset == room.presetId
                        FilterChip(
                            selected = selected,
                            onClick = { onReverbChange(room.presetId) },
                            label = { Text(room.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricMint.copy(alpha = 0.18f),
                                selectedLabelColor = ElectricMint,
                            ),
                            modifier = Modifier.testTag("reverb_chip_${room.presetId}"),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
