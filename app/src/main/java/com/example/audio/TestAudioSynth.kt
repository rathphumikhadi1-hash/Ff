package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates a rich stereo reference groove (sub-bass pulse, warm chord harmonics, and crisp percussion)
 * through Android's AudioTrack so users can immediately audition EQ bands, Bass Boost, Spatializer,
 * Compressor, and Stereo Balance even when no external music app is open.
 */
class TestAudioSynth {

    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    var audioSessionId: Int = 0
        private set

    fun start(
        scope: CoroutineScope,
        onSessionCreated: (Int) -> Unit,
        onSampleFrame: (leftPeak: Float, rightPeak: Float, synthSpectrum: List<Float>) -> Unit,
    ) {
        if (isPlaying) return
        isPlaying = true

        playbackJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val minBufferBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            audioSessionId = track.audioSessionId
            onSessionCreated(audioSessionId)
            track.play()

            val framesPerWrite = 1024
            val pcm = ShortArray(framesPerWrite * 2)
            var sampleIndex = 0L

            // Musical chord progression frequencies (C minor7 -> Ab maj7 -> Fm9 -> G7)
            val chords = arrayOf(
                floatArrayOf(65.41f, 130.81f, 196.00f, 233.08f, 311.13f, 783.99f),
                floatArrayOf(51.91f, 103.83f, 207.65f, 261.63f, 311.13f, 932.33f),
                floatArrayOf(43.65f, 87.31f, 174.61f, 207.65f, 349.23f, 1046.50f),
                floatArrayOf(49.00f, 98.00f, 196.00f, 246.94f, 349.23f, 1174.66f),
            )

            while (isActive && isPlaying) {
                var peakL = 0f
                var peakR = 0f

                for (i in 0 until framesPerWrite) {
                    val t = sampleIndex.toDouble() / sampleRate.toDouble()
                    val beatPos = (t * 2.0) % 1.0 // 120 BPM beat
                    val barIdx = ((t * 0.5).toInt()) % chords.size
                    val chord = chords[barIdx]

                    // Sub-bass kick envelope
                    val kickEnv = kotlin.math.exp(-beatPos * 7.5).toFloat()
                    val kickFreq = 45.0 + 70.0 * kotlin.math.exp(-beatPos * 22.0)
                    val kick = sin(2.0 * PI * kickFreq * t).toFloat() * kickEnv * 0.38f

                    // Warm harmonic pad + bassline
                    var padL = 0f
                    var padR = 0f
                    for (h in chord.indices) {
                        val freq = chord[h].toDouble()
                        val osc = sin(2.0 * PI * freq * t).toFloat()
                        val weight = when (h) {
                            0 -> 0.22f
                            1 -> 0.16f
                            else -> 0.08f
                        }
                        val pan = if (h % 2 == 0) 0.85f else 1.15f
                        padL += osc * weight * (2f - pan) * 0.5f
                        padR += osc * weight * pan * 0.5f
                    }

                    // Crisp high-frequency air / hi-hat transient on off-beats
                    val hatPos = ((t * 4.0) % 1.0)
                    val hatEnv = kotlin.math.exp(-hatPos * 30.0).toFloat()
                    val hat = (Random.nextFloat() * 2f - 1f) * hatEnv * 0.07f

                    val left = (kick + padL + hat).coerceIn(-0.95f, 0.95f)
                    val right = (kick + padR - hat * 0.6f).coerceIn(-0.95f, 0.95f)

                    if (kotlin.math.abs(left) > peakL) peakL = kotlin.math.abs(left)
                    if (kotlin.math.abs(right) > peakR) peakR = kotlin.math.abs(right)

                    pcm[i * 2] = (left * Short.MAX_VALUE).toInt().toShort()
                    pcm[i * 2 + 1] = (right * Short.MAX_VALUE).toInt().toShort()
                    sampleIndex++
                }

                track.write(pcm, 0, pcm.size)

                // Emit approximate spectrum energy for smooth fallback if Visualizer permission isn't granted
                val tNow = sampleIndex.toDouble() / sampleRate.toDouble()
                val beatPulse = kotlin.math.exp(-((tNow * 2.0) % 1.0) * 6.5).toFloat()
                val hatPulse = kotlin.math.exp(-((tNow * 4.0) % 1.0) * 18.0).toFloat()
                val synthSpectrum = List(24) { bar ->
                    val norm = bar / 23f
                    val bassComponent = (1f - norm).coerceIn(0f, 1f) * (0.35f + 0.55f * beatPulse)
                    val midComponent = kotlin.math.exp(-((norm - 0.45f) * 4f) * ((norm - 0.45f) * 4f)) * 0.5f
                    val highComponent = norm * (0.15f + 0.45f * hatPulse)
                    (bassComponent * 0.7f + midComponent * 0.55f + highComponent * 0.5f)
                        .coerceIn(0.05f, 0.98f)
                }
                onSampleFrame(peakL, peakR, synthSpectrum)
            }

            runCatching {
                track.stop()
                track.release()
            }
            audioTrack = null
            audioSessionId = 0
            isPlaying = false
        }
    }

    fun stop() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
        audioSessionId = 0
    }
}
