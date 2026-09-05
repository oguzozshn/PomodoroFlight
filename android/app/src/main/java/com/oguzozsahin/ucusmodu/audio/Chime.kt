package com.oguzozsahin.ucusmodu.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

enum class ChimeKind { LEVEL, LAND, BOARDING }

/**
 * Web surumundeki WebAudio kabin cani, ayni notalarla PCM olarak sentezlenir.
 * Uctan uca hesaplandigi icin ses dosyasi tasimaya gerek yok.
 */
object Chime {

    private const val SAMPLE_RATE = 22050
    private const val TONE_SECONDS = 1.0
    private const val ATTACK = 0.012
    private const val PEAK = 0.16
    private const val TAIL = 0.0008

    private val cache = HashMap<ChimeKind, ShortArray>()
    private var current: AudioTrack? = null

    /** Notalar: frekans -> baslangic gecikmesi (saniye). */
    private fun tonesOf(kind: ChimeKind): List<Pair<Double, Double>> = when (kind) {
        ChimeKind.LAND -> listOf(659.0 to 0.0, 523.0 to 0.22, 392.0 to 0.44)
        ChimeKind.LEVEL -> listOf(880.0 to 0.0, 659.0 to 0.26)
        ChimeKind.BOARDING -> listOf(659.0 to 0.0, 880.0 to 0.24)
    }

    @Synchronized
    fun play(kind: ChimeKind) {
        val pcm = cache.getOrPut(kind) { render(kind) }
        release()
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            current = track
            val ms = (pcm.size * 1000L / SAMPLE_RATE) + 250L
            Handler(Looper.getMainLooper()).postDelayed({
                if (current === track) release()
            }, ms)
        } catch (e: Exception) {
            current = null
        }
    }

    @Synchronized
    fun release() {
        current?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // zaten durmus olabilir
            }
            it.release()
        }
        current = null
    }

    private fun render(kind: ChimeKind): ShortArray {
        val tones = tonesOf(kind)
        val total = ((tones.maxOf { it.second } + TONE_SECONDS) * SAMPLE_RATE).toInt()
        val buf = DoubleArray(total)
        val decay = ln(TAIL / PEAK) / (0.9 - ATTACK)

        for ((freq, offset) in tones) {
            val start = (offset * SAMPLE_RATE).toInt()
            val len = (TONE_SECONDS * SAMPLE_RATE).toInt()
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= total) break
                val t = i.toDouble() / SAMPLE_RATE
                val env = if (t < ATTACK) PEAK * (t / ATTACK) else PEAK * exp(decay * (t - ATTACK))
                // ucgen dalga: WebAudio "triangle" osilatorunun karsiligi
                val triangle = 2.0 / PI * asin(sin(2.0 * PI * freq * t))
                buf[idx] += triangle * env
            }
        }

        val out = ShortArray(total)
        for (i in 0 until total) {
            val v = (buf[i] * 4.0).coerceIn(-1.0, 1.0)
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
