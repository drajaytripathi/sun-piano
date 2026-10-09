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
    private val onNoteOn: (note: Int, velocity: Int) -> Unit,
    private val onNoteOff: (note: Int) -> Unit,
    private val onSustain: (down: Boolean) -> Unit,
    private val onDeviceChanged: (name: String?) -> Unit,
) {
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
            handler.post { parse(copy) }
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
        port?.disconnect(receiver)
        runCatching { port?.close() }
        runCatching { device?.close() }
        port = null
        device = null
        currentId = -1
        onDeviceChanged(null)
    }

    private fun parse(d: ByteArray) {
        var i = 0
        var running = 0
        while (i < d.size) {
            var b = d[i].toInt() and 0xFF
            if (b >= 0xF8) { i++; continue } // realtime
            if (b >= 0x80) { running = b; i++ } else if (running == 0) { i++; continue }
            b = running
            val type = b and 0xF0
            val need = if (type == 0xC0 || type == 0xD0) 1 else 2
            if (b >= 0xF0 || i + need > d.size) return // sysex/system: ignore the rest
            val d1 = d[i].toInt() and 0x7F
            val d2 = if (need == 2) d[i + 1].toInt() and 0x7F else 0
            i += need
            when (type) {
                0x90 -> if (d2 > 0) onNoteOn(d1, d2) else onNoteOff(d1)
                0x80 -> onNoteOff(d1)
                0xB0 -> if (d1 == 64) onSustain(d2 >= 64)
            }
        }
    }
}
