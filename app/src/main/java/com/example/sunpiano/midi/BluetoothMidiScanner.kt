package com.example.sunpiano.midi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A Bluetooth MIDI keyboard seen by a scan. */
data class FoundMidiDevice(val address: String, val name: String)

/**
 * Finds Bluetooth LE MIDI keyboards. Android only lets an app use one after the app has found it
 * and opened it with `MidiManager.openBluetoothDevice()`; they don't show up on their own.
 * Callers must check [hasPermissions] and [problem] before [start].
 */
@SuppressLint("MissingPermission") // start() and the scan callback run only after hasPermissions()
class BluetoothMidiScanner(private val context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val handler = Handler(Looper.getMainLooper())

    /** True if the device can use Bluetooth MIDI at all. */
    val isSupported: Boolean = adapter != null &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)

    private val _found = MutableStateFlow<List<FoundMidiDevice>>(emptyList())
    val found: StateFlow<List<FoundMidiDevice>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Called on the main thread if the system refuses to scan. */
    var onScanFailed: (() -> Unit)? = null

    fun hasPermissions(): Boolean = PERMISSIONS.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    /** Why a scan can't run now (Bluetooth off, or Location off where scans need it), or null. */
    fun problem(): String? = when {
        adapter?.isEnabled != true -> "Bluetooth is off. Turn it on, then search again."
        Build.VERSION.SDK_INT in 28..30 &&
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled == false ->
            "Location is off. Android 11 and older need it on to find Bluetooth devices."
        else -> null
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = add(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::add)
        override fun onScanFailed(errorCode: Int) {
            handler.removeCallbacks(timeout)
            _scanning.value = false
            onScanFailed?.invoke()
        }
    }

    private val timeout = Runnable { stop() }

    private fun add(result: ScanResult) {
        if (!_scanning.value) return // a late result from a stopped scan
        val address = result.device.address
        if (_found.value.any { it.address == address }) return
        val name = result.scanRecord?.deviceName ?: result.device.name ?: address
        _found.value = _found.value + FoundMidiDevice(address, name)
    }

    /** Scans for [SCAN_MS]; results appear in [found]. */
    fun start() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        stop()
        _found.value = emptyList()
        val filter = ScanFilter.Builder().setServiceUuid(MIDI_SERVICE).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, callback)
        _scanning.value = true
        handler.postDelayed(timeout, SCAN_MS)
    }

    fun stop() {
        handler.removeCallbacks(timeout)
        if (!_scanning.value) return
        _scanning.value = false
        if (adapter?.isEnabled == true && hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
    }

    /** Forgets the search results. */
    fun clear() {
        _found.value = emptyList()
    }

    /** The device behind [address], for opening. */
    fun device(address: String): BluetoothDevice? = adapter?.getRemoteDevice(address)

    companion object {
        /** The Bluetooth LE MIDI service, from the MIDI Association's BLE-MIDI specification. */
        val MIDI_SERVICE: ParcelUuid = ParcelUuid.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        const val SCAN_MS = 15_000L

        /** Runtime permissions needed to scan for and connect to Bluetooth devices. */
        val PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
