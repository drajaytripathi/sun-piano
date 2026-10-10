package code.alimiracle.sunpiano

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver

/** A virtual MIDI keyboard with one output port and no input ports; it sends nothing on its own. */
class TestMidiDeviceService : MidiDeviceService() {
    override fun onGetInputPortReceivers(): Array<MidiReceiver> = emptyArray()
}
