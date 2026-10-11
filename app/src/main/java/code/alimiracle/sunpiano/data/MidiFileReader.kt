package code.alimiracle.sunpiano.data

import java.io.IOException

/**
 * Reads a Standard MIDI File (format 0, 1 or 2) into note events: all tracks and channels merged,
 * tempo changes applied, note on/off and the sustain pedal (CC 64) kept. Program changes pick each
 * note's instrument ([GeneralMidi.instrumentFor]); the drum channel (10) is left out, since the app
 * has no drum sounds.
 */
object MidiFileReader {
    private const val DRUM_CHANNEL = 9
    private const val DEFAULT_TEMPO_US = 500_000 // 120 bpm until a tempo event says otherwise

    class Result(val events: List<NoteEvent>, val durationMs: Long, val trackName: String?)

    /** Throws [IOException] if [bytes] isn't a MIDI file the app can read. */
    fun read(bytes: ByteArray): Result {
        val r = Reader(bytes)
        if (r.ascii(4) != "MThd") throw IOException("not a MIDI file")
        val headerLength = r.u32()
        if (headerLength < 6) throw IOException("bad MIDI header")
        r.u16() // format: 0, 1 and 2 are all read as tracks to merge
        val tracks = r.u16()
        val division = r.u16()
        r.skip(headerLength - 6)

        // raw events in ticks, from every track
        class Raw(val tick: Long, val order: Int, val kind: Int, val a: Int, val b: Int, val channel: Int)
        val raw = ArrayList<Raw>()
        val tempos = ArrayList<Pair<Long, Int>>() // tick -> microseconds per quarter note
        var trackName: String? = null
        var order = 0
        repeat(tracks) {
            if (r.remaining() < 8) return@repeat // a truncated file: keep what was read
            val id = r.ascii(4)
            val length = r.u32()
            if (id != "MTrk") { r.skip(length); return@repeat }
            val end = minOf(r.pos + length, bytes.size)
            var tick = 0L
            var status = 0
            while (r.pos < end) {
                tick += r.vlq()
                var b = r.u8()
                if (b < 0x80) { // running status: the data byte belongs to the last status
                    if (status == 0) throw IOException("bad MIDI data")
                    r.pos--
                    b = status
                }
                when {
                    b == 0xFF -> { // meta event
                        val type = r.u8()
                        val len = r.vlq().toInt()
                        when {
                            type == 0x51 && len >= 3 -> tempos += tick to ((r.u8() shl 16) or (r.u8() shl 8) or r.u8()).also { r.skip(len - 3) }
                            type == 0x03 && trackName == null -> trackName = r.ascii(len).trim().ifEmpty { null }
                            type == 0x2F -> { r.skip(len); r.pos = end }
                            else -> r.skip(len)
                        }
                    }
                    b == 0xF0 || b == 0xF7 -> r.skip(r.vlq().toInt()) // system exclusive
                    else -> {
                        status = b
                        val kind = b and 0xF0
                        val channel = b and 0x0F
                        val one = kind == 0xC0 || kind == 0xD0
                        val d1 = r.u8()
                        val d2 = if (one) 0 else r.u8()
                        raw += Raw(tick, order++, kind, d1 and 0x7F, d2 and 0x7F, channel)
                    }
                }
            }
            r.pos = end
        }

        // ticks -> milliseconds, through the tempo map
        tempos.sortBy { it.first }
        fun ms(tick: Long): Long {
            if (division and 0x8000 != 0) { // SMPTE: frames per second and ticks per frame
                val fps = -(division shr 8).toByte().toInt()
                val perFrame = division and 0xFF
                return if (fps > 0 && perFrame > 0) tick * 1000 / (fps * perFrame) else 0
            }
            val ppq = division.coerceAtLeast(1)
            var us = 0.0
            var lastTick = 0L
            var tempo = DEFAULT_TEMPO_US
            for ((t, value) in tempos) {
                if (t >= tick) break
                us += (t - lastTick).toDouble() * tempo / ppq
                lastTick = t
                tempo = value
            }
            us += (tick - lastTick).toDouble() * tempo / ppq
            return (us / 1000).toLong()
        }

        val events = ArrayList<NoteEvent>()
        val program = IntArray(16) // per channel; GM's default is 0, but no program change means "no instrument said"
        val programSet = BooleanArray(16)
        val depth = IntArray(128) // the app keeps one voice per note: overlapping copies end with the last
        var pedalDown = false
        for (e in raw.sortedWith(compareBy({ it.tick }, { it.order }))) {
            if (e.channel == DRUM_CHANNEL) continue
            val time = ms(e.tick)
            when {
                e.kind == 0xC0 -> { program[e.channel] = e.a; programSet[e.channel] = true }
                e.kind == 0x90 && e.b > 0 -> {
                    depth[e.a]++
                    val instrument = if (programSet[e.channel]) GeneralMidi.instrumentFor(program[e.channel]) else null
                    events += NoteEvent(time, e.a, e.b, instrument)
                }
                e.kind == 0x80 || e.kind == 0x90 -> if (depth[e.a] > 0 && --depth[e.a] == 0) events += NoteEvent(time, e.a, 0)
                e.kind == 0xB0 && e.a == 64 -> {
                    val down = e.b >= 64
                    if (down != pedalDown) {
                        pedalDown = down
                        events += NoteEvent(time, NoteEvent.PEDAL, if (down) 127 else 0)
                    }
                }
            }
        }
        // notes never released, or a pedal left down, end with the file
        val end = events.maxOfOrNull { it.timeMs } ?: 0L
        for (note in 0..127) if (depth[note] > 0) events += NoteEvent(end, note, 0)
        if (pedalDown) events += NoteEvent(end, NoteEvent.PEDAL, 0)
        if (events.none { it.velocity > 0 && it.note != NoteEvent.PEDAL }) throw IOException("no notes")
        return Result(events, end, trackName)
    }

