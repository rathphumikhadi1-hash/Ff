package com.example.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.model.EqBandSpec
import com.example.model.EqMode
import com.example.model.ExternalAudioSession
import com.example.model.FiveBandSpecsDefault
import com.example.model.HardwareCapabilities
import com.example.model.MixerEffectsState
import com.example.model.OutputDeviceInfo
import com.example.model.TenBandSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Core Android Audio DSP & Cross-App Session Engine.
 *
 * Attaches real Android AudioEffects (DynamicsProcessing 10-band EQ + MBC + Limiter on API 28+,
 * hardware Equalizer, BassBoost, Virtualizer, LoudnessEnhancer, PresetReverb) to:
 * - Global Output Mix (audioSessionId = 0) so any media app receives EQ processing, AND/OR
 * - Specific external app audio sessions captured via ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
 *   or active playback configurations.
 */
class AudioEngineManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "SonicMixAudioEngine"

        @Volatile
        private var instance: AudioEngineManager? = null

        fun getInstance(context: Context): AudioEngineManager {
            return instance ?: synchronized(this) {
                instance ?: AudioEngineManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Active target session (0 = Global Output Mix across all apps)
    private val _selectedSessionId = MutableStateFlow(0)
    val selectedSessionId: StateFlow<Int> = _selectedSessionId.asStateFlow()

    private val _discoveredSessions = MutableStateFlow<List<ExternalAudioSession>>(emptyList())
    val discoveredSessions: StateFlow<List<ExternalAudioSession>> = _discoveredSessions.asStateFlow()

    private val _outputDevices = MutableStateFlow<List<OutputDeviceInfo>>(emptyList())
    val outputDevices: StateFlow<List<OutputDeviceInfo>> = _outputDevices.asStateFlow()

    private val _hardwareCapabilities = MutableStateFlow(HardwareCapabilities())
    val hardwareCapabilities: StateFlow<HardwareCapabilities> = _hardwareCapabilities.asStateFlow()

    private val _spectrumBars = MutableStateFlow(List(24) { 0.04f })
    val spectrumBars: StateFlow<List<Float>> = _spectrumBars.asStateFlow()

    private val _peakLeftDb = MutableStateFlow(-60f)
    val peakLeftDb: StateFlow<Float> = _peakLeftDb.asStateFlow()

    private val _peakRightDb = MutableStateFlow(-60f)
    val peakRightDb: StateFlow<Float> = _peakRightDb.asStateFlow()

    private val _isTestAudioPlaying = MutableStateFlow(false)
    val isTestAudioPlaying: StateFlow<Boolean> = _isTestAudioPlaying.asStateFlow()

    private var latestState: MixerEffectsState = MixerEffectsState()

    // Effect bundles keyed by audioSessionId (usually session 0 + any auto-attached external sessions)
    private class SessionEffectBundle(val sessionId: Int) {
        var dynamicsProcessing: DynamicsProcessing? = null
        var equalizer: Equalizer? = null
        var bassBoost: BassBoost? = null
        var virtualizer: Virtualizer? = null
        var loudnessEnhancer: LoudnessEnhancer? = null
        var presetReverb: PresetReverb? = null

        fun releaseAll() {
            runCatching { dynamicsProcessing?.release() }
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
            runCatching { virtualizer?.release() }
            runCatching { loudnessEnhancer?.release() }
            runCatching { presetReverb?.release() }
            dynamicsProcessing = null
            equalizer = null
            bassBoost = null
            virtualizer = null
            loudnessEnhancer = null
            presetReverb = null
        }
    }

    private val effectBundles = mutableMapOf<Int, SessionEffectBundle>()
    private var visualizer: Visualizer? = null
    private val testSynth = TestAudioSynth()
    private val knownExternalSessions = linkedMapOf<Int, ExternalAudioSession>()

    private var playbackCallbackRegistered = false
    private val playbackCallback: AudioManager.AudioPlaybackCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    refreshActivePlaybackSessions(configs ?: emptyList())
                }
            }
        } else null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDevices()
        }
    }

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        refreshOutputDevices()
        audioManager.registerAudioDeviceCallback(deviceCallback, null)

        val cb = playbackCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && cb != null && !playbackCallbackRegistered) {
            runCatching {
                audioManager.registerAudioPlaybackCallback(cb, null)
                playbackCallbackRegistered = true
                refreshActivePlaybackSessions(audioManager.activePlaybackConfigurations)
            }
        }

        // Attach primary Global Output Mix (session 0)
        attachEffectsToSession(0)
        refreshVisualizer()
    }

    fun selectTargetSession(sessionId: Int) {
        _selectedSessionId.value = sessionId
        if (!effectBundles.containsKey(sessionId)) {
            attachEffectsToSession(sessionId)
        }
        applyMixerState(latestState)
        refreshVisualizer()
        updateDiscoveredSessionsList()
    }

    fun onExternalSessionOpened(sessionId: Int, packageName: String?) {
        if (sessionId <= 0) return
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: "external.audio"
        val label = resolveAppLabel(pkg)
        knownExternalSessions[sessionId] = ExternalAudioSession(
            sessionId = sessionId,
            packageName = pkg,
            appLabel = label,
            usageLabel = "Media Session",
            isActive = true,
            isAttached = latestState.autoAttachSessions || _selectedSessionId.value == sessionId,
        )
        if (latestState.autoAttachSessions) {
            attachEffectsToSession(sessionId)
            applyStateToBundle(effectBundles[sessionId], latestState)
        }
        updateDiscoveredSessionsList()
    }

    fun onExternalSessionClosed(sessionId: Int) {
        if (sessionId <= 0) return
        knownExternalSessions.remove(sessionId)
        effectBundles.remove(sessionId)?.releaseAll()
        if (_selectedSessionId.value == sessionId) {
            _selectedSessionId.value = 0
            refreshVisualizer()
        }
        updateDiscoveredSessionsList()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshActivePlaybackSessions(configs: List<AudioPlaybackConfiguration>) {
        val activeStreams = configs.mapIndexed { idx, cfg ->
            val attrs = cfg.audioAttributes
            val usageLabel = when (attrs.usage) {
                AudioAttributes.USAGE_MEDIA -> "สื่อ / เพลง / วิดีโอ"
                AudioAttributes.USAGE_GAME -> "เกม (Game Audio)"
                AudioAttributes.USAGE_VOICE_COMMUNICATION -> "การโทร / สื่อสาร"
                AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "นำทาง (Navigation)"
                else -> "ระบบเสียงอื่น"
            }
            ExternalAudioSession(
                sessionId = -(idx + 1), // Config doesn't expose raw sessionId on public API; routed via Global Mix #0
                packageName = "stream.$idx",
                appLabel = "สตรีมเสียงที่กำลังเล่น #${idx + 1}",
                usageLabel = usageLabel,
                isActive = true,
                isAttached = true,
            )
        }
        val combined = mutableListOf<ExternalAudioSession>()
        combined.addAll(knownExternalSessions.values)
        // Add live stream indicators when active playback is detected without session broadcast
        activeStreams.forEach { stream ->
            if (combined.none { it.usageLabel == stream.usageLabel && it.isActive }) {
                combined.add(stream)
            }
        }
        _discoveredSessions.value = combined
    }

    private fun updateDiscoveredSessionsList() {
        val currentSelected = _selectedSessionId.value
        val updated = knownExternalSessions.values.map { session ->
            session.copy(isAttached = effectBundles.containsKey(session.sessionId) || currentSelected == session.sessionId)
        }
        _discoveredSessions.value = updated
    }

    fun refreshOutputDevices() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val mapped = devices.map { dev ->
            val typeStr = when (dev.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "ลำโพงเครื่อง"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Call"
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "หูฟังมีสาย 3.5mm"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB DAC / Audio"
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "LE Audio"
                else -> "เอาต์พุตเสียง"
            }
            val rate = dev.sampleRates.maxOrNull() ?: 48000
            OutputDeviceInfo(
                id = dev.id,
                name = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeStr,
                typeLabel = typeStr,
                sampleRateHz = rate,
            )
        }.distinctBy { "${it.typeLabel}_${it.name}" }
        _outputDevices.value = mapped
    }

    @Synchronized
    private fun attachEffectsToSession(sessionId: Int) {
        if (sessionId < 0) return
        if (effectBundles.containsKey(sessionId)) return

        val bundle = SessionEffectBundle(sessionId)
        var statusNote = if (sessionId == 0) "เชื่อมต่อ Global Mix #0 สำเร็จ" else "เชื่อมต่อ Session #$sessionId สำเร็จ"

        // 1. DynamicsProcessing (API 28+): 10-band Pre-EQ + Input Gain + MBC + Limiter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, 10,
                    true, 3,
                    false, 0,
                    true,
                ).build()
                bundle.dynamicsProcessing = DynamicsProcessing(0, sessionId, cfg)
            }.onFailure { err ->
                Log.w(TAG, "DynamicsProcessing unavailable on session $sessionId: ${err.message}")
            }
        }

        // 2. Classic Hardware Equalizer
        runCatching {
            bundle.equalizer = Equalizer(0, sessionId)
        }.onFailure { err ->
            Log.w(TAG, "Equalizer unavailable on session $sessionId: ${err.message}")
            if (bundle.dynamicsProcessing == null) {
                statusNote = "อุปกรณ์จำกัด Session #0 (รอจับเซสชันจากแอปเล่นเพลง)"
            }
        }

        // 3. BassBoost
        runCatching {
            bundle.bassBoost = BassBoost(0, sessionId)
        }.onFailure { err ->
            Log.w(TAG, "BassBoost unavailable on session $sessionId: ${err.message}")
        }

        // 4. Virtualizer
        runCatching {
            bundle.virtualizer = Virtualizer(0, sessionId)
        }.onFailure { err ->
            Log.w(TAG, "Virtualizer unavailable on session $sessionId: ${err.message}")
        }

        // 5. LoudnessEnhancer
        runCatching {
            bundle.loudnessEnhancer = LoudnessEnhancer(sessionId)
        }.onFailure { err ->
            Log.w(TAG, "LoudnessEnhancer unavailable on session $sessionId: ${err.message}")
        }

        // 6. PresetReverb (on session 0 or specific session)
        runCatching {
            bundle.presetReverb = PresetReverb(0, sessionId)
        }.onFailure { err ->
            Log.w(TAG, "PresetReverb unavailable on session $sessionId: ${err.message}")
        }

        effectBundles[sessionId] = bundle
        if (sessionId == 0 || sessionId == _selectedSessionId.value) {
            updateCapabilitiesSnapshot(bundle, statusNote)
        }
    }

    private fun updateCapabilitiesSnapshot(bundle: SessionEffectBundle, statusNote: String) {
        val eq = bundle.equalizer
        val hwBands = eq?.numberOfBands?.toInt() ?: 5
        val range = eq?.bandLevelRange
        val minDb = (range?.getOrNull(0)?.toFloat() ?: -1500f) / 100f
        val maxDb = (range?.getOrNull(1)?.toFloat() ?: 1500f) / 100f
        val specs = if (eq != null) {
            (0 until hwBands).map { i ->
                val hz = eq.getCenterFreq(i.toShort()) / 1000
                val label = if (hz >= 1000) {
                    val k = hz / 1000f
                    if (k % 1f == 0f) "${k.toInt()}K" else String.format("%.1fK", k)
                } else {
                    "$hz"
                }
                EqBandSpec(i, hz, label)
            }
        } else {
            FiveBandSpecsDefault
        }

        _hardwareCapabilities.value = HardwareCapabilities(
            hasDynamicsProcessing = bundle.dynamicsProcessing != null,
            hasEqualizer = eq != null,
            hardwareBandCount = hwBands,
            hardwareBandSpecs = specs,
            minGainDb = minDb,
            maxGainDb = maxDb,
            hasBassBoost = bundle.bassBoost != null,
            hasVirtualizer = bundle.virtualizer != null,
            hasLoudnessEnhancer = bundle.loudnessEnhancer != null,
            hasReverb = bundle.presetReverb != null,
            attachStatusMessage = statusNote,
        )
    }

    @Synchronized
    fun applyMixerState(state: MixerEffectsState) {
        latestState = state
        if (effectBundles.isEmpty()) {
            attachEffectsToSession(0)
        }
        effectBundles.values.forEach { bundle ->
            applyStateToBundle(bundle, state)
        }
    }

    private fun applyStateToBundle(bundle: SessionEffectBundle?, state: MixerEffectsState) {
        if (bundle == null) return
        val enabled = state.masterEnabled

        // A. DynamicsProcessing (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bundle.dynamicsProcessing?.let { dp ->
                applyDynamicsProcessing(dp, state, enabled)
            }
        }

        // B. Classic Equalizer (used when FIVE_BAND mode OR when DynamicsProcessing is unavailable)
        val useClassicEq = state.eqMode == EqMode.FIVE_BAND || bundle.dynamicsProcessing == null
        bundle.equalizer?.let { eq ->
            runCatching {
                eq.enabled = enabled && useClassicEq
                if (enabled && useClassicEq) {
                    val bandCount = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()
                    for (b in 0 until bandCount) {
                        val db = if (state.eqMode == EqMode.FIVE_BAND) {
                            state.bands5.getOrElse(b) { 0f }
                        } else {
                            // Map 10-band curve down to hardware bands
                            val mappedIdx = ((b.toFloat() / bandCount.coerceAtLeast(1)) * 9f).roundToInt().coerceIn(0, 9)
                            state.bands10.getOrElse(mappedIdx) { 0f }
                        }
                        val targetMb = (db * 100f).roundToInt().coerceIn(minMb, maxMb).toShort()
                        eq.setBandLevel(b.toShort(), targetMb)
                    }
                }
            }
        }

        // C. Bass Boost
        bundle.bassBoost?.let { bb ->
            runCatching {
                bb.enabled = enabled && state.bassBoostStrength > 0
                if (bb.strengthSupported && enabled) {
                    bb.setStrength(state.bassBoostStrength.coerceIn(0, 1000).toShort())
                }
            }
        }

        // D. 3D Virtualizer
        bundle.virtualizer?.let { virt ->
            runCatching {
                virt.enabled = enabled && state.virtualizerStrength > 0
                if (virt.strengthSupported && enabled) {
                    virt.setStrength(state.virtualizerStrength.coerceIn(0, 1000).toShort())
                }
            }
        }

        // E. Loudness Enhancer
        bundle.loudnessEnhancer?.let { le ->
            runCatching {
                le.enabled = enabled && state.loudnessGainMb > 0
                if (enabled) {
                    le.setTargetGain(state.loudnessGainMb.coerceIn(0, 1500))
                }
            }
        }

        // F. Room Reverb
        bundle.presetReverb?.let { rev ->
            runCatching {
                val active = enabled && state.reverbPreset > 0
                rev.enabled = active
                if (active) {
                    rev.preset = state.reverbPreset
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyDynamicsProcessing(
        dp: DynamicsProcessing,
        state: MixerEffectsState,
        enabled: Boolean,
    ) {
        runCatching {
            dp.enabled = enabled
            if (!enabled) return

            // 1. Preamp + Stereo L/R Balance per channel
            val leftTrimDb = if (state.stereoBalance > 0f) -state.stereoBalance * 24f else 0f
            val rightTrimDb = if (state.stereoBalance < 0f) state.stereoBalance * 24f else 0f
            dp.setInputGainbyChannel(0, state.preampGainDb + leftTrimDb)
            dp.setInputGainbyChannel(1, state.preampGainDb + rightTrimDb)

            // 2. 10-Band Pre-EQ
            val use10Band = state.eqMode == EqMode.TEN_BAND
            val cutoffs = TenBandSpecs.map { it.frequencyHz.toFloat() }
            for (i in 0 until 10) {
                val gainDb = if (use10Band) state.bands10.getOrElse(i) { 0f } else 0f
                dp.setPreEqBandAllChannelsTo(
                    i,
                    DynamicsProcessing.EqBand(use10Band, cutoffs[i], gainDb),
                )
            }

            // 3. 3-Band Multi-Band Compressor (Low 250Hz, Mid 2500Hz, High 16000Hz)
            val mbcCutoffs = floatArrayOf(250f, 2500f, 16000f)
            val drive = state.compressorDrive.coerceIn(0f, 1f)
            val thresholdDb = -6f - drive * 22f // -6dB down to -28dB
            val ratio = 1.5f + drive * 4.5f // 1.5:1 up to 6.0:1
            val makeupGainDb = if (state.compressorEnabled) drive * 4.5f else 0f
            for (b in 0 until 3) {
                dp.setMbcBandAllChannelsTo(
                    b,
                    DynamicsProcessing.MbcBand(
                        state.compressorEnabled,
                        mbcCutoffs[b],
                        8f, // attack ms
                        90f, // release ms
                        ratio,
                        thresholdDb,
                        6f, // knee width
                        -90f, // noise gate
                        1f, // expander ratio
                        0f,
                        makeupGainDb,
                    ),
                )
            }

            // 4. Master Output Limiter
            dp.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    1f,
                    60f,
                    10f,
                    state.limiterCeilingDb.coerceIn(-6f, 0f),
                    0f,
                )
            )
        }.onFailure { err ->
            Log.w(TAG, "Failed updating DynamicsProcessing: ${err.message}")
        }
    }

    fun refreshVisualizer() {
        val hasPerm = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        runCatching { visualizer?.enabled = false; visualizer?.release() }
        visualizer = null

        if (!hasPerm) return

        val targetSession = if (testSynth.isPlaying && testSynth.audioSessionId > 0) {
            testSynth.audioSessionId
        } else {
            _selectedSessionId.value.coerceAtLeast(0)
        }

        runCatching {
            val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            val vis = Visualizer(targetSession).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            waveform ?: return
                            var sumSq = 0.0
                            for (b in waveform) {
                                val norm = ((b.toInt() and 0xFF) - 128) / 128.0
                                sumSq += norm * norm
                            }
                            val rms = kotlin.math.sqrt(sumSq / waveform.size.coerceAtLeast(1)).toFloat()
                            val db = if (rms > 0.0005f) (20f * log10(rms)).coerceIn(-60f, 0f) else -60f
                            val bal = latestState.stereoBalance
                            val trimL = if (bal > 0f) -bal * 18f else 0f
                            val trimR = if (bal < 0f) bal * 18f else 0f
                            _peakLeftDb.value = (db + trimL).coerceIn(-60f, 0f)
                            _peakRightDb.value = (db + trimR).coerceIn(-60f, 0f)
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            fft ?: return
                            val bars = computeLogSpectrumBars(fft, 24)
                            _spectrumBars.value = bars
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true,
                )
                enabled = true
            }
            visualizer = vis
        }.onFailure { err ->
            Log.w(TAG, "Visualizer init failed on session $targetSession: ${err.message}")
        }
    }

    private fun computeLogSpectrumBars(fft: ByteArray, barCount: Int): List<Float> {
        val n = fft.size / 2
        if (n <= 2) return List(barCount) { 0.04f }
        val magnitudes = FloatArray(n)
        magnitudes[0] = abs(fft[0].toFloat())
        for (k in 1 until n) {
            val real = fft[2 * k].toFloat()
            val imag = fft[2 * k + 1].toFloat()
            magnitudes[k] = hypot(real, imag)
        }
        val prev = _spectrumBars.value
        return List(barCount) { bar ->
            val startFrac = (bar.toFloat() / barCount)
            val endFrac = ((bar + 1).toFloat() / barCount)
            // Exponential bin mapping so low/mid frequencies get finer resolution
            val startBin = (1 + (startFrac * startFrac * (n - 2))).toInt().coerceIn(1, n - 1)
            val endBin = (1 + (endFrac * endFrac * (n - 2))).toInt().coerceIn(startBin, n - 1)
            var maxMag = 0f
            for (b in startBin..endBin) {
                if (magnitudes[b] > maxMag) maxMag = magnitudes[b]
            }
            val dbNorm = ((20f * log10(maxMag + 1f)) / 42f).coerceIn(0.03f, 1f)
            // Smooth temporal decay
            val oldVal = prev.getOrElse(bar) { 0.04f }
            if (dbNorm > oldVal) dbNorm else (oldVal * 0.82f + dbNorm * 0.18f).coerceIn(0.03f, 1f)
        }
    }

    fun toggleTestAudio() {
        if (testSynth.isPlaying) {
            testSynth.stop()
            _isTestAudioPlaying.value = false
            _peakLeftDb.value = -60f
            _peakRightDb.value = -60f
            _spectrumBars.value = List(24) { 0.04f }
            refreshVisualizer()
        } else {
            _isTestAudioPlaying.value = true
            testSynth.start(
                scope = scope,
                onSessionCreated = { sessionId ->
                    if (sessionId > 0) {
                        attachEffectsToSession(sessionId)
                        applyStateToBundle(effectBundles[sessionId], latestState)
                        refreshVisualizer()
                    }
                },
                onSampleFrame = { peakL, peakR, synthBars ->
                    // Use direct synth telemetry when Visualizer isn't active (e.g., permission not granted)
                    if (visualizer == null) {
                        val bal = latestState.stereoBalance
                        val lGain = if (bal > 0f) 1f - bal else 1f
                        val rGain = if (bal < 0f) 1f + bal else 1f
                        val monoLevel = (peakL + peakR) * 0.5f
                        val effL = if (latestState.monoMixEnabled) monoLevel * lGain else peakL * lGain
                        val effR = if (latestState.monoMixEnabled) monoLevel * rGain else peakR * rGain
                        val boost = if (latestState.masterEnabled) {
                            1f + (latestState.preampGainDb / 24f) + (latestState.loudnessGainMb / 3000f)
                        } else 1f
                        _peakLeftDb.value = if (effL > 0.001f) (20f * log10((effL * boost).coerceAtLeast(0.001f))).coerceIn(-60f, 0f) else -60f
                        _peakRightDb.value = if (effR > 0.001f) (20f * log10((effR * boost).coerceAtLeast(0.001f))).coerceIn(-60f, 0f) else -60f
                        _spectrumBars.value = synthBars
                    }
                },
            )
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            val pm = appContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrElse { packageName }
    }

    fun release() {
        testSynth.stop()
        runCatching { visualizer?.enabled = false; visualizer?.release() }
        visualizer = null
        effectBundles.values.forEach { it.releaseAll() }
        effectBundles.clear()
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        val cb = playbackCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && cb != null && playbackCallbackRegistered) {
            runCatching { audioManager.unregisterAudioPlaybackCallback(cb) }
            playbackCallbackRegistered = false
        }
    }
}
