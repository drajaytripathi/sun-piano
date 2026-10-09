package com.example.sunpiano

import android.media.AudioTrack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sunpiano.audio.AudioOutput
import com.example.sunpiano.audio.AudioTrackOutput
import com.example.sunpiano.audio.SynthEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The engine must rebuild a failed audio output and keep playing (bug.md #1). */
@RunWith(AndroidJUnit4::class)
class AudioRecoveryTest {
    /** Accepts blocks at roughly real-time pace; output number [failingOutput] fails on its 5th write. */
    private inner class FakeOutput(private val number: Int, private val failingOutput: Int, private val soundAfterFailure: CountDownLatch) : AudioOutput {
        private var writes = 0
        override fun play() {}
        override fun write(data: FloatArray): Int {
            Thread.sleep(5)
            writes++
            if (number == failingOutput && writes == 5) return AudioTrack.ERROR_DEAD_OBJECT
            if (number > failingOutput && data.any { it != 0f }) soundAfterFailure.countDown()
            return data.size
        }
        override fun stop() {}
        override fun release() {}
    }

    @Test
    fun rebuildsOutputAfterWriteError() {
        val opened = AtomicInteger()
        val soundAfterFailure = CountDownLatch(20)
        val engine = SynthEngine { FakeOutput(opened.incrementAndGet(), failingOutput = 1, soundAfterFailure) }
        engine.noteOn(60, 100) // still sounding when the first output dies
        engine.start()
        try {
            assertTrue("no sound after the output failed", soundAfterFailure.await(5, TimeUnit.SECONDS))
        } finally {
            engine.stop()
        }
        assertEquals("outputs opened", 2, opened.get())
    }

    @Test
    fun retriesWhenRebuildingFails() {
        val opened = AtomicInteger()
        val soundAfterFailure = CountDownLatch(20)
        val engine = SynthEngine {
            when (val n = opened.incrementAndGet()) {
                2 -> throw UnsupportedOperationException("audio server not ready")
                else -> FakeOutput(n, failingOutput = 1, soundAfterFailure)
            }
        }
        engine.noteOn(60, 100)
        engine.start()
        try {
            assertTrue("no sound after the rebuild was retried", soundAfterFailure.await(5, TimeUnit.SECONDS))
        } finally {
            engine.stop()
        }
        assertEquals("outputs opened (1 failed write, 1 failed rebuild, 1 good)", 3, opened.get())
    }

    @Test
    fun realAudioTrackOutputAcceptsBlocks() {
        // Plays silence (no notes) through the real AudioTrack to check the wrapper works.
        val accepted = CountDownLatch(20)
        val engine = SynthEngine { rate ->
            val real = AudioTrackOutput(rate)
            object : AudioOutput by real {
                override fun write(data: FloatArray): Int = real.write(data).also { if (it == data.size) accepted.countDown() }
            }
        }
        engine.start()
        try {
            assertTrue("AudioTrack did not accept audio", accepted.await(5, TimeUnit.SECONDS))
        } finally {
            engine.stop()
        }
    }
}
