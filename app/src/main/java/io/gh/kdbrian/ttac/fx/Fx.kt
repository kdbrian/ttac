package io.gh.kdbrian.ttac.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Sfx { TAP, PLACE_X, PLACE_O, WIN, LOSE, DRAW, CONNECT, MEDAL, MOVE, ROTATE, DROP, CLEAR, QUAD, FOUND, WRONG, HINT, UNLOCK }

/**
 * Sound and haptics. Every sound is synthesised at startup from sine/triangle oscillators with
 * simple envelopes — there are no audio assets.
 */
class Fx(context: Context) {

    var soundEnabled = true
    var hapticsEnabled = true

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val audio = Executors.newFixedThreadPool(3) { r -> Thread(r, "ttac-sfx").apply { isDaemon = true } }
    private val clips: Map<Sfx, ShortArray> by lazy { Synth.buildAll() }

    fun play(sfx: Sfx) {
        if (soundEnabled) audio.execute { playClip(clips.getValue(sfx)) }
        if (hapticsEnabled) buzz(sfx)
    }

    private fun playClip(pcm: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Synth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(pcm.size * 1000L / Synth.SAMPLE_RATE + 40)
            track.release()
        }
    }

    private fun buzz(sfx: Sfx) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (sfx) {
            Sfx.TAP -> VibrationEffect.createOneShot(8, 60)
            Sfx.PLACE_X, Sfx.PLACE_O -> VibrationEffect.createOneShot(14, 140)
            Sfx.WIN -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30, 60, 70), intArrayOf(0, 200, 0, 220, 0, 255), -1)
            Sfx.LOSE -> VibrationEffect.createWaveform(longArrayOf(0, 90, 80, 140), intArrayOf(0, 120, 0, 90), -1)
            Sfx.DRAW -> VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40), intArrayOf(0, 120, 0, 120), -1)
            Sfx.CONNECT -> VibrationEffect.createWaveform(longArrayOf(0, 20, 50, 20), intArrayOf(0, 160, 0, 200), -1)
            Sfx.MOVE -> VibrationEffect.createOneShot(5, 40)
            Sfx.ROTATE -> VibrationEffect.createOneShot(7, 70)
            Sfx.DROP -> VibrationEffect.createOneShot(22, 200)
            Sfx.CLEAR -> VibrationEffect.createWaveform(longArrayOf(0, 18, 30, 28), intArrayOf(0, 160, 0, 220), -1)
            Sfx.QUAD -> VibrationEffect.createWaveform(longArrayOf(0, 20, 25, 20, 25, 20, 25, 60), intArrayOf(0, 180, 0, 200, 0, 230, 0, 255), -1)
            Sfx.FOUND -> VibrationEffect.createWaveform(longArrayOf(0, 15, 35, 30), intArrayOf(0, 140, 0, 210), -1)
            Sfx.WRONG -> VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 30), intArrayOf(0, 90, 0, 90), -1)
            Sfx.HINT -> VibrationEffect.createOneShot(12, 90)
            Sfx.UNLOCK -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 160), intArrayOf(0, 160, 0, 200, 0, 255), -1)
            Sfx.MEDAL -> VibrationEffect.createWaveform(longArrayOf(0, 25, 40, 25, 40, 25, 60, 120), intArrayOf(0, 150, 0, 190, 0, 230, 0, 255), -1)
        }
        runCatching { v.vibrate(effect) }
    }

    fun release() = audio.shutdownNow()
}

internal object Synth {
    const val SAMPLE_RATE = 22_050

    private data class Note(val freq: Double, val start: Double, val dur: Double, val gain: Double = 0.5, val slideTo: Double? = null, val triangle: Boolean = false)

