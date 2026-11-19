package com.example.model

enum class EqMode {
    TEN_BAND,
    FIVE_BAND,
}

data class EqBandSpec(
    val index: Int,
    val frequencyHz: Int,
    val label: String,
)

val TenBandSpecs = listOf(
    EqBandSpec(0, 31, "31"),
    EqBandSpec(1, 62, "62"),
    EqBandSpec(2, 125, "125"),
    EqBandSpec(3, 250, "250"),
    EqBandSpec(4, 500, "500"),
    EqBandSpec(5, 1000, "1K"),
    EqBandSpec(6, 2000, "2K"),
    EqBandSpec(7, 4000, "4K"),
    EqBandSpec(8, 8000, "8K"),
    EqBandSpec(9, 16000, "16K"),
)

val FiveBandSpecsDefault = listOf(
    EqBandSpec(0, 60, "60"),
    EqBandSpec(1, 230, "230"),
    EqBandSpec(2, 910, "910"),
    EqBandSpec(3, 3600, "3.6K"),
    EqBandSpec(4, 14000, "14K"),
)

enum class ReverbRoomOption(val presetId: Short, val label: String) {
    NONE(0, "ปิด (Dry)"),
    SMALL_ROOM(1, "ห้องเล็ก"),
    MEDIUM_ROOM(2, "ห้องกลาง"),
    LARGE_ROOM(3, "ห้องใหญ่"),
    MEDIUM_HALL(4, "ฮอลล์กลาง"),
    LARGE_HALL(5, "ฮอลล์ใหญ่"),
    PLATE(6, "Plate Studio"),
}

data class MixerEffectsState(
    val masterEnabled: Boolean = true,
    val eqMode: EqMode = EqMode.TEN_BAND,
    val bands10: List<Float> = List(10) { 0f },
    val bands5: List<Float> = List(5) { 0f },
    val preampGainDb: Float = 0f,
    val bassBoostStrength: Int = 0, // 0..1000
    val virtualizerStrength: Int = 0, // 0..1000
    val loudnessGainMb: Int = 0, // 0..1500 (0 to +15 dB)
    val reverbPreset: Short = 0, // 0..6
    val compressorEnabled: Boolean = false,
    val compressorDrive: Float = 0.35f, // 0f..1f
    val limiterCeilingDb: Float = -0.5f, // -6f..0f
    val stereoBalance: Float = 0f, // -1f (Left) .. 0f (Center) .. +1f (Right)
    val monoMixEnabled: Boolean = false,
    val activePresetId: String = "flat",
    val backgroundServiceEnabled: Boolean = true,
    val autoAttachSessions: Boolean = true,
)

data class EqPreset(
    val id: String,
    val name: String,
    val category: String,
    val isBuiltIn: Boolean,
    val bands10: List<Float>,
    val preampDb: Float = 0f,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudnessMb: Int = 0,
    val compressorOn: Boolean = false,
    val compressorDrive: Float = 0.35f,
    val reverbPreset: Short = 0,
)

data class ExternalAudioSession(
    val sessionId: Int,
    val packageName: String,
    val appLabel: String,
    val usageLabel: String,
    val isActive: Boolean,
    val isAttached: Boolean,
)

data class OutputDeviceInfo(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val sampleRateHz: Int,
)

data class HardwareCapabilities(
    val hasDynamicsProcessing: Boolean = false,
    val hasEqualizer: Boolean = false,
    val hardwareBandCount: Int = 5,
    val hardwareBandSpecs: List<EqBandSpec> = FiveBandSpecsDefault,
    val minGainDb: Float = -15f,
    val maxGainDb: Float = 15f,
    val hasBassBoost: Boolean = false,
    val hasVirtualizer: Boolean = false,
    val hasLoudnessEnhancer: Boolean = false,
    val hasReverb: Boolean = false,
    val attachStatusMessage: String = "พร้อมทำงาน (Global Mix #0)",
)

enum class AppTab {
    EQUALIZER,
    MIXER,
    ROUTING,
}
