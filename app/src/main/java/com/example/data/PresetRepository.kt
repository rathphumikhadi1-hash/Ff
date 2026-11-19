package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.EqMode
import com.example.model.EqPreset
import com.example.model.MixerEffectsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.audioDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonicmix_eq_prefs")

class PresetRepository(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val EQ_MODE = stringPreferencesKey("eq_mode")
        val BANDS_10 = stringPreferencesKey("bands_10")
        val BANDS_5 = stringPreferencesKey("bands_5")
        val PREAMP_DB = floatPreferencesKey("preamp_db")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val VIRTUALIZER = intPreferencesKey("virtualizer")
        val LOUDNESS_MB = intPreferencesKey("loudness_mb")
        val REVERB_PRESET = intPreferencesKey("reverb_preset")
        val COMPRESSOR_ON = booleanPreferencesKey("compressor_on")
        val COMPRESSOR_DRIVE = floatPreferencesKey("compressor_drive")
        val LIMITER_CEILING = floatPreferencesKey("limiter_ceiling")
        val STEREO_BALANCE = floatPreferencesKey("stereo_balance")
        val MONO_MIX = booleanPreferencesKey("mono_mix")
        val ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")
        val BG_SERVICE = booleanPreferencesKey("bg_service")
        val AUTO_ATTACH = booleanPreferencesKey("auto_attach")
        val CUSTOM_PRESETS_JSON = stringPreferencesKey("custom_presets_json")
    }

    val builtInPresets: List<EqPreset> = listOf(
        EqPreset(
            id = "flat",
            name = "Flat (มาตรฐาน)",
            category = "Reference",
            isBuiltIn = true,
            bands10 = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        ),
        EqPreset(
            id = "bass_heavy",
            name = "Bass Boost (เบสหนัก)",
            category = "Bass",
            isBuiltIn = true,
            bands10 = listOf(7.5f, 6.5f, 5.0f, 2.5f, 0.5f, -0.5f, 0f, 1.5f, 2.5f, 3.0f),
            preampDb = -1.5f,
            bassBoost = 580,
            compressorOn = true,
            compressorDrive = 0.45f,
        ),
        EqPreset(
            id = "vocal_clarity",
            name = "Vocal Clarity (เสียงร้องชัด)",
            category = "Vocal",
            isBuiltIn = true,
            bands10 = listOf(-3.0f, -2.0f, -0.5f, 1.5f, 3.5f, 5.0f, 5.5f, 4.0f, 2.0f, 1.0f),
            virtualizer = 220,
            loudnessMb = 250,
        ),
        EqPreset(
            id = "edm_club",
            name = "EDM / Club",
            category = "Electronic",
            isBuiltIn = true,
            bands10 = listOf(6.5f, 5.5f, 3.0f, -1.5f, -2.0f, 0.5f, 3.0f, 4.5f, 6.0f, 5.0f),
            preampDb = -1.0f,
            bassBoost = 420,
            virtualizer = 450,
            compressorOn = true,
            compressorDrive = 0.55f,
        ),
        EqPreset(
            id = "rock_punch",
            name = "Rock Punch",
            category = "Rock",
            isBuiltIn = true,
            bands10 = listOf(4.5f, 3.5f, 1.5f, -1.0f, -2.0f, 1.0f, 3.5f, 4.5f, 5.0f, 4.0f),
            bassBoost = 250,
            compressorOn = true,
            compressorDrive = 0.4f,
        ),
        EqPreset(
            id = "acoustic_warm",
            name = "Acoustic Warm",
            category = "Acoustic",
            isBuiltIn = true,
            bands10 = listOf(1.5f, 2.5f, 2.0f, 1.5f, 0.5f, 1.0f, 2.5f, 3.0f, 3.5f, 2.5f),
            virtualizer = 320,
            reverbPreset = 1,
        ),
        EqPreset(
            id = "late_night",
            name = "Late Night (ฟังกลางคืน)",
            category = "Dynamic",
            isBuiltIn = true,
            bands10 = listOf(3.0f, 2.5f, 1.0f, 0f, 1.5f, 2.5f, 2.0f, 1.0f, 1.5f, 1.0f),
            loudnessMb = 450,
            compressorOn = true,
            compressorDrive = 0.7f,
        ),
        EqPreset(
            id = "gaming_3d",
            name = "3D Gaming (จับเสียงฝีเท้า)",
            category = "Gaming",
            isBuiltIn = true,
            bands10 = listOf(-1.5f, -1.0f, 0.5f, 2.0f, 3.0f, 4.5f, 5.5f, 6.0f, 4.5f, 3.0f),
            virtualizer = 720,
            compressorOn = true,
            compressorDrive = 0.5f,
        ),
    )

    val mixerStateFlow: Flow<MixerEffectsState> = context.audioDataStore.data.map { prefs ->
        MixerEffectsState(
            masterEnabled = prefs[Keys.MASTER_ENABLED] ?: true,
            eqMode = prefs[Keys.EQ_MODE]?.let { runCatching { EqMode.valueOf(it) }.getOrNull() }
                ?: EqMode.TEN_BAND,
            bands10 = parseFloatList(prefs[Keys.BANDS_10], 10),
            bands5 = parseFloatList(prefs[Keys.BANDS_5], 5),
            preampGainDb = prefs[Keys.PREAMP_DB] ?: 0f,
            bassBoostStrength = prefs[Keys.BASS_BOOST] ?: 0,
            virtualizerStrength = prefs[Keys.VIRTUALIZER] ?: 0,
            loudnessGainMb = prefs[Keys.LOUDNESS_MB] ?: 0,
            reverbPreset = (prefs[Keys.REVERB_PRESET] ?: 0).toShort(),
            compressorEnabled = prefs[Keys.COMPRESSOR_ON] ?: false,
            compressorDrive = prefs[Keys.COMPRESSOR_DRIVE] ?: 0.35f,
            limiterCeilingDb = prefs[Keys.LIMITER_CEILING] ?: -0.5f,
            stereoBalance = prefs[Keys.STEREO_BALANCE] ?: 0f,
            monoMixEnabled = prefs[Keys.MONO_MIX] ?: false,
            activePresetId = prefs[Keys.ACTIVE_PRESET_ID] ?: "flat",
            backgroundServiceEnabled = prefs[Keys.BG_SERVICE] ?: true,
            autoAttachSessions = prefs[Keys.AUTO_ATTACH] ?: true,
        )
    }

    val customPresetsFlow: Flow<List<EqPreset>> = context.audioDataStore.data.map { prefs ->
        parsePresetsJson(prefs[Keys.CUSTOM_PRESETS_JSON] ?: "[]")
    }

    suspend fun saveMixerState(state: MixerEffectsState) {
        context.audioDataStore.edit { prefs ->
            prefs[Keys.MASTER_ENABLED] = state.masterEnabled
            prefs[Keys.EQ_MODE] = state.eqMode.name
            prefs[Keys.BANDS_10] = state.bands10.joinToString(",")
            prefs[Keys.BANDS_5] = state.bands5.joinToString(",")
            prefs[Keys.PREAMP_DB] = state.preampGainDb
            prefs[Keys.BASS_BOOST] = state.bassBoostStrength
            prefs[Keys.VIRTUALIZER] = state.virtualizerStrength
            prefs[Keys.LOUDNESS_MB] = state.loudnessGainMb
            prefs[Keys.REVERB_PRESET] = state.reverbPreset.toInt()
            prefs[Keys.COMPRESSOR_ON] = state.compressorEnabled
            prefs[Keys.COMPRESSOR_DRIVE] = state.compressorDrive
            prefs[Keys.LIMITER_CEILING] = state.limiterCeilingDb
            prefs[Keys.STEREO_BALANCE] = state.stereoBalance
            prefs[Keys.MONO_MIX] = state.monoMixEnabled
            prefs[Keys.ACTIVE_PRESET_ID] = state.activePresetId
            prefs[Keys.BG_SERVICE] = state.backgroundServiceEnabled
            prefs[Keys.AUTO_ATTACH] = state.autoAttachSessions
        }
    }

    suspend fun saveCustomPreset(
        name: String,
        state: MixerEffectsState,
    ): EqPreset {
        val newPreset = EqPreset(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.trim().ifEmpty { "พรีเซ็ตของฉัน" },
            category = "Custom",
            isBuiltIn = false,
            bands10 = state.bands10,
            preampDb = state.preampGainDb,
            bassBoost = state.bassBoostStrength,
            virtualizer = state.virtualizerStrength,
            loudnessMb = state.loudnessGainMb,
            compressorOn = state.compressorEnabled,
            compressorDrive = state.compressorDrive,
            reverbPreset = state.reverbPreset,
        )
        context.audioDataStore.edit { prefs ->
            val current = parsePresetsJson(prefs[Keys.CUSTOM_PRESETS_JSON] ?: "[]").toMutableList()
            current.add(0, newPreset)
            prefs[Keys.CUSTOM_PRESETS_JSON] = serializePresetsJson(current)
            prefs[Keys.ACTIVE_PRESET_ID] = newPreset.id
        }
        return newPreset
    }

    suspend fun deleteCustomPreset(presetId: String) {
        context.audioDataStore.edit { prefs ->
            val current = parsePresetsJson(prefs[Keys.CUSTOM_PRESETS_JSON] ?: "[]")
                .filterNot { it.id == presetId }
            prefs[Keys.CUSTOM_PRESETS_JSON] = serializePresetsJson(current)
            if (prefs[Keys.ACTIVE_PRESET_ID] == presetId) {
                prefs[Keys.ACTIVE_PRESET_ID] = "custom_unsaved"
            }
        }
    }

    suspend fun importPresetJson(jsonText: String): EqPreset? {
        val obj = runCatching { JSONObject(jsonText.trim()) }.getOrNull() ?: return null
        val name = obj.optString("name", "Imported EQ")
        val bandsArr = obj.optJSONArray("bands10") ?: return null
        if (bandsArr.length() != 10) return null
        val bands10 = (0 until 10).map { bandsArr.optDouble(it, 0.0).toFloat().coerceIn(-15f, 15f) }
        val imported = EqPreset(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name,
            category = "Imported",
            isBuiltIn = false,
            bands10 = bands10,
            preampDb = obj.optDouble("preampDb", 0.0).toFloat().coerceIn(-12f, 12f),
            bassBoost = obj.optInt("bassBoost", 0).coerceIn(0, 1000),
            virtualizer = obj.optInt("virtualizer", 0).coerceIn(0, 1000),
            loudnessMb = obj.optInt("loudnessMb", 0).coerceIn(0, 1500),
            compressorOn = obj.optBoolean("compressorOn", false),
            compressorDrive = obj.optDouble("compressorDrive", 0.35).toFloat().coerceIn(0f, 1f),
            reverbPreset = obj.optInt("reverbPreset", 0).coerceIn(0, 6).toShort(),
        )
        context.audioDataStore.edit { prefs ->
            val current = parsePresetsJson(prefs[Keys.CUSTOM_PRESETS_JSON] ?: "[]").toMutableList()
            current.add(0, imported)
            prefs[Keys.CUSTOM_PRESETS_JSON] = serializePresetsJson(current)
        }
        return imported
    }

    fun exportPresetJson(preset: EqPreset): String {
        return presetToJsonObject(preset).toString(2)
    }

    private fun parseFloatList(raw: String?, expectedSize: Int): List<Float> {
        if (raw.isNullOrBlank()) return List(expectedSize) { 0f }
        val parsed = raw.split(",").mapNotNull { it.toFloatOrNull() }
        return if (parsed.size == expectedSize) parsed else List(expectedSize) { 0f }
    }

    private fun parsePresetsJson(raw: String): List<EqPreset> {
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val bandsArr = obj.optJSONArray("bands10") ?: return@mapNotNull null
                val bands10 = (0 until 10).map { i ->
                    bandsArr.optDouble(i, 0.0).toFloat().coerceIn(-15f, 15f)
                }
                EqPreset(
                    id = obj.optString("id", "custom_$idx"),
                    name = obj.optString("name", "Custom $idx"),
                    category = obj.optString("category", "Custom"),
                    isBuiltIn = false,
                    bands10 = bands10,
                    preampDb = obj.optDouble("preampDb", 0.0).toFloat(),
                    bassBoost = obj.optInt("bassBoost", 0),
                    virtualizer = obj.optInt("virtualizer", 0),
                    loudnessMb = obj.optInt("loudnessMb", 0),
                    compressorOn = obj.optBoolean("compressorOn", false),
                    compressorDrive = obj.optDouble("compressorDrive", 0.35).toFloat(),
                    reverbPreset = obj.optInt("reverbPreset", 0).toShort(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun serializePresetsJson(presets: List<EqPreset>): String {
        val arr = JSONArray()
        presets.forEach { preset -> arr.put(presetToJsonObject(preset)) }
        return arr.toString()
    }

    private fun presetToJsonObject(preset: EqPreset): JSONObject {
        return JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("category", preset.category)
            put("bands10", JSONArray(preset.bands10.map { it.toDouble() }))
            put("preampDb", preset.preampDb.toDouble())
            put("bassBoost", preset.bassBoost)
            put("virtualizer", preset.virtualizer)
            put("loudnessMb", preset.loudnessMb)
            put("compressorOn", preset.compressorOn)
            put("compressorDrive", preset.compressorDrive.toDouble())
            put("reverbPreset", preset.reverbPreset.toInt())
        }
    }
}
