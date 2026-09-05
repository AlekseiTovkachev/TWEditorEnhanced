package app.tweditor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class SaveSummaryReaderTest {
    /**
     * The in-game save name embedded in savenfo.txt (UTF-8), which is shorter than the file name.
     */
    private val saveInfoName = "Территория Каэр Морхен"
    @Test
    fun readsNameLevelAndScreenshotFromTheFixture(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)

        val summary = SaveSummaryReader.read(environment, save)

        assertEquals(saveInfoName, summary.saveName)
        assertEquals(0, summary.level)
        assertEquals(64, summary.screenshot!!.width)
        assertEquals(64, summary.screenshot!!.height)
        assertEquals(save.lastModified(), summary.lastModified)
    }

    @Test
    fun fallsBackToTheFileNameWhenFactsAreMissing(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val saveDatabase = SaveDatabase(environment, save)
        saveDatabase.load()
        saveDatabase.entries.removeIf { entry ->
            entry.resourceName == "savenfo.txt" || entry.resourceName.endsWith(".tga")
        }
        saveDatabase.save()

        val summary = SaveSummaryReader.read(environment, save)

        assertEquals(SaveSeamSupport.EXPECTED_SAVE_NAME, summary.saveName)
        assertEquals(0, summary.level)
        assertNull(summary.screenshot)
    }

    @Test
    fun reportsAnUnreadablePlayerRecordAsUnknownLevel(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val saveDatabase = SaveDatabase(environment, save)
        saveDatabase.load()
        val garbage = tempDir.resolve("garbage.utc").toFile()
        Files.write(garbage.toPath(), byteArrayOf(1, 2, 3))
        saveDatabase.addEntry("player.utc", garbage)
        saveDatabase.save()

        val summary = SaveSummaryReader.read(environment, save)

        assertEquals(saveInfoName, summary.saveName)
        assertEquals(-1, summary.level)
        assertNotNull(summary.screenshot)
    }

    @Test
    fun cachesSummariesPerFileAndModificationTime(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        var reads = 0
        val cache = SaveSummaryCache({ file -> reads++; SaveSummaryReader.read(environment, file) })

        val first = cache.get(save)
        val second = cache.get(save)

        assertEquals(1, reads, "the same file must be parsed only once while unmodified")
        assertTrue(first === second)
        cache.get(touch(save, tempDir))
        assertEquals(2, reads, "a modified file must be parsed again")
    }

    private fun touch(save: File, tempDir: Path): File {
        val modified = Files.copy(save.toPath(), tempDir.resolve("touched.TheWitcherSave"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING).toFile()
        modified.setLastModified(save.lastModified() + 60000)
        return modified
    }

    companion object {
        lateinit var environment: AppEnvironment

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }
    }
}
