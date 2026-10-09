package com.example.sunpiano.midi

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper

/** Listens to the first connected USB/BLE MIDI controller and forwards channel messages. */
class MidiInput(
    private val context: Context,
    onNoteOn: (note: Int, velocity: Int) -> Unit,
    onNoteOff: (note: Int) -> Unit,
    onSustain: (down: Boolean) -> Unit,
    private val onDeviceChanged: (name: String?) -> Unit,
) {
    private val parser = MidiParser(onNoteOn, onNoteOff, onSustain)
    private val handler = Handler(Looper.getMainLooper())
    private val manager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else null

    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    private var currentId = -1

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
            handler.post { parser.parse(copy) }
        }
    }

    fun start() {
        val m = manager ?: return
        m.registerDeviceCallback(callback, handler)
        if (device == null) m.devices.firstOrNull { it.outputPortCount > 0 }?.let { open(it) }
    }

    fun stop() {
        manager?.unregisterDeviceCallback(callback)
        close()
    }

    private fun open(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0) return
        manager?.openDevice(info, { dev ->
            if (dev == null) return@openDevice
            if (device != null) {
                dev.close()
                return@openDevice
            }
            device = dev
            currentId = info.id
            port = dev.openOutputPort(0)?.also { it.connect(receiver) }
            val props = info.properties
            onDeviceChanged(props.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI device")
        }, handler)
    }

    private fun close() {
        // Without this, notes and the pedal held when the device is unplugged (or when we stop
        // listening) would never get their note-off / pedal-up and stay stuck.
        parser.releaseAll()
        port?.disconnect(receiver)
        runCatching { port?.close() }
        runCatching { device?.close() }
        port = null
        device = null
        currentId = -1
        onDeviceChanged(null)
    }
}
