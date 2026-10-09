package com.example.sunpiano.midi

/**
 * Turns raw MIDI bytes into note and sustain callbacks, and remembers which notes and pedal the
 * device is holding so they can be released when the device goes away mid-performance.
 */
class MidiParser(
    private val onNoteOn: (note: Int, velocity: Int) -> Unit,
    private val onNoteOff: (note: Int) -> Unit,
    private val onSustain: (down: Boolean) -> Unit,
) {
    private val held = LinkedHashSet<Int>()
    private var pedalDown = false

    // Parser state, kept between calls: a message, a SysEx dump or running status can continue
    // in the next buffer.
    private var status = 0 // running status of the current channel message, 0 = none
    private var inSysex = false
    private var skip = 0 // data bytes of a system common message still to skip
    private val data = IntArray(2)
    private var count = 0

    fun parse(d: ByteArray) {
        for (byte in d) feed(byte.toInt() and 0xFF)
    }

    private fun feed(b: Int) {
        when {
            b >= 0xF8 -> Unit // realtime (clock, start, stop…): may appear anywhere, changes nothing
            b == 0xF0 -> { // SysEx start: skip everything up to F7
                inSysex = true
                status = 0
                skip = 0
            }
            b >= 0xF1 -> { // system common (incl. F7 end of SysEx): cancels running status
                inSysex = false
                status = 0
                count = 0
                skip = when (b) {
                    0xF1, 0xF3 -> 1 // MIDI time code quarter frame, song select
                    0xF2 -> 2 // song position
                    else -> 0 // tune request, end of SysEx, undefined
                }
            }
            b >= 0x80 -> { // channel message status (also ends an unterminated SysEx)
                inSysex = false
                status = b
                count = 0
                skip = 0
            }
            inSysex -> Unit
            skip > 0 -> skip--
            status == 0 -> Unit // data byte without a status: ignore
            else -> {
                data[count++] = b
                val type = status and 0xF0
                val need = if (type == 0xC0 || type == 0xD0) 1 else 2
                if (count == need) {
                    count = 0 // running status: the next data bytes reuse `status`
                    when (type) {
                        0x90 -> if (data[1] > 0) noteOn(data[0], data[1]) else noteOff(data[0])
                        0x80 -> noteOff(data[0])
                        0xB0 -> if (data[0] == 64) sustain(data[1] >= 64)
                    }
                }
            }
        }
    }

    /**
     * For when the device goes away: sends note-off for every note it still holds and pedal-up if
     * its pedal is down, and forgets any half-received message.
     */
    fun releaseAll() {
        status = 0
        inSysex = false
        skip = 0
        count = 0
        val notes = held.toList()
        held.clear()
        notes.forEach { onNoteOff(it) }
        if (pedalDown) sustain(false)
    }

    // Only one note-on per note-off is passed on, so a device repeating a message can't leave a
    // note held (or release one held by touch or the computer keyboard).
    private fun noteOn(note: Int, velocity: Int) {
        if (held.add(note)) onNoteOn(note, velocity)
    }

    private fun noteOff(note: Int) {
        if (held.remove(note)) onNoteOff(note)
    }

    private fun sustain(down: Boolean) {
        pedalDown = down
        onSustain(down)
    }
}
