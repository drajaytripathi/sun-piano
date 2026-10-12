package code.alimiracle.sunpiano.midi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Listens to one MIDI controller and forwards channel messages: the first USB (or already shared)
 * device, or a Bluetooth keyboard the user picked with [openBluetooth].
 */
class MidiInput(
    private val context: Context,
    onNoteOn: (note: Int, velocity: Int) -> Unit,
    onNoteOff: (note: Int) -> Unit,
    onSustain: (down: Boolean) -> Unit,
    private val onDeviceChanged: (name: String?, bluetooth: Boolean) -> Unit,
    private val onBluetoothFailed: (name: String) -> Unit = {},
) {
    private companion object {
        const val TAG = "MidiInput"
    }

    private val parser = MidiParser(onNoteOn, onNoteOff, onSustain)
    private val handler = Handler(Looper.getMainLooper())
    private val manager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else null

    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    private var currentId = -1
    private var started = false

    /**
     * Raised by [close]. A parse the receiver queued before the device went away runs after
     * [close]'s cleanup, which would hold notes nothing will ever release; its generation no
     * longer matches, so it is dropped. Bytes of a newly attached device carry the new one.
     */
    @Volatile private var generation = 0

    /** The Bluetooth keyboard the user picked, reopened by [start] after leaving the app. */
    private var bluetooth: BluetoothDevice? = null
    private var bluetoothName = ""

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            if (device == null) open(info)
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (info.id == currentId) {
                close()
                manager?.devices?.firstOrNull { it.outputPortCount > 0 }?.let { open(it) }
            }
        }
    }

    private val receiver = object : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            // MIDI callbacks arrive on a binder thread; hop to main for the UI/view-model.
            val copy = data.copyOfRange(offset, offset + count)
            val seen = generation
            handler.post { if (seen == generation) parser.parse(copy) }
        }
    }

    fun start() {
        val m = manager ?: return
        started = true
        m.registerDeviceCallback(callback, handler)
        bluetooth?.let { connectBluetooth(it) }
        if (device == null) m.devices.firstOrNull { it.outputPortCount > 0 }?.let { open(it) }
    }

    fun stop() {
        started = false
        manager?.unregisterDeviceCallback(callback)
        close()
    }

    /** Connects to a Bluetooth MIDI keyboard, replacing the current device once it's open. */
    fun openBluetooth(dev: BluetoothDevice, name: String) {
        bluetooth = dev
        bluetoothName = name
        if (started) connectBluetooth(dev)
    }

    private fun connectBluetooth(dev: BluetoothDevice) {
        val m = manager ?: return
        m.openBluetoothDevice(dev, { opened ->
            when {
                opened == null -> {
                    if (bluetooth == dev) bluetooth = null
                    onBluetoothFailed(bluetoothName)
                }
                !started || bluetooth != dev -> opened.close() // left the app or picked another
                else -> {
                    if (device != null) close()
                    attach(opened, opened.info)
                }
            }
        }, handler)
    }

    private fun open(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0) return
        // The device list can be stale: a device that just went away (e.g. the Bluetooth keyboard
        // we closed on leaving the app) may still be listed, and opening it throws.
        try {
            openListed(info)
        } catch (e: RuntimeException) {
            Log.w(TAG, "couldn't open MIDI device ${info.id}", e)
        }
    }

    private fun openListed(info: MidiDeviceInfo) {
        manager?.openDevice(info, { dev ->
            if (dev == null) return@openDevice
            if (!started || device != null) { // left the app while it opened, or another won
                dev.close()
                return@openDevice
            }
            attach(dev, info)
        }, handler)
    }

    private fun attach(dev: MidiDevice, info: MidiDeviceInfo) {
        device = dev
        currentId = info.id
        port = dev.openOutputPort(0)?.also { it.connect(receiver) }
        val props = info.properties
        onDeviceChanged(props.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI device", info.type == MidiDeviceInfo.TYPE_BLUETOOTH)
    }

    private fun close() {
        // First, so a parse queued before this moment is dropped instead of re-holding notes
        // of the device being closed (the receiver's post checks it).
        generation++
        // Without this, notes and the pedal held when the device is unplugged (or when we stop
        // listening) would never get their note-off / pedal-up and stay stuck.
        parser.releaseAll()
        port?.disconnect(receiver)
        runCatching { port?.close() }
        runCatching { device?.close() }
        port = null
        device = null
        currentId = -1
        onDeviceChanged(null, false)
    }
}
