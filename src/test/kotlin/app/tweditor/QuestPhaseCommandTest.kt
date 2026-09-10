package app.tweditor

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(300)
class QuestPhaseCommandTest {
    @Test
    fun phaseTargetsComeOnlyFromExistingRootPhaseRows(@TempDir tempDir: Path) {
        val loaded = loaded(tempDir)
        val targets = QuestPhaseAccess.targets(loaded.session)
        assertTrue(targets.isNotEmpty())
        assertTrue(targets.all { it.phaseId > 0 })
        assertTrue(targets.any { it.questResourceName == "q2001_safeharb" && it.phaseId == 1 })

        val controller = EditorCommandController(loaded.session)
        val locked = controller.dispatch(SetQuestPhaseCommand("q2001_safeharb", 1))
        assertTrue(locked is EditorCommandResult.Rejected)
        assertEquals("journal.command.advancedOffQuest", (locked as EditorCommandResult.Rejected).problems.single().key)

        val arbitrary = controller.dispatch(
            SetQuestPhaseCommand("q2001_safeharb", 999, advancedJournalEditing = true)
        )
        assertTrue(arbitrary is EditorCommandResult.Rejected)
        assertEquals(
            "quest.command.arbitraryPhaseBlocked",
            (arbitrary as EditorCommandResult.Rejected).problems.single().key
        )
    }

    @Test
    fun rawPhaseOverrideUndoApplyRevertSaveAndReloadOnlyChangeTheQuestEntry(@TempDir tempDir: Path) {
        val loaded = loaded(tempDir)
        val saveFile = requireNotNull(loaded.saveDatabase!!.getFile())
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), saveFile)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val target = QuestPhaseAccess.targets(loaded.session)
            .first { it.questResourceName == "q2001_safeharb" && it.phaseId == 1 }
        val quest = requireNotNull(QuestPhaseAccess.quest(loaded.session, target.questResourceName))
        val oldPhase = requireNotNull(QuestPhaseAccess.shape(quest)).currentPhase
        val controller = EditorCommandController(loaded.session)

        loaded.session.createBaseline()
        val applied = controller.dispatch(
            SetQuestPhaseCommand(target.questResourceName, target.phaseId, advancedJournalEditing = true)
        )
        assertTrue(applied is EditorCommandResult.Applied)
        val appliedChange = (applied as EditorCommandResult.Applied).change
        assertEquals("quest.pending.override", appliedChange.description.key)
        assertEquals(
            listOf<Any>(target.questResourceName, oldPhase, 1, target.phaseName),
            appliedChange.description.args
        )
        assertEquals(EvidenceLevel.DELIBERATELY_DANGEROUS, appliedChange.evidence)
        assertEquals(1, QuestPhaseAccess.shape(quest)!!.currentPhase)
        assertTrue(controller.undo().completed)
        assertEquals(oldPhase, QuestPhaseAccess.shape(quest)!!.currentPhase)

        assertTrue(
            controller.dispatch(
                SetQuestPhaseCommand(target.questResourceName, target.phaseId, advancedJournalEditing = true)
            ) is EditorCommandResult.Applied
        )
        assertTrue(controller.revert().completed)
        assertEquals(oldPhase, QuestPhaseAccess.shape(quest)!!.currentPhase)

        assertTrue(
            controller.dispatch(
                SetQuestPhaseCommand(target.questResourceName, target.phaseId, advancedJournalEditing = true)
            ) is EditorCommandResult.Applied
        )
        assertTrue(controller.apply().completed)
        SaveSeamSupport.save(loaded)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), saveFile, tempDir)
        val reloadedQuest = requireNotNull(QuestPhaseAccess.quest(reloaded.session, target.questResourceName))
        assertEquals(1, QuestPhaseAccess.shape(reloadedQuest)!!.currentPhase)
        val questEntry = before.keys.single { it.endsWith(target.questResourceName + ".qst", ignoreCase = true) }
        SaveSeamSupport.assertUntouchedEntries(
            before,
            SaveSeamSupport.entryDigests(reloaded.saveDatabase!!),
            setOf(questEntry, reloaded.modName!!, "player.utc", reloaded.smmName!!)
        )
    }

    @Test
    fun rawPhaseOverridePersistsThroughSaveAs(@TempDir tempDir: Path) {
        val loaded = loaded(tempDir)
        val target = QuestPhaseAccess.targets(loaded.session)
            .first { it.questResourceName == "q2001_safeharb" && it.phaseId == 2 }
        val controller = EditorCommandController(loaded.session)
        assertTrue(
            controller.dispatch(
                SetQuestPhaseCommand(target.questResourceName, target.phaseId, advancedJournalEditing = true)
            ) is EditorCommandResult.Applied
        )

        val copyDir = Files.createDirectory(tempDir.resolve("save-as"))
        val copy = copyDir.resolve("000099 - Quest phase copy.TheWitcherSave").toFile()
        SaveSeamSupport.saveAs(loaded, copy)
        val reloaded = SaveSeamSupport.load(
            SaveSeamSupport.createEnvironment(),
            copy,
            copyDir
        )
        val quest = requireNotNull(QuestPhaseAccess.quest(reloaded.session, target.questResourceName))
        assertEquals(2, QuestPhaseAccess.shape(quest)!!.currentPhase)
    }

    private fun loaded(tempDir: Path): SaveSeamSupport.Loaded {
        val environment = SaveSeamSupport.createEnvironment()
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        return SaveSeamSupport.load(environment, save, tempDir)
    }
}
