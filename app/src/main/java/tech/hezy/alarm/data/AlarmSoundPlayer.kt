package tech.hezy.alarm.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import tech.hezy.alarm.domain.AlarmTone
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class AlarmSoundPlayer {
    private var track: AudioTrack? = null

    fun play(tone: AlarmTone) {
        stop()
        val pcm = synthesize(tone)
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
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
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        audio.write(pcm, 0, pcm.size)
        audio.setLoopPoints(0, pcm.size - 1, -1)
        audio.play()
        track = audio
    }

    fun stop() {
        track?.runCatching {
            pause()
            flush()
            release()
        }
        track = null
    }

    private fun synthesize(tone: AlarmTone): ShortArray {
        val total = SAMPLE_RATE * 2
        val out = ShortArray(total)
        val base = when (tone) {
            AlarmTone.Rise -> 523.25
            AlarmTone.Pulse -> 392.00
            AlarmTone.Glass -> 987.77
            AlarmTone.Harbor -> 329.63
            AlarmTone.Clarity -> 659.25
        }
        val intervals = doubleArrayOf(1.0, 5.0 / 4.0, 3.0 / 2.0, 2.0)
        intervals.forEachIndexed { index, ratio ->
            val start = index * SAMPLE_RATE / 2
            val length = (SAMPLE_RATE * 0.16).toInt()
            val freq = base * ratio
            for (sample in 0 until length) {
                val time = sample.toDouble() / SAMPLE_RATE
                val envelope = exp(-time * (6.0 + index))
                out[start + sample] = (sin(2 * PI * freq * time) * envelope * 28_000).toInt().toShort()
            }
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}
