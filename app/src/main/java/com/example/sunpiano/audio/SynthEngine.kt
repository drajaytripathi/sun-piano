package com.example.sunpiano.audio

import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.example.sunpiano.music.Notes
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Polyphonic real-time synthesizer rendering into an [AudioOutput] (a low-latency float
 * [AudioTrack] unless [openOutput] says otherwise).
 *
 * Public methods are thread-safe: note events go through a lock-free queue that the
 * audio thread drains once per block; parameters are volatile fields.
 */
class SynthEngine(private val openOutput: (sampleRate: Int) -> AudioOutput = ::AudioTrackOutput) {
    companion object {
        const val BLOCK = 256
        const val MAX_VOICES = 24
        /** Extra voice slots where stolen notes fade out while the new note starts in another. */
        private const val FADE_SLOTS = 8
        /** Time constant of that fade: inaudible as a click, ~45 ms to silence. */
        private const val STEAL_FADE_S = 0.005f
        /**
         * Highest FM modulator frequency, as a fraction of the sample rate. Above this the
         * modulator folds back below Nyquist as an out-of-tune tone, so FM is left off for those
         * (very high) notes; their real overtones would be ultrasonic anyway.
         */
        private const val MAX_FM_INC = 0.45f
        const val TABLE = 2048
        private const val BANDS = 11
        private const val TWO_PI = (2.0 * PI).toFloat()
        private const val ATTACK = 0
        private const val DECAY = 1
        private const val RELEASE = 2
        private const val TAG = "SynthEngine"
        /** Pause between attempts to rebuild a failed audio output, so a dead device doesn't spin. */
        private const val RETRY_DELAY_MS = 200L
    }

    private sealed interface Cmd
    private class On(val note: Int, val velocity: Int) : Cmd
    private class Off(val note: Int) : Cmd
    private class Pedal(val down: Boolean) : Cmd
    private object AllOff : Cmd

    private class Bank(val instrument: Instrument, val tables: Array<FloatArray>)

