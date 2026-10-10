package com.example.sunpiano

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sunpiano.audio.Instruments
import com.example.sunpiano.audio.SynthEngine
import com.example.sunpiano.data.DebouncedSaver
import com.example.sunpiano.data.NoteEvent
import com.example.sunpiano.data.Recording
import com.example.sunpiano.data.RecordingStore
import com.example.sunpiano.data.Settings
import com.example.sunpiano.data.SettingsRepository
import com.example.sunpiano.midi.BluetoothMidiScanner
import com.example.sunpiano.midi.FoundMidiDevice
import com.example.sunpiano.midi.MidiInput
import com.example.sunpiano.music.KeyboardLayout
import com.example.sunpiano.music.NoteHolders
import com.example.sunpiano.music.Notes
import com.example.sunpiano.music.SustainSources
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Owns the synth engine and all piano state. Every method must be called on the main thread,
 * which keeps note bookkeeping free of locks.
 */
class PianoViewModel(app: Application) : AndroidViewModel(app) {
    val layout = KeyboardLayout()
    private val engine = SynthEngine()
    private val settingsRepo = SettingsRepository(app)
    private val store = RecordingStore(app)

    /** Writes settings once they stop changing, instead of on every slider movement. */
    private val settingsSaver = DebouncedSaver(viewModelScope, SETTINGS_SAVE_DELAY_MS) { s: Settings -> settingsRepo.save(s) }

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _pressed = MutableStateFlow<Set<Int>>(emptySet())
    /** Keys currently lit on screen (touch, MIDI, computer keyboard and playback). */
    val pressed: StateFlow<Set<Int>> = _pressed.asStateFlow()

    private val sustainSources = SustainSources()
    private val _sustain = MutableStateFlow(false)
    /** Whether the pedal is down, from any source. */
    val sustain: StateFlow<Boolean> = _sustain.asStateFlow()
    private val _sustainLatched = MutableStateFlow(false)
    /** Whether the on-screen Sustain button is latched on. */
    val sustainLatched: StateFlow<Boolean> = _sustainLatched.asStateFlow()

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _progress.asStateFlow()

    private val _metronomeOn = MutableStateFlow(false)
    val metronomeOn: StateFlow<Boolean> = _metronomeOn.asStateFlow()

    private val _beat = MutableStateFlow(-1)
    /** Current metronome beat within the bar, or -1 when stopped. */
    val beat: StateFlow<Int> = _beat.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** A problem to tell the user about, or null. The UI shows it and then calls [messageShown]. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    private val _midiDevice = MutableStateFlow<String?>(null)
    /** Name of the open MIDI device (USB, Bluetooth or virtual), or null. */
    val midiDevice: StateFlow<String?> = _midiDevice.asStateFlow()
    private val _bluetoothMidiDevice = MutableStateFlow<String?>(null)
    /** Name of the open MIDI device if it's a Bluetooth one, or null. */
    val bluetoothMidiDevice: StateFlow<String?> = _bluetoothMidiDevice.asStateFlow()

    private val _firstWhite = MutableStateFlow(layout.whiteIndexOf(48).toFloat())
    /** Left edge of the visible keyboard, in white-key units. */
    val firstWhite: StateFlow<Float> = _firstWhite.asStateFlow()

    /** Live presses and playback notes; a note stops only when its last holder lets go. */
    private val holders = NoteHolders()
    private var playbackJob: Job? = null

    private var recStart = -1L
    private val recEvents = ArrayList<NoteEvent>()

    private val midi = MidiInput(
        app,
        onNoteOn = { n, v -> noteOn(n, v) },
        onNoteOff = { noteOff(it) },
        onSustain = { setSustain(SustainSources.Source.MIDI, it) },
        onDeviceChanged = { name, bluetooth ->
            _midiDevice.value = name
            _bluetoothMidiDevice.value = name.takeIf { bluetooth }
        },
        onBluetoothFailed = { _message.value = "Couldn't connect to $it. Make sure it's switched on and nearby, then try again." },
    )

    private val bluetoothScanner = BluetoothMidiScanner(app).apply {
        onScanFailed = { _message.value = "Couldn't search for Bluetooth devices. Try turning Bluetooth off and on." }
    }
    /** True if this device can use Bluetooth MIDI keyboards. */
    val bluetoothMidiSupported: Boolean get() = bluetoothScanner.isSupported
    /** Bluetooth MIDI keyboards found by the last search. */
    val bluetoothDevices: StateFlow<List<FoundMidiDevice>> get() = bluetoothScanner.found
    val bluetoothScanning: StateFlow<Boolean> get() = bluetoothScanner.scanning

