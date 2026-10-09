package com.example.sunpiano

import android.app.Application
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sunpiano.data.MidiFileWriter
import com.example.sunpiano.data.NoteEvent
import com.example.sunpiano.data.Recording
import com.example.sunpiano.data.RecordingStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** "Share .mid" exports must not overwrite each other or crash the app (bug.md #15). */
@RunWith(AndroidJUnit4::class)
class MidiExportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val store = RecordingStore(app)

    private fun recording(id: String, name: String, note: Int) =
        Recording(id, name, System.currentTimeMillis(), 500, listOf(NoteEvent(0, note, 90), NoteEvent(500, note, 0)))

    @Test
    fun recordingsWithTheSameNameExportToDifferentFiles() = runBlocking {
        val a = recording("test-a", "Recording Oct 10, 01:20", 60)
        val b = recording("test-b", "Recording Oct 10, 01:20", 72)
        val fa = store.exportMidi(a)
        val fb = store.exportMidi(b)
        assertNotEquals(fa.path, fb.path)
        assertArrayEquals(MidiFileWriter.write(a), fa.readBytes()) // a's file still holds a
        assertArrayEquals(MidiFileWriter.write(b), fb.readBytes())
    }

    @Test
    fun anArabicNameIsKeptAndCanBeShared() = runBlocking {
        val file = store.exportMidi(recording("test-ar", "تسجيل جميل", 60))
        assertEquals("تسجيل جميل.mid", file.name)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        assertTrue(uri.toString(), uri.lastPathSegment!!.endsWith("تسجيل جميل.mid"))
    }

    @Test
    fun aFailedExportIsReportedInsteadOfCrashing() {
        val exports = File(app.cacheDir, "exports")
        exports.deleteRecursively()
        exports.writeText("not a folder") // nothing can be written under it
        try {
            lateinit var vm: PianoViewModel
            instrumentation.runOnMainSync { vm = PianoViewModel(app) }
            val file = runBlocking { vm.exportMidi(recording("test-x", "Blocked", 60)) }
            assertNull(file)
            assertTrue(vm.message.value.orEmpty(), vm.message.value.orEmpty().startsWith("Couldn't export \"Blocked\""))
        } finally {
            exports.delete()
        }
    }
}
