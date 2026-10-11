package code.alimiracle.sunpiano.data

import android.content.Context
import code.alimiracle.sunpiano.audio.AudioExporter
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
        File(exportDir(r), exportFileName(r.name)).also { it.writeBytes(MidiFileWriter.write(r)) }
    }

    /**
     * Renders [r] (with its own instrument, see [AudioExporter]) and writes it as an .m4a (AAC) audio file, like
     * [exportMidi]. Takes a while for a long recording; [onProgress] gets 0..1.
     */
    suspend fun exportAudio(r: Recording, settings: Settings, onProgress: (Float) -> Unit): File = withContext(Dispatchers.Default) {
        File(exportDir(r), exportFileName(r.name, "m4a")).also { AudioExporter.exportM4a(r, settings, it, onProgress) }
    }

    /** An empty folder for [r]'s export: an earlier one may be under a previous name. */
    private fun exportDir(r: Recording): File {
        val out = File(context.cacheDir, "exports/${r.id}")
        out.deleteRecursively()
        if (!out.mkdirs() && !out.isDirectory) throw IOException("could not create ${out.path}")
        return out
    }

    companion object {
        /**
         * File name for sharing a recording called [name]: keeps letters of any script (Arabic,
         * accents…) and replaces only characters file systems reject.
         */
        fun exportFileName(name: String, extension: String = "mid"): String {
            var clean = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_").trim(' ', '.', '_')
            if (clean.length > 80) {
                clean = clean.take(80)
                if (clean.last().isHighSurrogate()) clean = clean.dropLast(1) // don't split an emoji
            }
            return clean.ifEmpty { "recording" } + "." + extension
        }
    }
}
