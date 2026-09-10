package app.tweditor

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(60)
class JournalViewModelTest {
    @Test
    fun journalStateKeepsGameOrderAndLeavesUnknownCategoriesUnresolved(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            SaveSeamSupport.createEnvironment(),
            SaveSeamSupport.copyFixtureTo(tempDir),
            tempDir
        )

        val state = JournalViewState.from(loaded.session)
        assertEquals(
            listOf("Quests", "Characters", "Locations", "Monsters", "Formula", "Ingredients", "Glossary", "Tutorials"),
            JournalSection.entries.map { it.displayName }
        )
        assertEquals("Prolog", state.storyPhase)
        assertTrue(state.quests.isNotEmpty())
        assertTrue(state.entries(JournalSection.CHARACTERS).any { it.category == "character" })
        assertTrue(state.entries(JournalSection.LOCATIONS).any { it.category == "place" })
        assertEquals(3, state.entries(JournalSection.TUTORIALS).size)
        assertTrue(state.unresolvedEntries.any { it.category == "unique" })
        assertTrue(state.unresolvedEntries.any { it.category == "hidden" })
        assertFalse(state.entries(JournalSection.MONSTERS).any { it.category == "unique" })
    }

    @Test
    fun advancedJournalEditingIsSessionScopedAndResetsWhenASecondSaveOpens(@TempDir tempDir: Path) {
        val environment = SaveSeamSupport.createEnvironment()
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val workflow = ComposeFileWorkflow(environment, loaded.session)

        workflow.setAdvancedJournalEditing(true)
        assertTrue(workflow.snapshot().advancedJournalEditing)

        val completed = CountDownLatch(1)
        workflow.open(save) { completed.countDown() }
        assertTrue(completed.await(30, TimeUnit.SECONDS), "the second Save must finish opening")
        assertFalse(workflow.snapshot().advancedJournalEditing)
        workflow.close()
    }
}
