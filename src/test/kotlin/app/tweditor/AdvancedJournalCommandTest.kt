package app.tweditor

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(300)
class AdvancedJournalCommandTest {
    private val representativeEntries = linkedMapOf(
        "character" to "vesemir/info",
        "place" to "kaer/basic",
        "hydragenum" to "hydragenum1",
        "info" to "wizards/info",
        "tutorial" to "tutorial01"
    )

    @Test
    fun advancedJournalEditingRoundTripsEverySupportedCategory(@TempDir tempDir: Path) {
        for ((category, sourceId) in representativeEntries) {
            val workDir = Files.createDirectory(tempDir.resolve(category))
            val environment = SaveSeamSupport.createEnvironment()
            val save = SaveSeamSupport.copyFixtureTo(workDir, SaveSeamSupport.Fixture.STORAGE)
            val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
            pristine.load()
            val before = SaveSeamSupport.entryDigests(pristine)
            val loaded = SaveSeamSupport.load(environment, save, workDir)
            val controller = EditorCommandController(loaded.session)
            val previewId = "agent/preview/$category"
            val finalId = "agent/edited/$category"

            loaded.session.createBaseline()
            assertTrue(
                controller.dispatch(
                    AddAdvancedJournalEntryCommand(
                        category = category,
                        entryId = previewId,
                        timeOfDay = 1800000,
                        advancedJournalEditing = true
                    )
                ) is EditorCommandResult.Applied
            )
            assertTrue(controller.revert().completed)
            assertFalse(hasJournalEntry(loaded.session, category, previewId))

            val added = controller.dispatch(
                AddAdvancedJournalEntryCommand(
                    category = category,
                    entryId = sourceId + "/agent",
                    timeOfDay = 1800000,
                    advancedJournalEditing = true
                )
            )
            assertTrue(added is EditorCommandResult.Applied)
            assertEquals(
                "journal.pending.add",
                (added as EditorCommandResult.Applied).change.description.key
            )
            assertEquals(
                listOf<Any>(
                    LocalizedText(AdvancedJournalCategories.messageKey(category)!!),
                    "$category:$sourceId/agent"
                ),
                (added as EditorCommandResult.Applied).change.description.args
            )
            assertEquals(EvidenceLevel.DELIBERATELY_DANGEROUS, (added as EditorCommandResult.Applied).change.evidence)

            val edited = controller.dispatch(
                EditAdvancedJournalEntryCommand(
                    category = category,
                    currentEntryId = "$sourceId/agent",
                    values = AdvancedJournalEntryValues(finalId, read = true, timeOfDay = 1900000),
                    advancedJournalEditing = true
                )
            )
            assertTrue(edited is EditorCommandResult.Applied)
            assertEquals(
                "journal.pending.edit",
                (edited as EditorCommandResult.Applied).change.description.key
            )
            assertEquals(
                listOf<Any>(
                    LocalizedText(AdvancedJournalCategories.messageKey(category)!!),
                    "$category:$sourceId/agent -> $category:$finalId"
                ),
                (edited as EditorCommandResult.Applied).change.description.args
            )
            assertTrue(controller.undo().completed)
            assertTrue(hasJournalEntry(loaded.session, category, "$sourceId/agent"))

            assertTrue(
                controller.dispatch(
                    EditAdvancedJournalEntryCommand(
                        category = category,
                        currentEntryId = "$sourceId/agent",
                        values = AdvancedJournalEntryValues(finalId, read = true, timeOfDay = 1900000),
                        advancedJournalEditing = true
                    )
                ) is EditorCommandResult.Applied
            )
            assertTrue(controller.apply().completed)
            SaveSeamSupport.save(loaded)

            val afterEdit = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, workDir)
            assertJournalEntry(afterEdit.session, category, finalId, read = true, timeOfDay = 1900000)
            SaveSeamSupport.assertUntouchedEntries(
                before,
                SaveSeamSupport.entryDigests(afterEdit.saveDatabase!!),
                setOf(afterEdit.modName!!, "player.utc", afterEdit.smmName!!, afterEdit.questDBName + ".qdb")
            )

            val removeController = EditorCommandController(afterEdit.session)
            val removed = removeController.dispatch(
                RemoveAdvancedJournalEntryCommand(category, finalId, advancedJournalEditing = true)
            )
            assertTrue(removed is EditorCommandResult.Applied)
            assertEquals(
                "journal.pending.remove",
                (removed as EditorCommandResult.Applied).change.description.key
            )
            assertEquals(
                listOf<Any>(
                    LocalizedText(AdvancedJournalCategories.messageKey(category)!!),
                    "$category:$finalId"
                ),
                (removed as EditorCommandResult.Applied).change.description.args
            )
            assertEquals(EvidenceLevel.DELIBERATELY_DANGEROUS, (removed as EditorCommandResult.Applied).change.evidence)
            assertTrue(removeController.undo().completed)
            assertTrue(hasJournalEntry(afterEdit.session, category, finalId))
            assertTrue(
                removeController.dispatch(
                    RemoveAdvancedJournalEntryCommand(category, finalId, advancedJournalEditing = true)
                ) is EditorCommandResult.Applied
            )
            assertTrue(removeController.apply().completed)
            SaveSeamSupport.save(afterEdit)

            val afterRemove = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, workDir)
            assertFalse(hasJournalEntry(afterRemove.session, category, finalId))
            SaveSeamSupport.assertUntouchedEntries(
                before,
                SaveSeamSupport.entryDigests(afterRemove.saveDatabase!!),
                setOf(afterRemove.modName!!, "player.utc", afterRemove.smmName!!, afterRemove.questDBName + ".qdb")
            )
        }
    }

    @Test
    fun advancedModeAndUnfamiliarShapesRemainSafe(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            SaveSeamSupport.createEnvironment(),
            SaveSeamSupport.copyFixtureTo(tempDir),
            tempDir
        )
        val controller = EditorCommandController(loaded.session)
        val locked = controller.dispatch(
            AddAdvancedJournalEntryCommand("character", "blocked/id", advancedJournalEditing = false)
        )
        assertTrue(locked is EditorCommandResult.Rejected)
        assertEquals("journal.command.advancedOff", (locked as EditorCommandResult.Rejected).problems.single().key)

        val top = loaded.session.getQuestDatabase()!!.getTopLevelStruct()!!.getValue() as DBList
        val journal = top.getElement("Journal")!!.getValue() as DBList
        val character = journal.first { (it.getValue() as DBList).getString("Entry").startsWith("Character:") }
        val fields = character.getValue() as DBList
        fields.addElement(DBElement(DBElement.STRING, 0, "UnknownField", "preserve me"))

        val view = JournalViewState.from(loaded.session).entries(JournalSection.CHARACTERS)
            .single { it.entryId == "vesemir/info" }
        assertFalse(view.advancedEditable)
        assertEquals("journal.edit.unfamiliarFields", view.advancedEditReason!!.key)

        val rejected = controller.dispatch(
            RemoveAdvancedJournalEntryCommand("character", "vesemir/info", advancedJournalEditing = true)
        )
        assertTrue(rejected is EditorCommandResult.Rejected)
        assertEquals(
            "journal.edit.unfamiliarFields",
            (rejected as EditorCommandResult.Rejected).problems.single().key
        )
        assertEquals("preserve me", fields.getString("UnknownField"))
    }

    @Test
    fun duplicateIdentityIsRejectedBeforeAnyRawVariantIsDeleted(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            SaveSeamSupport.createEnvironment(),
            SaveSeamSupport.copyFixtureTo(tempDir),
            tempDir
        )
        val top = loaded.session.getQuestDatabase()!!.getTopLevelStruct()!!.getValue() as DBList
        val journal = top.getElement("Journal")!!.getValue() as DBList
        val original = journal.first { (it.getValue() as DBList).getString("Entry") == "Character:vesemir/info" }
        journal.addElement(original.clone())

        val result = EditorCommandController(loaded.session).dispatch(
            RemoveAdvancedJournalEntryCommand("character", "vesemir/info", advancedJournalEditing = true)
        )
        assertTrue(result is EditorCommandResult.Rejected)
        assertEquals(
            "journal.edit.multipleVariantsRemove",
            (result as EditorCommandResult.Rejected).problems.single().key
        )
        assertEquals(2, AdvancedJournalAccess.records(loaded.session).count { it.entryId == "vesemir/info" })
    }

    private fun hasJournalEntry(session: GameSession, category: String, entryId: String): Boolean =
        session.getJournalData()?.entries.orEmpty().any {
            it.category.equals(category, ignoreCase = true) && it.entryId.equals(entryId, ignoreCase = true)
        }

    private fun assertJournalEntry(session: GameSession, category: String, entryId: String, read: Boolean, timeOfDay: Int) {
        val record = AdvancedJournalAccess.records(session).single {
            it.category == category && it.entryId.equals(entryId, ignoreCase = true)
        }
        assertTrue(record.supportedShape)
        assertEquals(read, record.fields.getInteger("EntryRead") == 1)
        assertEquals(timeOfDay, record.fields.getInteger("EntryTOD"))
        assertEquals(0, record.fields.getInteger("EntryCD"))
    }
}