    fun buildAll(): Map<Sfx, ShortArray> = mapOf(
        Sfx.TAP to render(listOf(Note(1400.0, 0.0, 0.035, 0.25))),
        Sfx.PLACE_X to render(listOf(Note(520.0, 0.0, 0.11, 0.5, slideTo = 880.0, triangle = true))),
        Sfx.PLACE_O to render(listOf(Note(660.0, 0.0, 0.13, 0.5, slideTo = 440.0))),
        Sfx.WIN to render(
            listOf(523.25, 659.25, 783.99, 1046.5).mapIndexed { i, f -> Note(f, i * 0.09, 0.22, 0.42, triangle = i % 2 == 1) } +
                Note(1318.5, 0.36, 0.35, 0.3)
        ),
        Sfx.LOSE to render(listOf(Note(392.0, 0.0, 0.18, 0.45, triangle = true), Note(311.1, 0.16, 0.34, 0.45, slideTo = 220.0, triangle = true))),
        Sfx.DRAW to render(listOf(Note(587.3, 0.0, 0.14, 0.4), Note(587.3, 0.16, 0.2, 0.35, slideTo = 523.25))),
        Sfx.CONNECT to render(listOf(Note(880.0, 0.0, 0.08, 0.35), Note(1318.5, 0.08, 0.14, 0.35))),
        // A little fanfare with a shimmering top note.
        Sfx.MOVE to render(listOf(Note(900.0, 0.0, 0.025, 0.18))),
        Sfx.ROTATE to render(listOf(Note(700.0, 0.0, 0.05, 0.25, slideTo = 1050.0, triangle = true))),
        Sfx.DROP to render(listOf(Note(260.0, 0.0, 0.12, 0.55, slideTo = 110.0))),
        Sfx.CLEAR to render(listOf(Note(660.0, 0.0, 0.08, 0.35), Note(990.0, 0.06, 0.16, 0.35, triangle = true))),
        Sfx.QUAD to render(listOf(523.25, 659.25, 783.99, 1046.5, 1318.5).mapIndexed { i, f -> Note(f, i * 0.06, 0.2, 0.34, triangle = true) }),
        Sfx.FOUND to render(listOf(Note(784.0, 0.0, 0.09, 0.32), Note(1175.0, 0.07, 0.2, 0.3, triangle = true))),
        Sfx.WRONG to render(listOf(Note(220.0, 0.0, 0.1, 0.3, triangle = true), Note(196.0, 0.1, 0.14, 0.3, triangle = true))),
        Sfx.HINT to render(listOf(Note(1318.5, 0.0, 0.1, 0.22), Note(1760.0, 0.08, 0.18, 0.18))),
        Sfx.UNLOCK to render(
            listOf(392.0, 523.25, 659.25, 783.99, 1046.5, 1318.5).mapIndexed { i, f -> Note(f, i * 0.08, 0.26, 0.3, triangle = i % 2 == 0) } +
                Note(2093.0, 0.5, 0.6, 0.18)
        ),
        Sfx.MEDAL to render(
            listOf(783.99 to 0.0, 1046.5 to 0.1, 1318.5 to 0.2, 1567.98 to 0.3).map { (f, t) -> Note(f, t, 0.18, 0.35, triangle = true) } +
                listOf(Note(2093.0, 0.42, 0.5, 0.22), Note(2637.0, 0.46, 0.45, 0.14))
        ),
    )

    private fun render(notes: List<Note>): ShortArray {
        val total = notes.maxOf { it.start + it.dur } + 0.02
        val out = DoubleArray((total * SAMPLE_RATE).toInt())
        for (n in notes) {
            val startIdx = (n.start * SAMPLE_RATE).toInt()
            val len = (n.dur * SAMPLE_RATE).toInt()
            var phase = 0.0
            for (k in 0 until len) {
                val t = k.toDouble() / SAMPLE_RATE
                val progress = k.toDouble() / len
                val freq = n.slideTo?.let { n.freq + (it - n.freq) * progress } ?: n.freq
                phase += 2 * PI * freq / SAMPLE_RATE
                val wave = if (n.triangle) (2 / PI) * kotlin.math.asin(sin(phase)) else sin(phase)
                // Fast attack, exponential decay: a soft "pluck".
                val env = minOf(1.0, t / 0.004) * exp(-4.2 * progress)
                val i = startIdx + k
                if (i < out.size) out[i] += wave * env * n.gain
            }
        }
        return ShortArray(out.size) { i -> (out[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.8).toInt().toShort() }
    }
}
