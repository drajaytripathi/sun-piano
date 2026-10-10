package code.alimiracle.sunpiano

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import code.alimiracle.sunpiano.audio.Instruments
import code.alimiracle.sunpiano.audio.SynthEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Renders a short passage through the real engine (no AudioTrack) and writes a WAV file to
 * /sdcard/Android/data/code.alimiracle.sunpiano/files/ so the output can be inspected and listened to.
 */
@RunWith(AndroidJUnit4::class)
class PianoRenderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** (time ms, note, velocity) — velocity 0 = note off, note -1 = pedal. */
    private val passage = listOf(
        Triple(0, 60, 70), Triple(380, 60, 0),
        Triple(400, 64, 75), Triple(780, 64, 0),
        Triple(800, 67, 80), Triple(1180, 67, 0),
        Triple(1200, 72, 90), Triple(1700, 72, 0),
        Triple(1800, -1, 127),
        Triple(1800, 48, 100), Triple(1800, 60, 100), Triple(1800, 64, 100), Triple(1800, 67, 100),
        Triple(2600, 48, 0), Triple(2600, 60, 0), Triple(2600, 64, 0), Triple(2600, 67, 0),
        Triple(4200, -1, 0),
        Triple(4600, 69, 45), Triple(5000, 69, 0),
        Triple(5200, 69, 120), Triple(5800, 69, 0),
    )

    @Test
    fun renderPiano() {
        val out = render("piano_synth.wav")
        val peak = out.maxOf { abs(it) }
        Log.i(TAG, "peak $peak")
        assertTrue("output is silent", peak > 0.05f)
        assertTrue("output has NaN", out.none { it.isNaN() })
    }

    private fun render(fileName: String): FloatArray {
        val engine = SynthEngine()
        engine.useInstrument(Instruments.GRAND)
        engine.volume = 0.8f
        engine.reverbLevel = 0.2f
        val rate = engine.sampleRate
        val totalFrames = rate * 7
        val out = FloatArray(totalFrames / SynthEngine.BLOCK * SynthEngine.BLOCK * 2)
        val block = FloatArray(SynthEngine.BLOCK * 2)
        var next = 0
        var frame = 0
        while ((frame + SynthEngine.BLOCK) * 2 <= out.size) {
            val nowMs = frame * 1000L / rate
            while (next < passage.size && passage[next].first <= nowMs) {
                val (_, note, vel) = passage[next++]
                when {
                    note < 0 -> engine.setSustain(vel > 0)
                    vel > 0 -> engine.noteOn(note, vel)
                    else -> engine.noteOff(note)
                }
            }
            engine.renderBlock(block)
            block.copyInto(out, frame * 2)
            frame += SynthEngine.BLOCK
        }
        writeWav(File(context.getExternalFilesDir(null), fileName), out, rate)
        return out
    }

    private fun writeWav(file: File, data: FloatArray, rate: Int) {
        DataOutputStream(FileOutputStream(file).buffered()).use { o ->
            fun le32(v: Int) = o.writeInt(Integer.reverseBytes(v))
            fun le16(v: Int) = o.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
            o.writeBytes("RIFF"); le32(36 + data.size * 2); o.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(2); le32(rate); le32(rate * 4); le16(4); le16(16)
            o.writeBytes("data"); le32(data.size * 2)
            for (s in data) le16((s.coerceIn(-1f, 1f) * 32767).toInt())
        }
    }

    companion object {
        private const val TAG = "PianoRenderTest"
    }
}