    private val audioManager = app.getSystemService(AudioManager::class.java)

    /** Headphones unplugged (or Bluetooth audio gone): stop what would now blare from the speaker. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onAudioBecomingNoisy()
        }
    }
    private var noisyRegistered = false

    /**
     * Recording playback takes transient audio focus, so music apps pause meanwhile and resume
     * afterwards, and it stops if another app (a call, a video) takes focus. Live playing and the
     * metronome don't take focus, so you can still play along with music.
     */
    private val playbackFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopPlayback()
        }, Handler(Looper.getMainLooper()))
        .build()

    /** Settings changes made before the saved settings finished loading; null once loaded. */
    private var changesBeforeLoad: MutableList<(Settings) -> Settings>? = ArrayList()

    init {
        engine.onBeat = { _beat.value = it }
        // decoded piano recordings cached by earlier versions (about 51 MB)
        File(app.cacheDir, "piano-pcm").deleteRecursively()
        viewModelScope.launch {
            var loaded = settingsRepo.flow.first()
            // Changes made while loading were applied to the defaults; replay them on the saved
            // settings so neither the saved settings nor those changes are lost.
            val early = changesBeforeLoad.orEmpty()
            changesBeforeLoad = null
            for (change in early) loaded = change(loaded)
            val changed = early.isNotEmpty()
            _settings.value = loaded
            applySettings(loaded)
            clampScroll()
            if (changed) settingsSaver.submit(loaded)
            _recordings.value = store.list()
        }
    }

    // ---- lifecycle ----
    private var foreground = false

    /** Safe to call again while already in the foreground (e.g. by a recreated activity). */
    fun onForeground() {
        if (foreground) return
        foreground = true
        engine.start()
        midi.start()
        ContextCompat.registerReceiver(
            getApplication(), noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    fun onBackground() {
        settingsSaver.flush() // don't lose a change made just before leaving
        if (!foreground) return
        foreground = false
        stopPlayback()
        if (_isRecording.value) stopRecording()
        releaseAll()
        setMetronome(false)
        bluetoothScanner.stop()
        midi.stop()
        engine.stop()
        unregisterNoisy()
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        noisyRegistered = false
        getApplication<Application>().unregisterReceiver(noisyReceiver)
    }

    fun onAudioBecomingNoisy() {
        stopPlayback()
        setMetronome(false)
    }

    override fun onCleared() {
        unregisterNoisy()
        bluetoothScanner.stop()
        midi.stop()
        engine.stop()
    }

    // ---- settings ----
    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        applySettings(next)
        // Before loading finishes `next` is built on the defaults: saving it would overwrite the
        // user's settings, so remember the change instead (it's saved once replayed on load).
        changesBeforeLoad?.add(transform) ?: settingsSaver.submit(next)
        clampScroll()
    }

    private fun applySettings(s: Settings) {
        engine.setInstrument(Instruments.byId(s.instrumentId))
        engine.volume = s.volume
        engine.reverbLevel = s.reverb
        engine.tuningA4 = s.tuning.toDouble()
        engine.bpm = s.bpm
        engine.beatsPerBar = s.beatsPerBar
        engine.metronomeVolume = s.metronomeVolume
    }

    // ---- playing ----
    fun noteOn(key: Int, velocity: Int = 100) {
        if (key !in layout.low..layout.high) return
        val sounding = (key + _settings.value.transpose).coerceIn(0, 127)
        // A second holder re-strikes the note. Record that as a note-off and a new note-on, so
        // every note-on in the recording (and the exported .mid) has its own note-off.
        if (holders.isHeld(sounding)) record(sounding, 0)
        holders.press(key, sounding)
        engine.noteOn(sounding, velocity)
        record(sounding, velocity)
        publishPressed()
    }

    fun noteOff(key: Int) {
        if (key !in holders.keys) return
        holders.release(key)?.let { sounding ->
            engine.noteOff(sounding)
            record(sounding, 0)
        }
        publishPressed()
    }

    /** Presses or releases the pedal for one [source]; it stays down while any source holds it. */
    fun setSustain(source: SustainSources.Source, down: Boolean) {
        val pedal = sustainSources.set(source, down)
        if (source == SustainSources.Source.BUTTON) _sustainLatched.value = down
        if (_sustain.value == pedal) return
        _sustain.value = pedal
        engine.setSustain(pedal)
        record(NoteEvent.PEDAL, if (pedal) 127 else 0)
    }

    fun toggleSustainButton() = setSustain(SustainSources.Source.BUTTON, !_sustainLatched.value)

    fun releaseAll() {
        while (holders.keys.isNotEmpty()) noteOff(holders.keys.first())
    }

    // ---- Bluetooth MIDI ----
    fun hasBluetoothPermissions() = bluetoothScanner.hasPermissions()

    /** Searches for Bluetooth MIDI keyboards; the caller asks for the permissions first. */
    fun searchBluetoothMidi() {
        val problem = if (!bluetoothScanner.hasPermissions()) {
            val permission = if (Build.VERSION.SDK_INT >= 31) "Nearby devices" else "Location"
            "Sun Piano needs the $permission permission to find Bluetooth MIDI keyboards. You can allow it in the app's settings."
        } else bluetoothScanner.problem()
        if (problem != null) _message.value = problem else bluetoothScanner.start()
    }

    fun connectBluetoothMidi(found: FoundMidiDevice) {
        bluetoothScanner.stop()
        bluetoothScanner.clear()
        val device = bluetoothScanner.device(found.address) ?: return
        midi.openBluetooth(device, found.name)
    }

    fun setMetronome(on: Boolean) {
        _metronomeOn.value = on
        engine.metronomeOn = on
        if (!on) _beat.value = -1
    }

    private fun publishPressed() {
        _pressed.value = holders.keys + holders.playbackNotes
    }

    // ---- scrolling ----
    fun setFirstWhite(value: Float) {
        _firstWhite.value = value.coerceIn(0f, maxFirstWhite())
    }

    private fun maxFirstWhite() = (layout.whiteCount - _settings.value.visibleKeys).coerceAtLeast(0).toFloat()
    private fun clampScroll() = setFirstWhite(_firstWhite.value)

    /** Shifts the keyboard by one octave (7 white keys). */
    fun shiftOctave(dir: Int) = setFirstWhite(_firstWhite.value + 7 * dir)

    // ---- recording ----
    private fun record(note: Int, velocity: Int) {
        if (!_isRecording.value) return
        val now = SystemClock.elapsedRealtime()
        if (recStart < 0) recStart = now
        recEvents += NoteEvent(now - recStart, note, velocity)
    }

    fun toggleRecording() = if (_isRecording.value) stopRecording() else startRecording()

    private fun startRecording() {
        stopPlayback()
        recEvents.clear()
        recStart = -1
        _isRecording.value = true
        // notes already sounding when recording starts are not captured; a held pedal is
        if (_sustain.value) record(NoteEvent.PEDAL, 127)
    }

    private fun stopRecording() {
        // close any still-held notes so the recording is well formed
        holders.liveSounding().forEach { record(it, 0) }
        if (_sustain.value) record(NoteEvent.PEDAL, 0)
        _isRecording.value = false
        val events = recEvents.toList()
        recEvents.clear()
        if (events.none { it.note != NoteEvent.PEDAL }) return
        val now = System.currentTimeMillis()
        val name = "Recording " + SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(now))
        val rec = Recording(UUID.randomUUID().toString(), name, now, events.maxOf { it.timeMs }, events)
        viewModelScope.launch {
            saveOrReport(rec, "Couldn't save the recording")
            _recordings.value = store.list()
        }
    }

    // ---- playback ----
    fun togglePlayback(rec: Recording) {
        if (_playingId.value == rec.id) stopPlayback() else play(rec)
    }

    private fun play(rec: Recording) {
        stopPlayback()
        if (_isRecording.value) stopRecording()
        if (audioManager.requestAudioFocus(playbackFocus) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            _message.value = "Can't play the recording right now: another app, such as a call, is using the sound."
            return
        }
        _playingId.value = rec.id
        playbackJob = viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()
                for (e in rec.events.sortedBy { it.timeMs }) {
                    val wait = e.timeMs - (SystemClock.elapsedRealtime() - start)
                    if (wait > 0) delay(wait)
                    _progress.value = if (rec.durationMs > 0) e.timeMs.toFloat() / rec.durationMs else 0f
                    when {
                        e.note == NoteEvent.PEDAL -> setSustain(SustainSources.Source.PLAYBACK, e.velocity > 0)
                        e.velocity > 0 -> {
                            holders.playbackPress(e.note)
                            engine.noteOn(e.note, e.velocity)
                            publishPressed()
                        }
                        else -> {
                            holders.playbackRelease(e.note)?.let { engine.noteOff(it) }
                            publishPressed()
                        }
                    }
                }
                delay(400)
            } finally {
                holders.playbackNotes.toList().forEach { n -> holders.playbackRelease(n)?.let { engine.noteOff(it) } }
                setSustain(SustainSources.Source.PLAYBACK, false)
                publishPressed()
                // unless another recording has started playing meanwhile
                if (playbackJob == null || playbackJob === coroutineContext[Job]) {
                    _playingId.value = null
                    _progress.value = 0f
                    audioManager.abandonAudioFocusRequest(playbackFocus)
                }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        // now, not in the job's cleanup, which runs later: a recording started next mustn't see it
        setSustain(SustainSources.Source.PLAYBACK, false)
    }

    // ---- library ----
    fun deleteRecording(rec: Recording) {
        if (_playingId.value == rec.id) stopPlayback()
        viewModelScope.launch {
            store.delete(rec.id)
            _recordings.value = store.list()
        }
    }

    fun renameRecording(rec: Recording, name: String) {
        val clean = name.trim().ifEmpty { return }
        viewModelScope.launch {
            saveOrReport(rec.copy(name = clean), "Couldn't rename the recording")
            _recordings.value = store.list()
        }
    }

    /** Writes [rec] as a .mid file for sharing; on failure tells the user and returns null. */
    suspend fun exportMidi(rec: Recording): File? = try {
        store.exportMidi(rec)
    } catch (e: IOException) {
        android.util.Log.e("SunPiano", "Couldn't export ${rec.name}", e)
        _message.value = "Couldn't export \"${rec.name}\" as MIDI. The device storage may be full."
        null
    }

    /** Saves [rec]; on failure tells the user instead of losing it silently (or crashing). */
    private suspend fun saveOrReport(rec: Recording, failure: String) {
        try {
            store.save(rec)
        } catch (e: IOException) {
            android.util.Log.e("SunPiano", "$failure: ${rec.name}", e)
            _message.value = "$failure (\"${rec.name}\"). The device storage may be full."
        }
    }

    // ---- computer keyboard ----
    // Keys are matched by position, never by the character they type: a character table only
    // works on a US layout (on an Arabic layout just one key matched).
    companion object {
        private const val SETTINGS_SAVE_DELAY_MS = 500L

        // Linux evdev scan codes (see Android's Generic.kl), unaffected by the selected layout.
        // Rows by US label: Z S X D C V G B H N J M , L . ; /  and  Q 2 W 3 E R 5 T 6 Y 7 U I 9 O 0 P [ = ]
        private val LOWER_SCANS = intArrayOf(44, 31, 45, 32, 46, 47, 34, 48, 35, 49, 36, 50, 51, 38, 52, 39, 53)
        private val UPPER_SCANS = intArrayOf(16, 3, 17, 4, 18, 19, 6, 20, 7, 21, 8, 22, 23, 10, 24, 11, 25, 26, 13, 27)

        // Fallback for keyboards that report no scan code. Some layouts (AZERTY, QWERTZ) remap these.
        private val LOWER_CODES = intArrayOf(
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_SLASH,
        )
        private val UPPER_CODES = intArrayOf(
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_RIGHT_BRACKET,
        )

        /** Maps a physical key to a piano key (lower row from C3, upper row from C4), or null. */
        fun keyForPhysicalKey(scanCode: Int, keyCode: Int): Int? {
            if (scanCode > 0) return lookup(scanCode, LOWER_SCANS, UPPER_SCANS)
            return lookup(keyCode, LOWER_CODES, UPPER_CODES)
        }

        private fun lookup(code: Int, lower: IntArray, upper: IntArray): Int? {
            lower.indexOf(code).takeIf { it >= 0 }?.let { return 48 + it }
            upper.indexOf(code).takeIf { it >= 0 }?.let { return Notes.MIDDLE_C + it }
            return null
        }
    }
}
