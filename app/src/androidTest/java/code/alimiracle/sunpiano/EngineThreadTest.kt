package code.alimiracle.sunpiano

import androidx.test.ext.junit.runners.AndroidJUnit4
import code.alimiracle.sunpiano.audio.AudioOutput
import code.alimiracle.sunpiano.audio.SynthEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An output stuck in write() for longer than stop() waits (bug.md #4) must not leave its thread
 * running beside the next start's: two threads would render the same voices at once.
 */
@RunWith(AndroidJUnit4::class)
class EngineThreadTest {
    private fun renderThreads() = Thread.getAllStackTraces().keys.count { it.name == "pan-synth" && it.isAlive }

    @Test
    fun aThreadStuckInWriteExitsInsteadOfRunningBesideTheNextOne() {
        val hang = AtomicBoolean(true)
        val engine = SynthEngine {
            object : AudioOutput {
                override fun play() {}
                override fun write(data: FloatArray): Int {
                    Thread.sleep(if (hang.getAndSet(false)) 2500L else 5L) // the first write hangs (a dead audio route)
                    return data.size
                }
                override fun stop() {}
                override fun release() {}
            }
        }
        val before = renderThreads()
        engine.start()
        Thread.sleep(300) // the thread is stuck in its first write
        engine.stop() // gives up waiting after 1 s
        engine.start()
        Thread.sleep(3000) // the stuck write has returned
        assertEquals("audio threads running", before + 1, renderThreads())
        engine.stop()
        Thread.sleep(200)
        assertEquals("audio threads after stop", before, renderThreads())
    }
}
