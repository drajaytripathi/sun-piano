package code.alimiracle.sunpiano

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** A non-Bluetooth MIDI device must not show as the connected Bluetooth keyboard (bug.md #27). */
@RunWith(AndroidJUnit4::class)
class BluetoothLabelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun aNonBluetoothKeyboardIsNotTheBluetoothKeyboard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
            // the test APK's virtual (non-Bluetooth) MIDI keyboard gets opened
            val deadline = SystemClock.uptimeMillis() + 5000
            while (vm.midiDevice.value == null) {
                check(SystemClock.uptimeMillis() < deadline) { "the virtual MIDI keyboard wasn't opened" }
                SystemClock.sleep(100)
            }
            instrumentation.runOnMainSync {
                assertNull("${vm.midiDevice.value} reported as the Bluetooth keyboard", vm.bluetoothMidiDevice.value)
            }
        }
    }
}
