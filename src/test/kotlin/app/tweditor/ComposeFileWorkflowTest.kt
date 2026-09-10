package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ComposeFileWorkflowTest {
    @Test
    fun navigationDestinationsKeepTheAcceptedOrder() {
        assertEquals(
            listOf("nav.hero", "nav.journal", "nav.inventory"),
            ComposeDestination.entries.map { it.labelKey }
        )
        val messages = EditorMessages.english()
        assertEquals(listOf("Hero", "Journal", "Inventory"), ComposeDestination.entries.map { messages.get(it.labelKey) })
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    fun openSaveAndReloadUseTheRealFileWorkflow(@TempDir tempDir: Path) {
        val environment = SaveSeamSupport.createEnvironment()
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val workflow = ComposeFileWorkflow(environment, GameSession(tempDir.toFile()))

        val opened = CountDownLatch(1)
        workflow.open(save) { opened.countDown() }
        assertTrue(opened.await(60, TimeUnit.SECONDS), "open did not complete")
        assertEquals(SaveSeamSupport.EXPECTED_SAVE_NAME, workflow.snapshot().fileName)
        assertTrue(workflow.snapshot().errorMessage.isEmpty(), workflow.snapshot().errorMessage.toString())

        val before = SaveDatabase(environment, save).also { it.load() }
        val beforeDigests = SaveSeamSupport.entryDigests(before)
        workflow.session.addJournalEntry("quest", "compose_workflow_test")
        assertTrue(workflow.snapshot().dataModified)

        val saved = CountDownLatch(1)
        workflow.save { saved.countDown() }
        assertTrue(saved.await(60, TimeUnit.SECONDS), "save did not complete")
        assertTrue(workflow.snapshot().errorMessage.isEmpty(), workflow.snapshot().errorMessage.toString())

        val after = SaveDatabase(environment, save).also { it.load() }
        SaveSeamSupport.assertUntouchedEntries(
            beforeDigests,
            SaveSeamSupport.entryDigests(after),
            setOf(workflow.session.getModName()!!, "player.utc", workflow.session.getSmmName()!!,
                workflow.session.getQuestDBName()!! + ".qdb")
        )

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertTrue(
            reloaded.session.getJournalData()!!.entries.any { it.entryId == "compose_workflow_test" },
            "the Compose file workflow must persist journal edits through save/reload"
        )
    }
}
