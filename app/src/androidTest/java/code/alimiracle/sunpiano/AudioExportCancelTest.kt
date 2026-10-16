package code.alimiracle.sunpiano

import android.app.Application
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import code.alimiracle.sunpiano.data.NoteEvent
import code.alimiracle.sunpiano.data.Recording
import code.alimiracle.sunpiano.data.RecordingStore
import code.alimiracle.sunpiano.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Closing the Library cancels its audio export: the export must stop then, not run on to the end (bug.md #3). */
@RunWith(AndroidJUnit4::class)
class AudioExportCancelTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test
    fun aCancelledExportStopsAtOnceAndLeavesNoFile() = runBlocking {
        // two minutes of notes: exporting it all takes far longer than the second allowed below
        val events = (0 until 240).flatMap { i -> listOf(NoteEvent(i * 500L, 60 + i % 12, 90, "grand"), NoteEvent(i * 500L + 400, 60 + i % 12, 0)) }
        val rec = Recording("test-cancel", "Long", 0, 120_000, events)
        var progress = 0f
        val export = launch(Dispatchers.Default) { RecordingStore(app).exportAudio(rec, Settings()) { progress = it } }
        while (progress == 0f) delay(10) // it has really started encoding
        val start = SystemClock.uptimeMillis()
        export.cancelAndJoin()
        val took = SystemClock.uptimeMillis() - start
        assertTrue("the export ran on for $took ms after being cancelled (at ${(progress * 100).toInt()}%)", took < 1000)
        val left = File(app.cacheDir, "exports/test-cancel").listFiles().orEmpty().map { it.name }
        assertTrue("a half-written file was left: $left", left.isEmpty())
    }
}