    private class Reader(val bytes: ByteArray) {
        var pos = 0
        fun remaining() = bytes.size - pos
        fun u8(): Int {
            if (pos >= bytes.size) throw IOException("MIDI file ends too early")
            return bytes[pos++].toInt() and 0xFF
        }
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
        fun vlq(): Long {
            var v = 0L
            repeat(4) {
                val b = u8()
                v = (v shl 7) or (b and 0x7F).toLong()
                if (b < 0x80) return v
            }
            throw IOException("bad MIDI number")
        }
        fun skip(n: Int) {
            if (n < 0) throw IOException("bad MIDI length")
            pos = minOf(pos + n, bytes.size)
        }
        fun ascii(n: Int): String {
            val end = minOf(pos + n.coerceAtLeast(0), bytes.size)
            return String(bytes, pos, end - pos, Charsets.ISO_8859_1).also { pos = end }
        }
    }
}

/** General MIDI programs (0..127) and the app's instruments (their ids). */
object GeneralMidi {
    /** The program written for each instrument; reading it back gives the same instrument. */
    private val programOf = mapOf(
        "grand" to 0, "honky" to 3, "electric" to 4, "harpsichord" to 6,
        "marimba" to 12, "organ" to 16, "strings" to 48, "lead" to 80,
    )

    fun programFor(instrumentId: String): Int = programOf[instrumentId] ?: 0

    /** The closest of the app's instruments for a General MIDI program. */
    fun instrumentFor(program: Int): String = programOf.entries.firstOrNull { it.value == program }?.key ?: when (program) {
        in 0..2 -> "grand"            // pianos
        3 -> "honky"
        in 4..5 -> "electric"         // electric pianos
        in 6..7 -> "harpsichord"      // harpsichord, clavinet
        in 8..15 -> "marimba"         // celesta, glockenspiel, vibraphone, xylophone…
        in 16..23 -> "organ"          // organs, accordion, harmonica
        in 24..31 -> "harpsichord"    // guitars: plucked
        in 32..39 -> "grand"          // basses
        in 40..55 -> "strings"        // strings, ensembles, choir
        in 56..79 -> "lead"           // brass, reeds, pipes
        in 80..87 -> "lead"           // synth leads
        in 88..95 -> "strings"        // synth pads
        else -> "grand"
    }
}