    val sampleRate: Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).coerceIn(22050, 96000)

    // ---- parameters (any thread) ----
    @Volatile var volume = 0.8f
    @Volatile var reverbLevel = 0.25f
    @Volatile var tuningA4 = 440.0
    @Volatile var metronomeOn = false
    @Volatile var bpm = 100
    @Volatile var beatsPerBar = 4
    @Volatile var metronomeVolume = 0.6f

    /** Called on the audio thread for each metronome beat (index within bar, 0 = downbeat). */
    @Volatile var onBeat: ((Int) -> Unit)? = null

    private val sineTable = FloatArray(TABLE + 2).also {
        for (i in 0 until TABLE + 2) it[i] = sin(2.0 * PI * i / TABLE).toFloat()
    }

    private val commands = ConcurrentLinkedQueue<Cmd>()
    private val bankCache = HashMap<String, Bank>()
    @Volatile private var bank: Bank = bankFor(Instruments.GRAND)

    private val voices = Array(MAX_VOICES + FADE_SLOTS) { Voice() }
    private var orderCounter = 0L
    private var pedalDown = false

    /** Last sustain state asked for. [stop] resets the pedal and drops queued commands, so [start] re-applies it. */
    @Volatile private var sustainRequested = false
    private val reverb = Reverb(sampleRate)

    @Volatile private var running = false
    private var thread: Thread? = null

    // metronome state (audio thread only)
    private var wasMetro = false
    private var metroCount = 0
    private var metroBeat = 0
    private var clickPhase = 0f
    private var clickInc = 0f
    private var clickEnv = 0f
    private var clickAmp = 0f

    // ---- control API ----
    fun setInstrument(instrument: Instrument) {
        bank = bankFor(instrument)
    }

    fun noteOn(note: Int, velocity: Int = 100) {
        if (note in 0..127) commands.add(On(note, velocity.coerceIn(1, 127)))
    }

    fun noteOff(note: Int) {
        if (note in 0..127) commands.add(Off(note))
    }

    fun setSustain(down: Boolean) {
        sustainRequested = down
        commands.add(Pedal(down))
    }

    fun allNotesOff() {
        commands.add(AllOff)
    }

    @Synchronized
    private fun bankFor(instrument: Instrument): Bank =
        bankCache.getOrPut(instrument.id) { Bank(instrument, buildTables(instrument, sampleRate)) }

    // ---- lifecycle ----
    @Synchronized
    fun start() {
        if (running) return
        val output = openOutput(sampleRate)
        commands.add(Pedal(sustainRequested))
        running = true
        thread = Thread({ renderLoop(output) }, "pan-synth").also { it.start() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        thread?.join(1000)
        thread = null
        commands.clear()
        voices.forEach { it.active = false }
        pedalDown = false
    }

    // ---- audio thread ----
    /**
     * Plays until [stop]. If the output fails (e.g. `ERROR_DEAD_OBJECT` when the audio server
     * restarts or the output device changes), it is released and rebuilt, so sound comes back
     * on its own instead of stopping until the app is restarted.
     */
    private fun renderLoop(initial: AudioOutput) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val out = FloatArray(BLOCK * 2)
        var t: AudioOutput? = initial
        while (running) {
            val current = t ?: try {
                openOutput(sampleRate).also { Log.i(TAG, "audio output rebuilt") }
            } catch (e: Exception) {
                Log.w(TAG, "could not rebuild audio output, retrying", e)
                pause(RETRY_DELAY_MS)
                continue
            }
            t = current
            try {
                current.play()
                while (running) {
                    renderBlock(out)
                    val written = current.write(out)
                    if (written < 0) {
                        Log.w(TAG, "audio output failed (error $written), rebuilding it")
                        break
                    }
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "audio output failed, rebuilding it", e)
            } finally {
                try {
                    current.stop()
                } catch (_: IllegalStateException) {
                }
                current.release()
                t = null
            }
            if (running) pause(RETRY_DELAY_MS)
        }
    }

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    private val mixL = FloatArray(BLOCK)
    private val mixR = FloatArray(BLOCK)

    /**
     * Renders the next [BLOCK] stereo frames into [out] (interleaved). Called by the audio
     * thread; tests call it directly, without starting the engine, to capture the output.
     */
    fun renderBlock(out: FloatArray) {
        drainCommands()
        mixL.fill(0f)
        mixR.fill(0f)
        for (v in voices) if (v.active) v.render(mixL, mixR, BLOCK)
        renderMetronome(mixL, mixR)

        val master = volume * volume
        val wet = reverbLevel
        val useReverb = wet > 0.001f
        for (i in 0 until BLOCK) {
            var l = mixL[i]
            var r = mixR[i]
            if (useReverb) {
                reverb.process(l, r)
                l += reverb.outL * wet
                r += reverb.outR * wet
            }
            out[2 * i] = softClip(l * master)
            out[2 * i + 1] = softClip(r * master)
        }
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a < 0.7f) return x
        val y = 0.7f + 0.3f * tanh((a - 0.7f) / 0.3f)
        return if (x < 0f) -y else y
    }

    private fun drainCommands() {
        while (true) {
            when (val c = commands.poll() ?: return) {
                is On -> startNote(c.note, c.velocity)
                is Off -> voices.firstOrNull { it.active && it.held && it.note == c.note }?.release(pedalDown)
                is Pedal -> {
                    pedalDown = c.down
                    if (!c.down) voices.forEach { if (it.active) it.pedalUp() }
                }
                AllOff -> voices.forEach { if (it.active) it.kill() }
            }
        }
    }

    private fun startNote(note: Int, velocity: Int) {
        var v = voices.firstOrNull { it.active && !it.fading && it.note == note }
        if (v == null) {
            if (voices.count { it.active && !it.fading } >= MAX_VOICES) {
                // Too many notes: fade the quietest releasing note (else the oldest) out quickly
                // instead of cutting it off, which clicks.
                val sounding = voices.filter { it.active && !it.fading }
                val victim = sounding.filter { it.stage == RELEASE }.minByOrNull { it.env } ?: sounding.minByOrNull { it.order }!!
                victim.fadeOut()
            }
            v = voices.firstOrNull { !it.active }
        }
        if (v == null) {
            // every slot busy, even the fade slots: reuse the quietest one (last resort)
            v = voices.minByOrNull { it.env }!!
            v.active = false
        }
        v.start(note, velocity, bank, sampleRate, Notes.frequency(note, tuningA4).toFloat(), ++orderCounter)
    }

    private fun renderMetronome(mixL: FloatArray, mixR: FloatArray) {
        val on = metronomeOn
        if (on && !wasMetro) {
            metroCount = 0
            metroBeat = 0
        }
        wasMetro = on
        if (!on && clickEnv < 0.001f) return
        val samplesPerBeat = (sampleRate * 60.0 / bpm.coerceIn(20, 300)).toInt()
        val decay = exp(-1f / (0.012f * sampleRate))
        for (i in 0 until BLOCK) {
            if (on && metroCount <= 0) {
                val accent = metroBeat == 0
                clickInc = (if (accent) 1500f else 1000f) / sampleRate
                clickAmp = if (accent) 0.5f else 0.35f
                clickEnv = 1f
                clickPhase = 0f
                onBeat?.invoke(metroBeat)
                metroBeat = (metroBeat + 1) % beatsPerBar.coerceAtLeast(1)
                metroCount = samplesPerBeat
            }
            if (on) metroCount--
            if (clickEnv > 0.001f) {
                val s = sin(TWO_PI * clickPhase) * clickEnv * clickAmp * metronomeVolume
                clickPhase += clickInc
                if (clickPhase >= 1f) clickPhase -= 1f
                clickEnv *= decay
                mixL[i] += s
                mixR[i] += s
            }
        }
    }

    // ---- wavetables ----
    private fun buildTables(ins: Instrument, sr: Int): Array<FloatArray> = Array(BANDS) { band ->
        val topNote = min(band * 12 + 11, 127)
        val topFreq = Notes.frequency(topNote) * 1.05
        val maxH = ((sr * 0.45) / topFreq).toInt().coerceIn(1, ins.harmonics.size)
        val t = FloatArray(TABLE + 2)
        var peak = 1e-6f
        for (i in 0 until TABLE) {
            var s = 0.0
            for (h in 0 until maxH) {
                val a = ins.harmonics[h]
                if (a != 0f) s += a * sin(2.0 * PI * (h + 1) * i / TABLE)
            }
            t[i] = s.toFloat()
            peak = max(peak, abs(t[i]))
        }
        for (i in 0 until TABLE) t[i] /= peak
        t[TABLE] = t[0]
        t[TABLE + 1] = t[1]
        t
    }

    private fun lookup(t: FloatArray, p: Float): Float {
        val x = p * TABLE
        val i = x.toInt()
        val f = x - i
        return t[i] + (t[i + 1] - t[i]) * f
    }

    private fun wrap(p: Float): Float = p - floor(p)

    // ---- voice ----
    private inner class Voice {
        var active = false
        /** Being faded out to make room for a new note; no longer counts as sounding. */
        var fading = false
        var held = false
        var sustained = false
        var note = 0
        var order = 0L
        var stage = ATTACK
        var env = 0f

        private var table = sineTable
        private var attackInc = 0f
        private var decayCoef = 0f
        private var releaseCoef = 0f
        private var sustainLevel = 0f
        private var gain = 0f
        private var panL = 1f
        private var panR = 1f

        private var phase1 = 0f
        private var phase2 = 0f
        private var phaseM = 0f
        private var vibPhase = 0f
        private var inc1 = 0f
        private var inc2 = 0f
        private var incM = 0f
        private var vibInc = 0f
        private var vibDepth = 0f
        private var second = false
        private var fm = false
        private var fmAmp = 0f
        private var fmCoef = 0f

        private var lp = 0f
        private var bright = 1f
        private var brightStep = 0f
        private var brightEnd = 1f

        fun start(note: Int, vel: Int, bank: Bank, sr: Int, freq: Float, order: Long) {
            val ins = bank.instrument
            val fresh = !active
            val v = vel / 127f
            this.note = note
            this.order = order
            active = true
            fading = false
            held = true
            sustained = false
            stage = ATTACK
            table = bank.tables[(note / 12).coerceIn(0, BANDS - 1)]
            if (fresh) {
                env = 0f
                lp = 0f
                phase1 = Math.random().toFloat()
                phase2 = Math.random().toFloat()
                phaseM = 0f
                vibPhase = 0f
            }
            attackInc = 1f / (max(ins.attack, 0.001f) * sr)
            val pitchScale = 2f.pow((60 - note) / 30f).coerceIn(0.25f, 3f)
            decayCoef = exp(-1f / (ins.decay * pitchScale * sr))
            releaseCoef = exp(-1f / (max(ins.release, 0.005f) * sr))
            sustainLevel = ins.sustain
            gain = 0.2f * v.pow(1.4f).coerceAtLeast(0.02f)
            val pan = ((note - 60) / 48f).coerceIn(-1f, 1f) * 0.35f
            val ang = (pan + 1f) * (PI / 4).toFloat()
            panL = cos(ang)
            panR = sin(ang)

            inc1 = freq / sr
            second = ins.detuneCents != 0f
            inc2 = inc1 * 2f.pow(ins.detuneCents / 1200f)
            incM = freq * ins.fmRatio / sr
            fm = ins.fmIndex > 0f && incM < MAX_FM_INC
            fmAmp = ins.fmIndex * (0.4f + 0.6f * v) / TWO_PI
            fmCoef = exp(-1f / (max(ins.fmDecay, 0.005f) * sr))
            vibInc = ins.vibratoHz / sr
            vibDepth = ins.vibratoCents / 1200f * 0.693f // ln2 -> small-signal pitch factor

            val noteScale = max(1f, 2f.pow((note - 60) / 24f))
            bright = min(1f, ins.brightStart * (0.45f + 0.55f * v) * noteScale)
            brightEnd = min(1f, ins.brightEnd * noteScale)
            brightStep = if (ins.brightTime > 0f) (brightEnd - bright) / (ins.brightTime * sr) else 0f
        }

        fun release(pedal: Boolean) {
            held = false
            if (pedal) sustained = true else stage = RELEASE
        }

        fun pedalUp() {
            if (sustained && !held) {
                sustained = false
                stage = RELEASE
            }
        }

        fun fadeOut() {
            fading = true
            held = false
            sustained = false
            stage = RELEASE
            releaseCoef = exp(-1f / (STEAL_FADE_S * sampleRate))
        }

        fun kill() {
            held = false
            sustained = false
            stage = RELEASE
        }

        fun render(mixL: FloatArray, mixR: FloatArray, frames: Int) {
            for (i in 0 until frames) {
                when (stage) {
                    ATTACK -> {
                        env += attackInc
                        if (env >= 1f) {
                            env = 1f
                            stage = DECAY
                        }
                    }
                    DECAY -> env = sustainLevel + (env - sustainLevel) * decayCoef
                    else -> {
                        env *= releaseCoef
                        if (env < 1e-4f) {
                            active = false
                            return
                        }
                    }
                }

                var vib = 1f
                if (vibDepth > 0f) {
                    vibPhase += vibInc
                    if (vibPhase >= 1f) vibPhase -= 1f
                    vib += vibDepth * lookup(sineTable, vibPhase)
                }

                var pm = 0f
                if (fm) {
                    phaseM += incM
                    phaseM -= floor(phaseM) // stays in [0, 1) whatever the step; a single "-= 1" didn't
                    fmAmp *= fmCoef
                    pm = fmAmp * lookup(sineTable, phaseM)
                }

                var s = lookup(table, wrap(phase1 + pm))
                phase1 += inc1 * vib
                if (phase1 >= 1f) phase1 -= 1f
                if (second) {
                    s = (s + lookup(table, wrap(phase2 + pm))) * 0.5f
                    phase2 += inc2 * vib
                    if (phase2 >= 1f) phase2 -= 1f
                }

                if (brightStep != 0f) {
                    bright += brightStep
                    if ((brightStep > 0f && bright >= brightEnd) || (brightStep < 0f && bright <= brightEnd)) {
                        bright = brightEnd
                        brightStep = 0f
                    }
                }
                lp += bright * (s - lp)

                val o = lp * env * gain
                mixL[i] += o * panL
                mixR[i] += o * panR
            }
        }
    }

    // ---- reverb (Freeverb-style) ----
    private class Reverb(sr: Int) {
        private class Comb(size: Int) {
            val buf = FloatArray(size)
            var idx = 0
            var store = 0f
            fun process(x: Float, feedback: Float, damp: Float): Float {
                val y = buf[idx]
                store = y * (1f - damp) + store * damp
                buf[idx] = x + store * feedback
                if (++idx >= buf.size) idx = 0
                return y
            }
        }

        private class Allpass(size: Int) {
            val buf = FloatArray(size)
            var idx = 0
            fun process(x: Float): Float {
                val b = buf[idx]
                val y = -x + b
                buf[idx] = x + b * 0.5f
                if (++idx >= buf.size) idx = 0
                return y
            }
        }

        private val k = sr / 44100f
        private fun n(x: Int) = max(8, (x * k).toInt())
        private val combL = intArrayOf(1116, 1188, 1277, 1356).map { Comb(n(it)) }
        private val combR = intArrayOf(1139, 1211, 1300, 1379).map { Comb(n(it)) }
        private val apL = intArrayOf(556, 441, 341).map { Allpass(n(it)) }
        private val apR = intArrayOf(579, 464, 364).map { Allpass(n(it)) }
        var outL = 0f
        var outR = 0f

        fun process(inL: Float, inR: Float) {
            val x = (inL + inR) * 0.15f
            var l = 0f
            var r = 0f
            for (c in combL) l += c.process(x, 0.82f, 0.25f)
            for (c in combR) r += c.process(x, 0.82f, 0.25f)
            for (a in apL) l = a.process(l)
            for (a in apR) r = a.process(r)
            outL = l
            outR = r
        }
    }
}
