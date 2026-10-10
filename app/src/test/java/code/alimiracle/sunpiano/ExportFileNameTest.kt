package code.alimiracle.sunpiano

import code.alimiracle.sunpiano.data.RecordingStore.Companion.exportFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Export file names keep any script and stay valid (bug.md #15). */
class ExportFileNameTest {
    @Test fun keepsNonLatinNames() {
        assertEquals("تسجيل جميل.mid", exportFileName("تسجيل جميل"))
        assertEquals("Für Elise – Übung.mid", exportFileName("Für Elise – Übung"))
    }

    @Test fun keepsOrdinaryNames() {
        assertEquals("Recording Oct 10, 01_20.mid", exportFileName("Recording Oct 10, 01:20"))
    }

    @Test fun replacesCharactersFileSystemsReject() {
        assertEquals("a_b_c_d.mid", exportFileName("a/b\\c?*d"))
        assertEquals("tab_here.mid", exportFileName("tab\there"))
    }

    @Test fun emptyOrSymbolOnlyNamesGetADefault() {
        assertEquals("recording.mid", exportFileName(""))
        assertEquals("recording.mid", exportFileName("///"))
        assertEquals("recording.mid", exportFileName(" .. "))
    }

    @Test fun longNamesAreCutWithoutSplittingAnEmoji() {
        val name = "a".repeat(79) + "🎹🎹"
        val file = exportFileName(name)
        assertTrue(file.length <= 80 + ".mid".length)
        assertFalse("ends in half an emoji", file.removeSuffix(".mid").last().isHighSurrogate())
        assertEquals("a".repeat(79) + ".mid", file)
    }
}
