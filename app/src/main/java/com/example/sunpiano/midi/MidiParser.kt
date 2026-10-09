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

    fun parse(d: ByteArray) {
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
                0x90 -> if (d2 > 0) noteOn(d1, d2) else noteOff(d1)
                0x80 -> noteOff(d1)
                0xB0 -> if (d1 == 64) sustain(d2 >= 64)
            }
        }
    }

    /** Sends note-off for every note the device still holds, and pedal-up if its pedal is down. */
    fun releaseAll() {
        val notes = held.toList()
        held.clear()
        notes.forEach { onNoteOff(it) }
        if (pedalDown) sustain(false)
    }

    private fun noteOn(note: Int, velocity: Int) {
        held += note
        onNoteOn(note, velocity)
    }

    private fun noteOff(note: Int) {
        held -= note
        onNoteOff(note)
    }

    private fun sustain(down: Boolean) {
        pedalDown = down
        onSustain(down)
    }
}
