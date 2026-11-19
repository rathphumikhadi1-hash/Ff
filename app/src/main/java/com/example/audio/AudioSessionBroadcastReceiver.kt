package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

class AudioSessionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId <= 0) return
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        val engine = AudioEngineManager.getInstance(context)

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                engine.onExternalSessionOpened(sessionId, packageName)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                engine.onExternalSessionClosed(sessionId)
            }
        }
    }
}
