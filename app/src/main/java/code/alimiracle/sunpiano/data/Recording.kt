package code.alimiracle.sunpiano.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** [velocity] 0 means note-off. [note] == [PEDAL] encodes the sustain pedal (velocity > 0 = down). */
data class NoteEvent(val timeMs: Long, val note: Int, val velocity: Int) {
    companion object {
        const val PEDAL = -1
    }
}

/**
 * [instrumentId] is the id of the instrument the recording was played with, or null for
 * recordings made before it was stored: those sound with the instrument chosen now.
 */
data class Recording(
    val id: String,
    val name: String,
    val createdAt: Long,
    val durationMs: Long,
    val events: List<NoteEvent>,
    val instrumentId: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("durationMs", durationMs)
        instrumentId?.let { put("instrument", it) }
        // compact [time, note, velocity] triples
        put("events", JSONArray().also { arr ->
            events.forEach { arr.put(JSONArray().put(it.timeMs).put(it.note).put(it.velocity)) }
        })
    }.toString()

    companion object {
        fun fromJson(text: String): Recording {
            val o = JSONObject(text)
            val arr = o.getJSONArray("events")
            val events = List(arr.length()) {
                val e = arr.getJSONArray(it)
                NoteEvent(e.getLong(0), e.getInt(1), e.getInt(2))
            }
            val instrument = if (o.has("instrument")) o.getString("instrument") else null
            return Recording(o.getString("id"), o.getString("name"), o.getLong("createdAt"), o.getLong("durationMs"), events, instrument)
        }
    }
}

/** Minimal Standard MIDI File (format 0) writer. One tick == one millisecond. */
object MidiFileWriter {
    private const val DIVISION = 500          // ticks per quarter note
    private const val TEMPO_US = 500_000      // 120 bpm -> 1 tick = 1 ms

    fun write(recording: Recording): ByteArray {
        val track = ByteArrayOutputStream()
        // tempo meta event
        track.write(byteArrayOf(0x00, 0xFF.toByte(), 0x51, 0x03, (TEMPO_US shr 16).toByte(), (TEMPO_US shr 8).toByte(), TEMPO_US.toByte()))
        var last = 0L
        for (e in recording.events.sortedBy { it.timeMs }) {
            writeVlq(track, e.timeMs - last)
            last = e.timeMs
            if (e.note == NoteEvent.PEDAL) {
                track.write(0xB0); track.write(64); track.write(if (e.velocity > 0) 127 else 0)
            } else if (e.velocity > 0) {
                track.write(0x90); track.write(e.note and 0x7F); track.write(e.velocity and 0x7F)
            } else {
                track.write(0x80); track.write(e.note and 0x7F); track.write(0)
            }
        }
        writeVlq(track, 0)
        track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

        val body = track.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        writeInt(out, 6)
        out.write(byteArrayOf(0, 0, 0, 1, (DIVISION shr 8).toByte(), DIVISION.toByte()))
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        writeInt(out, body.size)
        out.write(body)
        return out.toByteArray()
    }

    internal fun writeVlq(out: ByteArrayOutputStream, value: Long) {
        var v = value.coerceAtLeast(0)
        val bytes = ArrayList<Int>()
        bytes.add((v and 0x7F).toInt())
        v = v shr 7
        while (v > 0) {
            bytes.add(((v and 0x7F) or 0x80).toInt())
            v = v shr 7
        }
        for (i in bytes.indices.reversed()) out.write(bytes[i])
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v ushr 24); out.write(v ushr 16); out.write(v ushr 8); out.write(v)
    }
}
