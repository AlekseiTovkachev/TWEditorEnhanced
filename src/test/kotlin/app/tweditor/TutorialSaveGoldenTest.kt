package app.tweditor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class TutorialSaveGoldenTest {
    @Test
    fun fixtureIsRecognizedAsAValidSaveArchive(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        assertEquals(SaveSeamSupport.EXPECTED_SAVE_NAME, loaded.saveDatabase!!.getName())
        assertTrue(Regex("\\d{6} .*").matches(loaded.saveDatabase!!.getName()),
            "save name must start with six digits and a space for the editor to accept it")
        assertEquals(143, loaded.saveDatabase!!.entries.size)

        val modEntry = loaded.saveDatabase!!.getEntry(loaded.modName!!)
        val smmEntry = loaded.saveDatabase!!.getEntry(loaded.smmName!!)
        assertNotNull(modEntry, "module save entry missing")
        assertNotNull(smmEntry, "smm save entry missing")
        assertTrue(modEntry!!.isCompressed, "module .sav entries are zlib-compressed")
        assertNotNull(loaded.saveDatabase!!.getEntry("player.utc"))
        assertNotNull(loaded.saveDatabase!!.getEntry("savenfo.txt"))
        assertNotNull(loaded.saveDatabase!!.getEntry(loaded.questDBName + ".qdb"))
        assertNotNull(loaded.saveDatabase!!.getEntry("q0001.qst"))
        assertEquals("kaer_morhen.sav", loaded.modName)
        assertEquals("save_000007.smm", loaded.smmName)
    }

    @Test
    fun parsedPlayerFactsMatchTheFixture(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        assertEquals(0, loaded.player!!.getInteger("ExpLevel"))
        assertEquals(30, loaded.player!!.getInteger("Experience"))
        assertEquals(0, loaded.player!!.getInteger("Gold"))
        assertEquals(248, loaded.player!!.getInteger("CurrentHitPoints"))
        assertEquals(25, loaded.player!!.getInteger("CurrentEndurance"))
        assertEquals(0, loaded.player!!.getInteger("CurrentToxicity"))
        assertEquals(0, loaded.player!!.getInteger("TalentBronze"))
        assertEquals(0, loaded.player!!.getInteger("TalentSilver"))
        assertEquals(0, loaded.player!!.getInteger("TalentGold"))
        assertEquals(0, (loaded.player!!.getElement("ItemList")!!.getValue() as DBList).getElementCount())

        val playerTop = loaded.playerDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        assertEquals("Wiedzmin", playerTop.getString("Tag"))
        assertEquals(30, playerTop.getInteger("Experience"))
        assertEquals(0, playerTop.getInteger("Gold"))
    }

    @Test
    fun parsedQuestFactsMatchTheFixture(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val records = SaveSeamSupport.questRecords(loaded)

        assertEquals(137, loaded.questCount)
        assertEquals(137, records.size)
        assertEquals("Defending Kaer Morhen", records["q0001"]!!.questName)
        assertEquals(1, records["q0001"]!!.questState)
        assertEquals(2, records["p_init"]!!.questState)
        assertEquals("A Potion for Triss", records["q0002"]!!.questName)
        assertEquals(0, records["q0002"]!!.questState)
    }

    @Test
    fun roundTripLeavesUntouchedEntriesByteIdentical(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(environment, save)
        repacked.load()
        val after = SaveSeamSupport.entryDigests(repacked)

        assertEquals(before.keys, after.keys)
        val rewritten = SaveSeamSupport.changedEntries(before, after)
        assertEquals(setOf(loaded.modName!!, "player.utc", loaded.smmName!!), rewritten,
            "only the module .sav container, player.utc and the .smm file are rewritten by a save; every other entry must be byte-identical")
    }

    @Test
    fun roundTripReparsesWithSameFacts(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        SaveSeamSupport.save(loaded)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertEquals(0, reloaded.player!!.getInteger("ExpLevel"))
        assertEquals(30, reloaded.player!!.getInteger("Experience"))
        assertEquals(0, reloaded.player!!.getInteger("Gold"))
        assertEquals(248, reloaded.player!!.getInteger("CurrentHitPoints"))
        assertEquals(25, reloaded.player!!.getInteger("CurrentEndurance"))
        assertEquals(137, reloaded.questCount)

        val records = SaveSeamSupport.questRecords(reloaded)
        assertEquals("Defending Kaer Morhen", records["q0001"]!!.questName)
        assertEquals(1, records["q0001"]!!.questState)
        assertEquals(2, records["p_init"]!!.questState)
        assertEquals("A Potion for Triss", records["q0002"]!!.questName)
    }

    @Test
    fun editedGoldPersistsThroughRoundTrip(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        loaded.player!!.setInteger("Gold", 500)
        SaveSeamSupport.save(loaded)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertEquals(500, reloaded.player!!.getInteger("Gold"))
        assertEquals(30, reloaded.player!!.getInteger("Experience"))
        assertEquals(248, reloaded.player!!.getInteger("CurrentHitPoints"))
        assertEquals(137, reloaded.questCount)
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
