package com.example.sunpiano

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sunpiano.midi.BluetoothMidiScanner
import com.example.sunpiano.midi.FoundMidiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Bluetooth MIDI keyboards can be found, connected and played (bug.md #7). */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BluetoothMidiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val pkg = instrumentation.targetContext.packageName

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun <T> waitFor(what: String, timeoutMs: Long, value: () -> T?): T {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            var v: T? = null
            onMain { v = value() }
            v?.let { return it }
            check(SystemClock.uptimeMillis() < deadline) { "timed out waiting for $what" }
            SystemClock.sleep(100)
        }
    }

    private fun waitUntil(what: String, timeoutMs: Long, condition: () -> Boolean) {
        waitFor(what, timeoutMs) { if (condition()) true else null }
    }

    private fun withApp(block: (PianoViewModel) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
            block(vm)
        }
    }

    @Test
    fun a_searchingWithoutThePermissionExplainsWhatIsMissing() {
        // revoking would kill the app's process, so this runs first, on the fresh install
        withApp { vm ->
            assumeFalse("permission already granted", vm.hasBluetoothPermissions())
            assertTrue("emulator should support Bluetooth MIDI", vm.bluetoothMidiSupported)
            onMain { vm.searchBluetoothMidi() }
            assertTrue(vm.message.value.orEmpty(), vm.message.value.orEmpty().contains("Nearby devices permission"))
            assertFalse(vm.bluetoothScanning.value)
        }
    }

    @Test
    fun b_searchingRunsAndCanBeStoppedByLeavingTheApp() {
        BluetoothMidiScanner.PERMISSIONS.forEach { instrumentation.uiAutomation.grantRuntimePermission(pkg, it) }
        withApp { vm ->
            onMain { vm.searchBluetoothMidi() }
            assertNull("search refused: ${vm.message.value}", vm.message.value)
            assertTrue(vm.bluetoothScanning.value)
            onMain { vm.onBackground() }
            assertFalse("still scanning after leaving the app", vm.bluetoothScanning.value)
        }
    }

    /** Needs [BleMidiPeer] running on a second emulator; run with `-e bleCentral true`. */
    @Test
    fun c_aBluetoothKeyboardIsFoundConnectedAndPlayed() {
        assumeTrue("needs the BleMidiPeer emulator", InstrumentationRegistry.getArguments().getString("bleCentral") != null)
        BluetoothMidiScanner.PERMISSIONS.forEach { instrumentation.uiAutomation.grantRuntimePermission(pkg, it) }
        withApp { vm ->
            onMain { vm.searchBluetoothMidi() }
            val keyboard: FoundMidiDevice = waitFor("the peer keyboard in the search results", 15_000) { vm.bluetoothDevices.value.firstOrNull() }
            assertEquals("SunPiano Test Keys", keyboard.name)
            onMain { vm.connectBluetoothMidi(keyboard) }
            val name = waitFor("the connection", 20_000) { vm.midiDevice.value }
            assertTrue(name, name.contains("SunPiano Test Keys"))
            assertTrue("search results kept after connecting", vm.bluetoothDevices.value.isEmpty())
            waitUntil("middle C from the keyboard", 10_000) { 60 in vm.pressed.value }
            waitUntil("the keyboard's sustain pedal", 10_000) { vm.sustain.value }
            waitUntil("middle C released", 10_000) { 60 !in vm.pressed.value }

            // leaving and returning to the app reconnects the chosen keyboard
            onMain { vm.onBackground() }
            assertNull("still connected after leaving the app", vm.midiDevice.value)
            onMain { vm.onForeground() }
            waitFor("reconnection after returning", 20_000) { vm.midiDevice.value }
            waitUntil("middle C after reconnecting", 10_000) { 60 in vm.pressed.value }
        }
    }
}
