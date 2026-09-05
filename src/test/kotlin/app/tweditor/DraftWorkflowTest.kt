package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DraftWorkflowTest {
    @Test
    fun revertRestoresTheBaselineInMemory(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val session = loaded.session
        val journalCountBefore = session.getJournalData()!!.entries.size
        session.createBaseline()

        playerListOf(loaded).setInteger("Gold", 500)
        session.addJournalEntry("quest", "draft_test")
        assertTrue(session.isDataModified(), "edits mark the session modified")
        assertTrue(session.isDraftDirty(), "edits mark the draft dirty")

        assertTrue(session.revertToBaseline())

        assertEquals(0, playerListOf(loaded).getInteger("Gold"), "gold reverts to the baseline value")
        assertEquals(journalCountBefore, session.getJournalData()!!.entries.size,
            "journal additions are discarded by the revert")
        assertFalse(session.isDataModified())
        assertFalse(session.isDraftDirty())
    }

    @Test
    fun applyMovesTheRevertPoint(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val session = loaded.session
        session.createBaseline()

        playerListOf(loaded).setInteger("Gold", 500)
        session.setDataModified(true)
        session.applyDraft()
        assertFalse(session.isDraftDirty(), "apply commits the draft")
        assertTrue(session.isDataModified(), "applied edits are still unsaved")

        playerListOf(loaded).setInteger("Gold", 700)
        session.setDataModified(true)
        assertTrue(session.revertToBaseline())
        assertEquals(500, playerListOf(loaded).getInteger("Gold"),
            "revert must return to the applied state, not the file on disk")
    }

    @Test
    fun validationGatesRunAtTheApplyAndSavePoints(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val session = loaded.session

        assertTrue(session.runValidation().isEmpty(), "no gates registered yet")

        val gates = session.validationGates
        gates.add { assertThatGoldIsNotNegative(it) }
        playerListOf(loaded).setInteger("Gold", 500)
        assertTrue(session.runValidation().isEmpty())

        playerListOf(loaded).setInteger("Gold", -1)
        val problems = session.runValidation()
        assertEquals(listOf("Gold must not be negative"), problems)

        gates.clear()
        assertTrue(session.runValidation().isEmpty())
    }

    @Test
    fun saveAsWritesTheModifiedStateToACopyAndLeavesTheOriginalUntouched(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val originalDigests = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        playerListOf(loaded).setInteger("Gold", 500)
        loaded.session.addJournalEntry("quest", "draft_test")

        val target = tempDir.resolve("000777 - Copy Target.TheWitcherSave").toFile()
        assertFalse(target.exists())
        SaveSeamSupport.saveAs(loaded, target)

        val afterOriginal = SaveDatabase(environment, save)
        afterOriginal.load()
        assertEquals(originalDigests, SaveSeamSupport.entryDigests(afterOriginal),
            "the original file must be byte-identical after a save-as")

        assertTrue(File(target.getParentFile(), target.getName() + ".bak").isFile,
            "the copy gets the first-write backup")

        val reloaded = SaveSeamSupport.load(environment, target, tempDir)
        assertEquals("000777 - Copy Target", reloaded.saveDatabase!!.getName())
        assertEquals(500, reloaded.player!!.getInteger("Gold"), "the gold edit must persist in the copy")
        val reloadedJournal = reloaded.session.getJournalData()!!.entries
        assertTrue(reloadedJournal.any { it.entryId == "draft_test" }, "the journal edit must persist in the copy")

        for (entry in reloaded.saveDatabase!!.entries) {
            assertTrue(entry.getResourcePath().startsWith("000777 - Copy Target\\"),
                "entries must be repathed to the new save name, got " + entry.getResourcePath())
        }

        val targetDigests = SaveSeamSupport.entryDigests(reloaded.saveDatabase!!)
        val oldId = SaveSeamSupport.EXPECTED_SAVE_NAME.substring(0, 6)
        val newId = "000777"
        fun targetKey(name: String): String = when (name) {
            "save_$oldId.smm" -> "save_$newId.smm"
            "$oldId.tga" -> "$newId.tga"
            else -> name
        }
        val beforeNormalized = originalDigests.entries.associate { targetKey(it.key) to it.value }
        assertEquals(beforeNormalized.keys, targetDigests.keys)
        val rewritten = SaveSeamSupport.changedEntries(beforeNormalized, targetDigests)
        val allowedToChange = setOf(reloaded.modName!!, "player.utc", reloaded.smmName!!,
            reloaded.questDBName!! + ".qdb")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries beyond the edited set changed: " + rewritten)
        for (name in beforeNormalized.keys) {
            if (!rewritten.contains(name)) {
                assertEquals(beforeNormalized[name], targetDigests[name],
                    "untouched entry " + name + " must be byte-identical")
            }
        }
    }

    private fun playerListOf(loaded: SaveSeamSupport.Loaded): DBList {
        val topList = loaded.ifoDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        val playerList = topList.getElement("Mod_PlayerList")!!.getValue() as DBList
        return playerList.getElement(0).getValue() as DBList
    }

    private fun assertThatGoldIsNotNegative(session: GameSession): List<String> {
        val topList = session.database!!.getTopLevelStruct()!!.getValue() as DBList
        val playerList = topList.getElement("Mod_PlayerList")!!.getValue() as DBList
        val player = playerList.getElement(0).getValue() as DBList
        return if (player.getInteger("Gold") < 0) listOf("Gold must not be negative") else emptyList()
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
