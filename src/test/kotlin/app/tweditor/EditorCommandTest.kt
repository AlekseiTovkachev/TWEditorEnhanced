package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EditorCommandTest {
    @Test
    fun difficultyStateIsImmutableAndTheCommandRecordsEvidenceAndDescription(@TempDir tempDir: Path) {
        val loaded = loadFixture(tempDir)
        val controller = EditorCommandController(loaded.session)
        val before = controller.state()
        val previous = requireNotNull(before.difficulty)
        val target = nextDifficulty(previous)

        val result = controller.dispatch(SetDifficultyCommand(target))

        assertTrue(result is EditorCommandResult.Applied)
        val applied = result as EditorCommandResult.Applied
        assertEquals("difficulty.pending.set", applied.change.description.key)
        assertEquals(
            listOf<Any>(LocalizedText(previous.messageKey), LocalizedText(target.messageKey)),
            applied.change.description.args
        )
        assertEquals(EvidenceLevel.STRUCTURALLY_VERIFIED, applied.change.evidence)
        assertEquals(target, applied.state.difficulty)
        assertEquals(listOf(applied.change), applied.state.pendingChanges)
        assertTrue(applied.state.canUndo)
        assertTrue(loaded.session.isDataModified())
    }

    @Test
    fun undoRestoresDifficultyAndTheSessionEditFlags(@TempDir tempDir: Path) {
        val loaded = loadFixture(tempDir)
        val controller = EditorCommandController(loaded.session)
        val before = controller.state()

        controller.dispatch(SetDifficultyCommand(nextDifficulty(requireNotNull(before.difficulty))))
        val undone = controller.undo()

        assertTrue(undone.completed)
        assertEquals(before.difficulty, undone.state.difficulty)
        assertTrue(undone.state.pendingChanges.isEmpty())
        assertFalse(undone.state.canUndo)
        assertFalse(loaded.session.isDataModified())
        assertFalse(loaded.session.isDraftDirty())
    }

    @Test
    fun applyCreatesTheRevertPointAndRevertKeepsEarlierAppliedChanges(@TempDir tempDir: Path) {
        val loaded = loadFixture(tempDir)
        val controller = EditorCommandController(loaded.session)
        val first = nextDifficulty(requireNotNull(controller.state().difficulty))
        val second = nextDifficulty(first)

        controller.dispatch(SetDifficultyCommand(first))
        val applied = controller.apply()
        assertTrue(applied.completed)
        assertFalse(loaded.session.isDraftDirty())
        assertTrue(loaded.session.isDataModified())

        controller.dispatch(SetDifficultyCommand(second))
        val reverted = controller.revert()

        assertTrue(reverted.completed)
        assertEquals(first, reverted.state.difficulty)
        assertEquals(1, reverted.state.pendingChanges.size)
        assertFalse(loaded.session.isDraftDirty())
        assertTrue(loaded.session.isDataModified(), "reverting a post-Apply draft must keep earlier applied work unsaved")
    }

    @Test
    fun sameDifficultyIsRejectedWithoutMutatingTheSession(@TempDir tempDir: Path) {
        val loaded = loadFixture(tempDir)
        val controller = EditorCommandController(loaded.session)
        val current = requireNotNull(controller.state().difficulty)

        val result = controller.dispatch(SetDifficultyCommand(current))

        assertTrue(result is EditorCommandResult.Rejected)
        val rejected = result as EditorCommandResult.Rejected
        assertEquals(
            listOf(LocalizedText("difficulty.command.alreadySet", LocalizedText(current.messageKey))),
            rejected.problems
        )
        assertFalse(loaded.session.isDataModified())
        assertTrue(rejected.state.pendingChanges.isEmpty())
    }

    @Test
    fun difficultyCommandPersistsThroughSaveReloadAndProtectsUntouchedEntries(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        loaded.session.createBaseline()
        val controller = EditorCommandController(loaded.session)
        val target = nextDifficulty(requireNotNull(controller.state().difficulty))
        controller.dispatch(SetDifficultyCommand(target))
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        val allowed = setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        SaveSeamSupport.assertUntouchedEntries(before, SaveSeamSupport.entryDigests(repacked), allowed)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        assertEquals(target, DifficultyAccess.readOrNull(reloaded.session))
    }

    private fun loadFixture(tempDir: Path): SaveSeamSupport.Loaded {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        loaded.session.createBaseline()
        return loaded
    }

    private fun nextDifficulty(current: Difficulty): Difficulty = when (current) {
        Difficulty.EASY -> Difficulty.MEDIUM
        Difficulty.MEDIUM -> Difficulty.HARD
        Difficulty.HARD -> Difficulty.EASY
    }
}
