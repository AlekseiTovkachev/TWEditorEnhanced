package app.tweditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

class JournalComposeSemanticsTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun journalShowsAllSectionsAndKeepsAdvancedActionsLockedUntilWarningAccepted(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir),
            tempDir
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        setContent {
            var currentState by remember { mutableStateOf(workflow.snapshot()) }
            DisposableEffect(workflow) {
                val subscription = workflow.addListener { currentState = workflow.snapshot() }
                onDispose { subscription.close() }
            }
            Box(Modifier.fillMaxSize()) {
                JournalWorkspace(environment, workflow, currentState)
            }
        }

        JournalSection.entries.forEach { section ->
            onAllNodesWithText(section.displayName, substring = false).onFirst().assertIsDisplayed()
        }
        fun digest(node: SemanticsNode): String {
            val text = node.config.joinToString(" | ") { entry -> entry.toString().take(60) }
            return "(${node.id}) ${node.positionInRoot} ${node.size} $text"
        }
        val walk = ArrayDeque(listOf(onRoot().fetchSemanticsNode()))
        while (walk.isNotEmpty()) {
            val node = walk.removeFirst()
            println("DEBUG ${digest(node)}")
            node.children.forEach { walk.addLast(it) }
        }
        // Toggle advanced editing first; the actual workspace tabs scroll the
        // list of entries, which moves the pinned header row out of the test
        // viewport without a real user gesture.
        onNode(hasContentDescription("Advanced Journal Editing")).performClick()
        onNodeWithText("Enable Advanced Editing", substring = false).assertIsDisplayed()
        onNodeWithText("Cancel", substring = false).performClick()
        onNodeWithText("Advanced off", substring = false).assertIsDisplayed()

        onAllNodesWithText("Phase override (Advanced)", substring = false).onFirst().assertIsNotEnabled()
        onNodeWithText("Characters", substring = false).performClick()
        onAllNodesWithText("Advanced edit", substring = false).onFirst().assertIsNotEnabled()

        onNode(hasContentDescription("Advanced Journal Editing")).performClick()
        onNodeWithText("Enable Advanced Editing", substring = false).performClick()
        onNodeWithText("Enabled for this open Save", substring = false).assertIsDisplayed()
        onAllNodesWithText("Edit", substring = false).onFirst().assertIsDisplayed()
        onAllNodesWithText("Remove", substring = false).onFirst().assertIsDisplayed()
        onAllNodesWithText("Edit", substring = false).onFirst().assertIsEnabled()
        onAllNodesWithText("Remove", substring = false).onFirst().assertIsEnabled()
        onNodeWithText("Add entry", substring = false).assertIsEnabled()
        onNodeWithText("Quests", substring = false).performClick()
        onAllNodesWithText("Override phase", substring = false).onFirst().assertIsEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun monsterGrantIsAvailableWithoutAdvancedJournalEditing(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val catalog = tempDir.resolve("journal.2da")
        Files.writeString(catalog, "2DA V2.0\n\n\tCategory\tPicture\tEntryId\n0\tbestiary\tje_monster\tghoul/w/1\n")
        environment.resourceFiles["journal.2da"] = catalog.toFile()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir),
            tempDir
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        setContent {
            var currentState by remember { mutableStateOf(workflow.snapshot()) }
            DisposableEffect(workflow) {
                val subscription = workflow.addListener { currentState = workflow.snapshot() }
                onDispose { subscription.close() }
            }
            Box(Modifier.fillMaxSize()) {
                JournalWorkspace(environment, workflow, currentState)
            }
        }

        onAllNodesWithText("Monsters", substring = false).onFirst().performClick()
        onNodeWithText("Grant", substring = false).performClick()
        onNodeWithText("Remove", substring = false).assertIsDisplayed()
        assertTrue(loaded.session.getJournalData()!!.entries.any { it.entryId == "ghoul/s/1" })
        assertFalse(workflow.snapshot().advancedJournalEditing)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun formulaGrantSyncsThePlayerCollectionsAndKeepsRemovalAdvanced(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val catalog = tempDir.resolve("journal.2da")
        Files.writeString(
            catalog,
            "2DA V2.0\n\n\tCategory\tPicture\tEntryId\n" +
                "0\trecipe\titem_icon\tit_potion_ui\n"
        )
        environment.resourceFiles["journal.2da"] = catalog.toFile()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir),
            tempDir
        )
        assertTrue(
            JournalCatalog.formulaTargets(environment, loaded.session)
                .any { it.formulaId == "it_potion_ui" }
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        setContent {
            var currentState by remember { mutableStateOf(workflow.snapshot()) }
            DisposableEffect(workflow) {
                val subscription = workflow.addListener { currentState = workflow.snapshot() }
                onDispose { subscription.close() }
            }
            Box(Modifier.fillMaxSize()) {
                JournalWorkspace(environment, workflow, currentState)
            }
        }

        onAllNodesWithText("Formula", substring = false).onFirst().performClick()
        onAllNodesWithText("Grant", substring = false).onFirst().performClick()
        onAllNodesWithText("Remove (Advanced)", substring = false).onFirst().assertIsNotEnabled()
        assertTrue(
            "journal.formula.potion" in
                workflow.editorCommands.state().pendingChanges.single().description.args.map { (it as? LocalizedText)?.key ?: "" }
        )
        assertTrue(FormulaAccess.state(loaded.session, FormulaKind.POTION, "it_potion_ui").complete)
        assertFalse(workflow.snapshot().advancedJournalEditing)
    }
}


