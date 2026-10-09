package com.example.sunpiano.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Stores recordings as JSON files in the app's private storage. */
class RecordingStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "recordings").also { it.mkdirs() }

    suspend fun list(): List<Recording> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { runCatching { Recording.fromJson(it.readText()) }.getOrNull() }
            .sortedByDescending { it.createdAt }
    }

    /** Throws [IOException] if the recording couldn't be written (e.g. storage full). */
    suspend fun save(r: Recording) = withContext(Dispatchers.IO) {
        val tmp = File(dir, "${r.id}.tmp")
        try {
            tmp.writeText(r.toJson())
            if (!tmp.renameTo(File(dir, "${r.id}.json"))) throw IOException("could not move ${tmp.name} into place")
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
        Unit
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        Unit
    }

    /** Writes a .mid file into the cache dir (exposed through FileProvider) and returns it. */
    suspend fun exportMidi(r: Recording): File = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "exports").also { it.mkdirs() }
        val safe = r.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "recording" }
        File(out, "$safe.mid").also { it.writeBytes(MidiFileWriter.write(r)) }
    }
}
