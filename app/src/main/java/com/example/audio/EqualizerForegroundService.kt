package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PresetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EqualizerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "sonicmix_eq_channel"
        const val NOTIFICATION_ID = 4102
        const val ACTION_START = "com.example.action.START_EQ_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_EQ_SERVICE"
        const val ACTION_TOGGLE_MASTER = "com.example.action.TOGGLE_MASTER_EQ"

        fun startService(context: Context) {
            val intent = Intent(context, EqualizerForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, EqualizerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audioEngine: AudioEngineManager
    private lateinit var presetRepository: PresetRepository
    private val sessionReceiver = AudioSessionBroadcastReceiver()
    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        audioEngine = AudioEngineManager.getInstance(this)
        presetRepository = PresetRepository(this)
        createNotificationChannel()
        registerSessionReceiver()

        serviceScope.launch {
            presetRepository.mixerStateFlow.collect { state ->
                audioEngine.applyMixerState(state)
                updateNotification(
                    isEnabled = state.masterEnabled,
                    presetId = state.activePresetId,
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MASTER -> {
                serviceScope.launch {
                    val current = presetRepository.mixerStateFlow.first()
                    presetRepository.saveMixerState(current.copy(masterEnabled = !current.masterEnabled))
                }
            }
        }

        val notification = buildNotification(isEnabled = true, presetName = "VA SOUND")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(sessionReceiver) }
            receiverRegistered = false
        }
        serviceScope.cancel()
    }

    private fun registerSessionReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(
            this,
            sessionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.fgs_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(isEnabled: Boolean, presetId: String) {
        val presetName = presetRepository.builtInPresets.firstOrNull { it.id == presetId }?.name
            ?: if (presetId == "custom_unsaved") "Custom EQ" else "Active Studio EQ"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(isEnabled, presetName))
    }

    private fun buildNotification(isEnabled: Boolean, presetName: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, EqualizerForegroundService::class.java).apply {
                action = ACTION_TOGGLE_MASTER
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val statusText = if (isEnabled) "กำลังทำงาน • $presetName" else "บายพาส (ปิดชั่วคราว)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.fgs_notif_title))
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                0,
                if (isEnabled) "ปิด EQ ชั่วคราว" else "เปิด EQ",
                toggleIntent,
            )
            .build()
    }
}
