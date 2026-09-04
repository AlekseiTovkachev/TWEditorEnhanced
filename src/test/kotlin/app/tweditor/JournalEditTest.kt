package app.tweditor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class JournalEditTest {
    @Test
    fun addedJournalEntryPersistsThroughRoundTrip(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val countBefore = loaded.session.getJournalData()!!.entries.size

        loaded.session.addJournalEntry("character", "zoltan/ras")
        assertTrue(loaded.session.isJournalDirty(), "editing the journal must flag the quest database for saving")
        assertTrue(loaded.session.isDataModified())
        assertEquals(countBefore + 1, loaded.session.getJournalData()!!.entries.size)

        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(environment, save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!, loaded.questDBName + ".qdb")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries outside the module .sav container, player.utc, the .smm file and save.qdb changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        val added = reloaded.session.getJournalData()!!.entries.firstOrNull {
            it.category == "character" && it.entryId == "zoltan/ras"
        }
        assertTrue(added != null, "the added journal entry must survive the write/reload round trip")
        assertFalse(added!!.isRead, "a freshly added entry is unread")
    }

    @Test
    fun removedJournalEntryPersistsThroughRoundTrip(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        loaded.session.removeJournalEntries(loaded.session.getJournalData()!!.entriesInCategory("tutorial"))
        assertEquals(10, loaded.session.getJournalData()!!.entries.size, "13 fixture entries minus 3 tutorial entries")

        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(environment, save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!, loaded.questDBName + ".qdb")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries outside the module .sav container, player.utc, the .smm file and save.qdb changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertTrue(reloaded.session.getJournalData()!!.entriesInCategory("tutorial").isEmpty(),
            "the removed tutorial entries must be gone after the round trip")
        assertEquals(10, reloaded.session.getJournalData()!!.entries.size)
    }

    @Test
    fun removalMatchesEntryIdsCaseInsensitively(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        assertTrue(loaded.session.getJournalData()!!.entries.any { it.entryId == "tutorial35" },
            "the fixture stores Tutorial35 with a capital letter")

        loaded.session.removeJournalEntries(listOf(JournalEntry("tutorial", "Tutorial35", false)))

        assertTrue(loaded.session.getJournalData()!!.entriesInCategory("tutorial").size == 2,
            "removal must match the entry id regardless of case")
    }

    @Test
    fun localSavesSurviveAggressiveJournalEditing(@TempDir tempDir: Path) {
        val savesDir = Path.of(System.getProperty("tweditor.localSaves", ".local-saves")).toFile()
        val saves = savesDir.listFiles { _, name -> name.endsWith(".TheWitcherSave") }
        org.junit.jupiter.api.Assumptions.assumeTrue(saves != null && saves.isNotEmpty(),
            "no local saves in '" + savesDir + "'")

        for (save in saves) {
            val workDir = Files.createDirectory(tempDir.resolve("edit-" + save.name))
            val copy = Files.copy(save.toPath(), workDir.resolve(save.name)).toFile()
            val loaded = SaveSeamSupport.load(environment, copy, workDir)
            val pristine = SaveDatabase(environment, copy)
            pristine.load()
            val before = SaveSeamSupport.entryDigests(pristine)

            loaded.session.addJournalEntry("character", "abigail/saved")
            loaded.session.addJournalEntry("recipe", "it_potion_001")
            loaded.session.addJournalEntry("bestiary", "ghoul/w/1")
            loaded.session.removeJournalEntries(loaded.session.getJournalData()!!.entriesInCategory("tutorial"))

            SaveSeamSupport.save(loaded)

            val repacked = SaveDatabase(environment, copy)
            repacked.load()
            val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
            val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!, loaded.questDBName + ".qdb")
            assertTrue(allowedToChange.containsAll(rewritten), save.name + ": unexpected entries changed: " + rewritten)

            val reloaded = SaveSeamSupport.load(environment, copy, workDir)
            assertTrue(reloaded.session.getJournalData()!!.entries.any { it.entryId == "abigail/saved" },
                save.name + ": added entry must survive")
            assertTrue(reloaded.session.getJournalData()!!.entriesInCategory("tutorial").isEmpty(),
                save.name + ": removed entries must be gone")
        }
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
