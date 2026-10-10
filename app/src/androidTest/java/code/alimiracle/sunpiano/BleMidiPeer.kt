package code.alimiracle.sunpiano

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import code.alimiracle.sunpiano.midi.BluetoothMidiScanner
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Not a test of the app: run on a second emulator, it pretends to be a Bluetooth LE MIDI keyboard
 * for [BluetoothMidiTest]. It advertises the BLE-MIDI service and, while a central listens, plays
 * middle C with the sustain pedal once a second. Skipped unless run with `-e blePeer <seconds>`.
 */
@RunWith(AndroidJUnit4::class)
class BleMidiPeer {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun actAsBluetoothMidiKeyboard() {
        val seconds = InstrumentationRegistry.getArguments().getString("blePeer")?.toLong()
        assumeTrue("only run on the peer emulator", seconds != null)
        instrumentation.uiAutomation.adoptShellPermissionIdentity()
        try {
            serve(seconds!! * 1000)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Suppress("MissingPermission")
    private fun serve(durationMs: Long) {
        val context = instrumentation.targetContext
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager.adapter
        adapter.name = "SunPiano Test Keys"
        val io = BluetoothGattCharacteristic(
            MIDI_IO,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
        ).apply {
            addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        }
        val service = BluetoothGattService(BluetoothMidiScanner.MIDI_SERVICE.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(io)

        val listeners = java.util.concurrent.CopyOnWriteArraySet<BluetoothDevice>()
        lateinit var server: BluetoothGattServer
        server = manager.openGattServer(context, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                Log.i(TAG, "connection ${device.address}: $newState")
                if (newState != BluetoothProfile.STATE_CONNECTED) listeners -= device
            }

            override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, c: BluetoothGattCharacteristic) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0)) // BLE-MIDI: empty read
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, c: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
            ) {
                if (responseNeeded) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, d: BluetoothGattDescriptor) {
                val on = device in listeners
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0,
                    if (on) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice, requestId: Int, d: BluetoothGattDescriptor,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
            ) {
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) listeners += device else listeners -= device
                Log.i(TAG, "notifications ${device.address}: ${device in listeners}")
                if (responseNeeded) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        })
        check(server.addService(service)) { "service not added" }
        SystemClock.sleep(500)

        val advertiser = checkNotNull(adapter.bluetoothLeAdvertiser) { "no BLE advertiser" }
        val advertising = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) { Log.e(TAG, "advertising failed: $errorCode") }
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) { Log.i(TAG, "advertising") }
        }
        advertiser.startAdvertising(
            AdvertiseSettings.Builder().setConnectable(true).setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).build(),
            AdvertiseData.Builder().addServiceUuid(ParcelUuid(BluetoothMidiScanner.MIDI_SERVICE.uuid)).build(),
            AdvertiseData.Builder().setIncludeDeviceName(true).build(),
            advertising,
        )

        fun send(vararg midi: Int) {
            // BLE-MIDI packet: header and timestamp bytes (high bit set), then the MIDI message
            val packet = byteArrayOf(0x80.toByte(), 0x80.toByte()) + ByteArray(midi.size) { midi[it].toByte() }
            for (d in listeners) server.notifyCharacteristicChanged(d, io, false, packet)
        }

        val end = SystemClock.uptimeMillis() + durationMs
        while (SystemClock.uptimeMillis() < end) {
            if (listeners.isNotEmpty()) {
                send(0x90, 60, 100)     // note on, middle C
                SystemClock.sleep(250)
                send(0xB0, 64, 127)     // sustain pedal down
                SystemClock.sleep(250)
                send(0x80, 60, 0)       // note off
                SystemClock.sleep(250)
                send(0xB0, 64, 0)       // pedal up
                SystemClock.sleep(250)
            } else SystemClock.sleep(100)
        }
        advertiser.stopAdvertising(advertising)
        server.close()
    }

    companion object {
        private const val TAG = "BleMidiPeer"
        /** BLE-MIDI data I/O characteristic. */
        val MIDI_IO: UUID = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
