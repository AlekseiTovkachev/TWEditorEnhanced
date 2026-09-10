package app.tweditor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class ComposeNavigationSemanticsTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun primaryDestinationsAreReachableBySemanticLabel() = runComposeUiTest {
        setContent {
            ComposeNavigationRail(
                environment = SaveSeamSupport.createEnvironment(),
                active = ComposeDestination.HERO,
                onSelect = {}
            )
        }

        onNode(hasContentDescription("Hero destination")).assertIsDisplayed()
        onNode(hasContentDescription("Journal destination")).assertIsDisplayed().assertIsEnabled()
        onNode(hasContentDescription("Inventory destination")).assertIsDisplayed().assertIsEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun clickingAWorkspaceRailItemDispatchesTheSelectedDestination() = runComposeUiTest {
        var selected by mutableStateOf(ComposeDestination.HERO)
        setContent {
            ComposeNavigationRail(
                environment = SaveSeamSupport.createEnvironment(),
                active = selected,
                onSelect = { selected = it }
            )
        }

        onNode(hasContentDescription("Inventory destination")).performClick()
        assertEquals(ComposeDestination.INVENTORY, selected)
    }
}
