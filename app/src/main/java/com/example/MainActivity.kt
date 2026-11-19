package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppTab
import com.example.ui.SonicMixViewModel
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.MixerScreen
import com.example.ui.screens.RoutingPresetsScreen
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.SonicMixTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SonicMixTheme {
                SonicMixStudioApp()
            }
        }
    }
}

@Composable
fun SonicMixStudioApp(
    viewModel: SonicMixViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onRecordPermissionResult(granted)
            if (!granted) {
                scope.launch {
                    snackbarHostState.showSnackbar("สามารถเปิดสเปกตรัมสดภายหลังได้ในสิทธิ์ไมโครโฟน/เสียง")
                }
            }
        },
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> },
    )

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Back gesture returns from Mixer / Routing tabs to Equalizer home tab
    BackHandler(enabled = uiState.selectedTab != AppTab.EQUALIZER) {
        viewModel.selectTab(AppTab.EQUALIZER)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isExpanded = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                StudioTopBar(
                    masterEnabled = uiState.mixerState.masterEnabled,
                    activePresetId = uiState.mixerState.activePresetId,
                    onToggleMaster = viewModel::toggleMasterEnabled,
                )
            },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar(
                        containerColor = ConsoleSurface,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = uiState.selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tabLabel(tab),
                                    )
                                },
                                label = { Text(tabLabel(tab), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isExpanded) {
                    NavigationRail(
                        containerColor = ConsoleSurface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = uiState.selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tabLabel(tab),
                                    )
                                },
                                label = { Text(tabLabel(tab)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1100.dp)

                    when (uiState.selectedTab) {
                        AppTab.EQUALIZER -> EqualizerScreen(
                            uiState = uiState,
                            isExpanded = isExpanded,
                            onBandChanged = viewModel::updateBandGain,
                            onModeChanged = viewModel::setEqMode,
                            onApplyPreset = viewModel::applyPreset,
                            onSavePreset = viewModel::saveCustomPreset,
                            onResetFlat = viewModel::resetFlat,
                            onToggleTestAudio = viewModel::toggleTestAudio,
                            onRequestVisualizerPermission = {
                                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = contentModifier,
                        )
                        AppTab.MIXER -> MixerScreen(
                            uiState = uiState,
                            isExpanded = isExpanded,
                            onPreampChange = viewModel::updatePreampDb,
                            onBassBoostChange = viewModel::updateBassBoost,
                            onVirtualizerChange = viewModel::updateVirtualizer,
                            onLoudnessChange = viewModel::updateLoudness,
                            onToggleCompressor = viewModel::toggleCompressor,
                            onCompressorDriveChange = viewModel::updateCompressorDrive,
                            onLimiterCeilingChange = viewModel::updateLimiterCeiling,
                            onBalanceChange = viewModel::updateStereoBalance,
                            onToggleMonoMix = viewModel::toggleMonoMix,
                            onReverbChange = viewModel::updateReverbPreset,
                            modifier = contentModifier,
                        )
                        AppTab.ROUTING -> RoutingPresetsScreen(
                            uiState = uiState,
                            onToggleBackgroundService = { enable ->
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasNotif = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!hasNotif) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                viewModel.toggleBackgroundService(enable)
                            },
                            onToggleAutoAttach = viewModel::toggleAutoAttachSessions,
                            onSelectSession = viewModel::selectTargetSession,
                            onApplyPreset = viewModel::applyPreset,
                            onDeletePreset = viewModel::deleteCustomPreset,
                            onExportPreset = viewModel::exportPresetJson,
                            onImportPresetJson = viewModel::importPresetJson,
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            modifier = contentModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    masterEnabled: Boolean,
    activePresetId: String,
    onToggleMaster: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleSurface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
            Text(
                text = if (masterEnabled) "DSP Active • ${activePresetId.uppercase()}" else "Bypass Mode",
                style = MaterialTheme.typography.labelSmall,
                color = if (masterEnabled) ElectricCyan else PeakCoral,
            )
        }

        Surface(
            onClick = onToggleMaster,
            shape = RoundedCornerShape(14.dp),
            color = if (masterEnabled) ElectricMint.copy(alpha = 0.16f) else ConsoleSurfaceElevated,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = if (masterEnabled) ElectricMint else ConsoleBorder,
                    shape = RoundedCornerShape(14.dp),
                )
                .minimumInteractiveComponentSize()
                .testTag("master_power_toggle"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = if (masterEnabled) {
                        stringResource(R.string.engine_active)
                    } else {
                        stringResource(R.string.engine_bypassed)
                    },
                    tint = if (masterEnabled) ElectricMint else TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (masterEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (masterEnabled) ElectricMint else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun tabLabel(tab: AppTab): String {
    return when (tab) {
        AppTab.EQUALIZER -> stringResource(R.string.tab_equalizer)
        AppTab.MIXER -> stringResource(R.string.tab_mixer)
        AppTab.ROUTING -> stringResource(R.string.tab_routing)
    }
}

private fun tabIcon(tab: AppTab, selected: Boolean) = when (tab) {
    AppTab.EQUALIZER -> if (selected) Icons.Filled.Equalizer else Icons.Outlined.Equalizer
    AppTab.MIXER -> if (selected) Icons.Filled.Tune else Icons.Outlined.Tune
    AppTab.ROUTING -> if (selected) Icons.Filled.SettingsInputComponent else Icons.Outlined.SettingsInputComponent
}
