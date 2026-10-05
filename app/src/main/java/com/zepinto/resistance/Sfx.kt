package com.zepinto.resistance

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin

enum class Cue { FLIP, WARN, BLIP, GOOD, BAD, WIN, LOSE }

/** The game's sounds, synthesised at start-up so the app needs no audio files. */
class Sfx {
    private val tracks: Map<Cue, AudioTrack?> = mapOf(
        Cue.FLIP to build(flip()),
        Cue.WARN to build(warn()),
        Cue.BLIP to build(blip()),
        Cue.GOOD to build(good()),
        Cue.BAD to build(bad()),
        Cue.WIN to build(win()),
        Cue.LOSE to build(lose()),
    )

    fun play(cue: Cue) {
        val track = tracks[cue] ?: return
        try {
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (_: IllegalStateException) {
            // a sound that fails to play must never break the game
        }
    }

    fun release() = tracks.values.forEach { it?.release() }

    private fun build(samples: ShortArray): AudioTrack? = try {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(samples, 0, samples.size) }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val RATE = 22_050

        /** A card flipped for the first time: two rising notes. */
        fun flip() = tone(1175.0, ms = 110, volume = 0.65) + tone(1568.0, ms = 240, volume = 0.65)

        /** A card looked at again: two harsh low pulses. */
        fun warn() = tone(150.0, ms = 170, volume = 0.55, square = true, decay = false) +
            ShortArray(RATE * 60 / 1000) +
            tone(120.0, ms = 230, volume = 0.55, square = true, decay = false)

        /** A secret choice made: the same short tick whatever was chosen, so the sound gives nothing away. */
        fun blip() = tone(880.0, ms = 70, volume = 0.5)

        /** An approved team or a successful mission: three rising notes. */
        fun good() = tone(1047.0, ms = 90, volume = 0.6) + tone(1319.0, ms = 90, volume = 0.6) + tone(1568.0, ms = 220, volume = 0.6)

        /** A rejected team or a failed mission: one low falling note, then a dull thud. */
        fun bad() = tone(300.0, 180.0, ms = 320, volume = 0.6) + tone(110.0, ms = 180, volume = 0.5, square = true, decay = true)

        /** The Resistance wins: a short rising fanfare. */
        fun win() = tone(523.0, ms = 120, volume = 0.6) + tone(659.0, ms = 120, volume = 0.6) +
            tone(784.0, ms = 120, volume = 0.6) + tone(1047.0, ms = 450, volume = 0.65)

        /** The Spies win: a long dark slide down. */
        fun lose(): ShortArray {
            val slide = tone(520.0, 55.0, ms = 1000, volume = 0.55, decay = false)
            val rumble = tone(70.0, 45.0, ms = 1000, volume = 0.45, square = true, decay = false)
            return ShortArray(slide.size) { ((slide[it] + rumble[it]) / 2).toShort() }
        }

        /** A note, optionally sliding from [f0] to [f1], with a short attack and release to avoid clicks. */
        fun tone(f0: Double, f1: Double = f0, ms: Int, volume: Double, square: Boolean = false, decay: Boolean = true): ShortArray {
            val n = RATE * ms / 1000
            val edge = RATE / 200 // 5 ms
            var phase = 0.0
            return ShortArray(n) { i ->
                phase += 2 * PI * (f0 + (f1 - f0) * i / n) / RATE
                val wave = if (square) sign(sin(phase)) else sin(phase)
                val env = (if (decay) exp(-3.5 * i / n) else 1.0) * minOf(1.0, i / edge.toDouble()) * minOf(1.0, (n - i) / edge.toDouble())
                (wave * env * volume * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }
}
