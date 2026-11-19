package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PresetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VA SOUND", appName)
    }

    @Test
    fun `built in presets and json export import roundtrip`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PresetRepository(context)
        assertTrue(repo.builtInPresets.isNotEmpty())
        val bassPreset = repo.builtInPresets.first { it.id == "bass_heavy" }
        val json = repo.exportPresetJson(bassPreset)
        val imported = repo.importPresetJson(json)
        assertNotNull(imported)
        assertEquals(bassPreset.name, imported!!.name)
        assertEquals(bassPreset.bands10, imported.bands10)
        val savedCustom = repo.customPresetsFlow.first()
        assertTrue(savedCustom.any { it.id == imported.id })
    }
}
