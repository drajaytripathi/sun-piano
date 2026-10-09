package com.example.sunpiano

import android.app.Application
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sunpiano.data.NoteEvent
import com.example.sunpiano.data.Recording
import com.example.sunpiano.data.RecordingStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** A recording that can't be saved must be reported, not lost silently or crash the app (bug.md #16). */
@RunWith(AndroidJUnit4::class)
class RecordingSaveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val store = RecordingStore(app)
    private val dir = File(app.filesDir, "recordings")

    private fun recording(id: String) = Recording(id, "Test $id", System.currentTimeMillis(), 500, listOf(NoteEvent(0, 60, 90), NoteEvent(500, 60, 0)))

    @Test
    fun aFailedMoveIntoPlaceIsReported() {
        val id = "test-blocked"
        val target = File(dir.apply { mkdirs() }, "$id.json")
        target.mkdirs() // a directory in the way: the rename can't succeed
        File(target, "x").writeText("x")
        try {
            runBlocking {
                try {
                    store.save(recording(id))
                    fail("save() pretended to succeed")
                } catch (expected: IOException) {
                }
                assertFalse("temporary file left behind", File(dir, "$id.tmp").exists())
                assertTrue(store.list().none { it.id == id })
            }
        } finally {
            target.deleteRecursively()
        }
    }

    @Test
    fun aNormalSaveStillWorks() = runBlocking {
        val id = "test-ok"
        try {
            store.save(recording(id))
            assertTrue(store.list().any { it.id == id })
        } finally {
            store.delete(id)
        }
    }

    @Test
    fun theViewModelTellsTheUserInsteadOfLosingTheRecording() {
        dir.mkdirs()
        assertTrue("could not make the folder read-only", dir.setWritable(false, false))
        try {
            lateinit var vm: PianoViewModel
            instrumentation.runOnMainSync {
                vm = PianoViewModel(app)
                vm.toggleRecording()
                vm.noteOn(60, 90)
                vm.noteOff(60)
                vm.toggleRecording() // saving fails: the folder is read-only
            }
            val deadline = SystemClock.elapsedRealtime() + 3000
            while (vm.message.value == null && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            val message = vm.message.value
            assertNotNull("no message shown for the failed save", message)
            assertTrue(message!!, message.startsWith("Couldn't save the recording"))
        } finally {
            dir.setWritable(true, true)
        }
    }
}
