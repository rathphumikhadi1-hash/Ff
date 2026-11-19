package com.example.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngineManager
import com.example.audio.EqualizerForegroundService
import com.example.data.PresetRepository
import com.example.model.AppTab
import com.example.model.EqBandSpec
import com.example.model.EqMode
import com.example.model.EqPreset
import com.example.model.ExternalAudioSession
import com.example.model.HardwareCapabilities
import com.example.model.MixerEffectsState
import com.example.model.OutputDeviceInfo
import com.example.model.TenBandSpecs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SonicMixUiState(
    val selectedTab: AppTab = AppTab.EQUALIZER,
    val mixerState: MixerEffectsState = MixerEffectsState(),
    val builtInPresets: List<EqPreset> = emptyList(),
    val customPresets: List<EqPreset> = emptyList(),
    val spectrumBars: List<Float> = List(24) { 0.04f },
    val peakLeftDb: Float = -60f,
    val peakRightDb: Float = -60f,
    val activeSessions: List<ExternalAudioSession> = emptyList(),
    val selectedSessionId: Int = 0,
    val outputDevices: List<OutputDeviceInfo> = emptyList(),
    val hardwareCapabilities: HardwareCapabilities = HardwareCapabilities(),
    val isTestAudioPlaying: Boolean = false,
    val hasRecordAudioPermission: Boolean = false,
) {
    val activeBandSpecs: List<EqBandSpec>
        get() = if (mixerState.eqMode == EqMode.TEN_BAND) {
            TenBandSpecs
        } else {
            hardwareCapabilities.hardwareBandSpecs
        }

    val activeBandGains: List<Float>
        get() = if (mixerState.eqMode == EqMode.TEN_BAND) {
            mixerState.bands10
        } else {
            val count = hardwareCapabilities.hardwareBandCount
            if (mixerState.bands5.size == count) mixerState.bands5
            else List(count) { idx -> mixerState.bands5.getOrElse(idx) { 0f } }
        }
}

class SonicMixViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = PresetRepository(context)
    private val audioEngine = AudioEngineManager.getInstance(context)

    private val _selectedTab = MutableStateFlow(AppTab.EQUALIZER)
    private val _hasRecordPermission = MutableStateFlow(checkRecordAudioPermission())
    private val _mixerState = MutableStateFlow(MixerEffectsState())
    private val _uiMessages = MutableSharedFlow<String>()
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    private var saveJob: Job? = null
    private var initializedFromDisk = false

    val uiState: StateFlow<SonicMixUiState> = combine(
        _selectedTab,
        _mixerState,
        repository.customPresetsFlow,
        audioEngine.spectrumBars,
        audioEngine.peakLeftDb,
        audioEngine.peakRightDb,
        audioEngine.discoveredSessions,
        audioEngine.selectedSessionId,
        audioEngine.outputDevices,
        audioEngine.hardwareCapabilities,
        audioEngine.isTestAudioPlaying,
        _hasRecordPermission,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tab = values[0] as AppTab
        val mixer = values[1] as MixerEffectsState
        @Suppress("UNCHECKED_CAST")
        val customPresets = values[2] as List<EqPreset>
        @Suppress("UNCHECKED_CAST")
        val bars = values[3] as List<Float>
        val peakL = values[4] as Float
        val peakR = values[5] as Float
        @Suppress("UNCHECKED_CAST")
        val sessions = values[6] as List<ExternalAudioSession>
        val selectedSession = values[7] as Int
        @Suppress("UNCHECKED_CAST")
        val devices = values[8] as List<OutputDeviceInfo>
        val caps = values[9] as HardwareCapabilities
        val testPlaying = values[10] as Boolean
        val hasPerm = values[11] as Boolean

        SonicMixUiState(
            selectedTab = tab,
            mixerState = mixer,
            builtInPresets = repository.builtInPresets,
            customPresets = customPresets,
            spectrumBars = bars,
            peakLeftDb = peakL,
            peakRightDb = peakR,
            activeSessions = sessions,
            selectedSessionId = selectedSession,
            outputDevices = devices,
            hardwareCapabilities = caps,
            isTestAudioPlaying = testPlaying,
            hasRecordAudioPermission = hasPerm,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SonicMixUiState(
            builtInPresets = repository.builtInPresets,
            hasRecordAudioPermission = checkRecordAudioPermission(),
        ),
    )

    init {
        viewModelScope.launch {
            repository.mixerStateFlow.collect { saved ->
                if (!initializedFromDisk) {
                    initializedFromDisk = true
                    _mixerState.value = saved
                    audioEngine.applyMixerState(saved)
                    if (saved.backgroundServiceEnabled) {
                        runCatching { EqualizerForegroundService.startService(context) }
                    }
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun onRecordPermissionResult(granted: Boolean) {
        _hasRecordPermission.value = granted
        if (granted) {
            audioEngine.refreshVisualizer()
            emitMessage("เปิดสเปกตรัมความถี่สดแล้ว")
        }
    }

    fun toggleMasterEnabled() {
        updateMixerState { it.copy(masterEnabled = !it.masterEnabled) }
    }

    fun setEqMode(mode: EqMode) {
        updateMixerState { it.copy(eqMode = mode) }
    }

    fun updateBandGain(index: Int, gainDb: Float) {
        val clamped = ((gainDb * 10f).roundToInt() / 10f).coerceIn(-15f, 15f)
        updateMixerState { state ->
            if (state.eqMode == EqMode.TEN_BAND) {
                val updated = state.bands10.toMutableList()
                if (index in updated.indices) updated[index] = clamped
                state.copy(bands10 = updated, activePresetId = "custom_unsaved")
            } else {
                val count = uiState.value.hardwareCapabilities.hardwareBandCount
                val updated = List(count) { idx ->
                    if (idx == index) clamped else state.bands5.getOrElse(idx) { 0f }
                }
                state.copy(bands5 = updated, activePresetId = "custom_unsaved")
            }
        }
    }

    fun resetFlat() {
        updateMixerState { state ->
            state.copy(
                bands10 = List(10) { 0f },
                bands5 = List(state.bands5.size.coerceAtLeast(5)) { 0f },
                activePresetId = "flat",
            )
        }
        emitMessage("รีเซ็ต EQ เป็น Flat (0 dB)")
    }

    fun applyPreset(preset: EqPreset) {
        val mapped5 = List(5) { idx ->
            val a = preset.bands10.getOrElse(idx * 2) { 0f }
            val b = preset.bands10.getOrElse(idx * 2 + 1) { 0f }
            ((a + b) * 0.5f)
        }
        updateMixerState { state ->
            state.copy(
                bands10 = preset.bands10,
                bands5 = mapped5,
                preampGainDb = preset.preampDb,
                bassBoostStrength = preset.bassBoost,
                virtualizerStrength = preset.virtualizer,
                loudnessGainMb = preset.loudnessMb,
                compressorEnabled = preset.compressorOn,
                compressorDrive = preset.compressorDrive,
                reverbPreset = preset.reverbPreset,
                activePresetId = preset.id,
            )
        }
        emitMessage("ใช้พรีเซ็ต: ${preset.name}")
    }

    fun saveCustomPreset(name: String) {
        viewModelScope.launch {
            val saved = repository.saveCustomPreset(name, _mixerState.value)
            _mixerState.value = _mixerState.value.copy(activePresetId = saved.id)
            emitMessage("บันทึกพรีเซ็ต \"${saved.name}\" แล้ว")
        }
    }

    fun deleteCustomPreset(presetId: String) {
        viewModelScope.launch {
            repository.deleteCustomPreset(presetId)
            emitMessage("ลบพรีเซ็ตแล้ว")
        }
    }

    fun exportPresetJson(preset: EqPreset): String {
        return repository.exportPresetJson(preset)
    }

    fun importPresetJson(rawJson: String) {
        viewModelScope.launch {
            val imported = repository.importPresetJson(rawJson)
            if (imported != null) {
                applyPreset(imported)
                emitMessage("นำเข้าพรีเซ็ต \"${imported.name}\" สำเร็จ")
            } else {
                emitMessage("รูปแบบ JSON ของพรีเซ็ตไม่ถูกต้อง")
            }
        }
    }

    fun updatePreampDb(gainDb: Float) {
        updateMixerState { it.copy(preampGainDb = gainDb.coerceIn(-12f, 12f), activePresetId = "custom_unsaved") }
    }

    fun updateBassBoost(strength: Int) {
        updateMixerState { it.copy(bassBoostStrength = strength.coerceIn(0, 1000)) }
    }

    fun updateVirtualizer(strength: Int) {
        updateMixerState { it.copy(virtualizerStrength = strength.coerceIn(0, 1000)) }
    }

    fun updateLoudness(gainMb: Int) {
        updateMixerState { it.copy(loudnessGainMb = gainMb.coerceIn(0, 1500)) }
    }

    fun updateReverbPreset(presetId: Short) {
        updateMixerState { it.copy(reverbPreset = presetId) }
    }

    fun toggleCompressor() {
        updateMixerState { it.copy(compressorEnabled = !it.compressorEnabled) }
    }

    fun updateCompressorDrive(drive: Float) {
        updateMixerState { it.copy(compressorDrive = drive.coerceIn(0f, 1f)) }
    }

    fun updateLimiterCeiling(ceilingDb: Float) {
        updateMixerState { it.copy(limiterCeilingDb = ceilingDb.coerceIn(-6f, 0f)) }
    }

    fun updateStereoBalance(balance: Float) {
        val snapped = if (kotlin.math.abs(balance) < 0.05f) 0f else balance.coerceIn(-1f, 1f)
        updateMixerState { it.copy(stereoBalance = snapped) }
    }

    fun toggleMonoMix() {
        updateMixerState { it.copy(monoMixEnabled = !it.monoMixEnabled) }
    }

    fun toggleBackgroundService(enabled: Boolean) {
        updateMixerState { it.copy(backgroundServiceEnabled = enabled) }
        if (enabled) {
            runCatching { EqualizerForegroundService.startService(context) }
            emitMessage("เปิดระบบ EQ ทำงานเบื้องหลังแล้ว")
        } else {
            runCatching { EqualizerForegroundService.stopService(context) }
            emitMessage("ปิดการทำงานเบื้องหลังแล้ว")
        }
    }

    fun toggleAutoAttachSessions(enabled: Boolean) {
        updateMixerState { it.copy(autoAttachSessions = enabled) }
    }

    fun selectTargetSession(sessionId: Int) {
        val target = if (sessionId < 0) 0 else sessionId
        audioEngine.selectTargetSession(target)
        emitMessage(
            if (target == 0) "รับเสียงจาก Global Output Mix (#0)"
            else "เชื่อมต่อ EQ กับเซสชันเสียง #$target"
        )
    }

    fun toggleTestAudio() {
        audioEngine.toggleTestAudio()
    }

    private fun updateMixerState(transform: (MixerEffectsState) -> MixerEffectsState) {
        val updated = transform(_mixerState.value)
        _mixerState.value = updated
        audioEngine.applyMixerState(updated)
        schedulePersist(updated)
    }

    private fun schedulePersist(state: MixerEffectsState) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(250)
            repository.saveMixerState(state)
        }
    }

    private fun emitMessage(msg: String) {
        viewModelScope.launch {
            _uiMessages.emit(msg)
        }
    }

    private fun checkRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
