package app.tweditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class InventoryComposeSemanticsTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun narrowInventoryNavigationExposesContainersWithoutDetailsFooter(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.STORAGE),
            tempDir
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        setContent {
            Box(Modifier.fillMaxSize()) {
                InventoryWorkspace(environment, workflow, workflow.snapshot())
            }
        }

        onAllNodesWithText("Storage", substring = false).assertCountEquals(2)
        val firstStorageItem = InventoryViewState.from(workflow.session).storage.first()
        onAllNodesWithText(firstStorageItem.name, substring = false).onFirst().performClick()
        onAllNodesWithText("Details", substring = false).assertCountEquals(0)
        onNodeWithText("Search", substring = false).performClick()
        onNodeWithText("Search Storage", substring = false).assertIsDisplayed()
        onNodeWithText("Name or resource", substring = false).assertIsDisplayed()
        onNodeWithText("Close", substring = false).performClick()

        onNodeWithText("Equipment", substring = false).performClick()
        onNodeWithText("Steel sword", substring = false).assertIsDisplayed()

        onNodeWithText("Carried items", substring = false).performClick()
        onNodeWithText("Satchel", substring = false).assertIsDisplayed()
        onAllNodesWithText("Quest Items", substring = false).onFirst().assertIsDisplayed()
        onNodeWithContentDescription("Quest Items icon").assertIsDisplayed()
        onNodeWithContentDescription("Satchel icon").assertIsDisplayed()
        // Larger in-game-proportioned cells mean the alchemy container sits
        // below the fold; bring it into view like a user scrolling would.
        onAllNodes(hasContentDescription("Narrow inventory page")).onFirst()
            .performScrollToNode(hasText("Alchemy"))
        onNodeWithText("Alchemy", substring = false).assertIsDisplayed()
        onNodeWithContentDescription("Alchemy icon").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun carriedContainersExposeOneSharedAddPickerEach(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        setContent {
            Box(Modifier.fillMaxSize()) {
                InventoryWorkspace(environment, workflow, workflow.snapshot())
            }
        }

        onNodeWithText("Carried items", substring = false).performClick()
        onAllNodesWithText("Add", substring = false).assertCountEquals(3)
        // Quest items sit on top now, so the first container's picker is theirs.
        onAllNodesWithText("Add", substring = false).onFirst().performClick()
        onNodeWithText("Add to Quest Items", substring = false).assertIsDisplayed()
        onNodeWithText("Search item templates", substring = false).assertIsDisplayed()
        onNodeWithText("Cancel", substring = false).performClick()
    }
}


