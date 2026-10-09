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

    /**
     * Writes a .mid file into the cache dir (exposed through FileProvider) and returns it. Each
     * recording gets its own folder, so recordings with the same name can't overwrite each
     * other's export. Throws [IOException] if it can't be written.
     */
    suspend fun exportMidi(r: Recording): File = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "exports/${r.id}")
        out.deleteRecursively() // an earlier export under a previous name
        if (!out.mkdirs() && !out.isDirectory) throw IOException("could not create ${out.path}")
        File(out, exportFileName(r.name)).also { it.writeBytes(MidiFileWriter.write(r)) }
    }

    companion object {
        /**
         * File name for sharing a recording called [name]: keeps letters of any script (Arabic,
         * accents…) and replaces only characters file systems reject.
         */
        fun exportFileName(name: String): String {
            var clean = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_").trim(' ', '.', '_')
            if (clean.length > 80) {
                clean = clean.take(80)
                if (clean.last().isHighSurrogate()) clean = clean.dropLast(1) // don't split an emoji
            }
            return clean.ifEmpty { "recording" } + ".mid"
        }
    }
}
