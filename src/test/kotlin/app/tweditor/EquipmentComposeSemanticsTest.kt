package app.tweditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class EquipmentComposeSemanticsTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun equipmentDoubleClickRequestsTheItemWindow(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val destination = InventoryViewState.from(loaded.session).equipment.first { it.item?.name == "Axe" }
        var openedId: String? = null
        setContent {
            EquipmentDestinationCard(
                destination = destination,
                selected = false,
                onSelect = {},
                onRemove = {},
                onDrop = { _, _ -> false },
                draggingId = null,
                draggingItem = null,
                hoveredSlot = null,
                environment = environment,
                onOpen = { openedId = it.id },
                compact = false,
                modifier = Modifier.size(100.dp),
                onDragState = { _, _ -> }
            )
        }

        onNodeWithText("Axe", substring = false).assertIsDisplayed()
        onNodeWithContentDescription("Remove Axe", substring = true).assertIsDisplayed()
        onNodeWithText("Axe", substring = false).performMouseInput { doubleClick() }
        waitForIdle()
        assertEquals(destination.item!!.id, openedId)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun selectedItemCanShowItsDescriptionAndEffects(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val item = InventoryViewState.from(loaded.session).equipment.flatMap { it.items }.first()
        val fields = requireNotNull(EquipmentAccess.record(loaded.session, item.id)).fields
        fields.setString("DescIdentified", "A reliable tool for close combat.")
        fields.setString("ExtraDesc", "Its weight makes every strike count.")
        ItemEdit(environment, fields).setWeaponAbilities(
            listOf(WeaponAbility("steady_grip", 1)),
            listOf(WeaponAbility("heavy_impact", 2))
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        val initial = requireNotNull(readEquipmentEditValues(loaded.session, item.id))
        val details = requireNotNull(readItemDetails(environment, loaded.session, item.id))
        setContent {
            Box(Modifier.requiredWidth(760.dp).requiredHeight(768.dp)) {
                ItemWindowBody(environment, workflow, item.id, item, true, initial, details, {})
            }
        }

        onNodeWithContentDescription("Item details and editing window").assertIsDisplayed()
        onNodeWithText("A reliable tool for close combat.", substring = false).assertIsDisplayed()
        onNodeWithText("Its weight makes every strike count.", substring = false).assertIsDisplayed()
        onNodeWithText("Wielder", substring = false).assertIsDisplayed()
        onAllNodesWithText("steady_grip", substring = true).onFirst().assertIsDisplayed()
        onNodeWithText("On hit", substring = false).assertIsDisplayed()
        onAllNodesWithText("heavy_impact ×2", substring = true).onFirst().assertIsDisplayed()
        onNodeWithText("ModelPart1", substring = false).assertIsDisplayed()
        onNodeWithText("CustomCost", substring = false).assertIsDisplayed()
        if (System.getProperty("tweditor.screenshots") == "true") {
            val bitmap = onNodeWithContentDescription("Item details and editing window").captureToImage()
            val pixels = bitmap.toPixelMap()
            val rendered = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
            rendered.setRGB(0, 0, pixels.width, pixels.height, pixels.buffer, pixels.bufferOffset, pixels.stride)
            val output = Path.of("build", "screenshots", "item-window.png").toAbsolutePath()
            Files.createDirectories(output.parent)
            ImageIO.write(rendered, "png", output.toFile())
        }
        onNodeWithText("Close", substring = false).performClick()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun secondOccupiedSlotClickOpensTheSlotAwarePickerWithoutAGlobalAddAction(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides readableDensity(baseDensity)) {
                Box(Modifier.requiredWidth(900.dp).requiredHeight(900.dp)) {
                    InventoryWorkspace(environment, workflow, workflow.snapshot())
                }
            }
        }

        onNodeWithText("Equipment", substring = false).performClick()
        onAllNodesWithText("Add to Equipment", substring = false).assertCountEquals(0)
        onNodeWithText("Axe", substring = false).performClick()
        mainClock.advanceTimeBy(600)
        waitForIdle()
        onAllNodesWithText("Axe", substring = false)[0].performClick()
        mainClock.advanceTimeBy(600)
        waitForIdle()
        onNodeWithText("Search item templates", substring = false).assertIsDisplayed()
        onNodeWithText("Choose for", substring = true).assertIsDisplayed()
        onNodeWithText("Cancel", substring = false).performClick()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun removalRefreshesTheEquipmentCellAfterEveryClick(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        val destination = InventoryViewState.from(loaded.session).equipment.first { it.items.size > 1 }
        val originalCount = destination.items.size
        setContent {
            var uiState by remember { mutableStateOf(workflow.snapshot()) }
            DisposableEffect(workflow) {
                val subscription = workflow.addListener { uiState = workflow.snapshot() }
                onDispose { subscription.close() }
            }
            Box(Modifier.requiredWidth(900.dp).requiredHeight(900.dp)) {
                InventoryWorkspace(environment, workflow, uiState)
            }
        }

        onNodeWithText("Equipment", substring = false).performClick()
        onNodeWithContentDescription("; $originalCount items", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Remove ${destination.item!!.name}", substring = true).performClick()
        if (originalCount - 1 > 1) {
            onNodeWithContentDescription("; ${originalCount - 1} items", substring = true).assertIsDisplayed()
        }
        assertEquals(1, workflow.snapshot().pendingChanges.size)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun compactSlotKeepsItsRemoveControlInsideTheCell(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        val equipment = requireNotNull(EquipmentAccess.equipmentList(loaded.session, create = false))
        val compactFields = equipment.first {
            val fields = it.getValue() as DBList
            fields.getInteger("BaseItem") != 36
        }.getValue() as DBList
        compactFields.setInteger("WeaponSlot", WeaponSlots.ELIXIR_1)
        val compactDestination = InventoryViewState.from(loaded.session).equipment
            .first { it.slot == WeaponSlots.ELIXIR_1 }
        setContent {
            Box(Modifier.requiredWidth(900.dp).requiredHeight(900.dp)) {
                InventoryWorkspace(environment, workflow, workflow.snapshot())
            }
        }

        onNodeWithText("Equipment", substring = false).performClick()
        val slot = onNodeWithContentDescription("Equipment slot ${compactDestination.name}", substring = true)
            .fetchSemanticsNode().boundsInRoot
        val remove = onNodeWithContentDescription("Remove ${compactDestination.item!!.name} from ${compactDestination.name}")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(slot.width >= 48f && slot.height >= 56f)
        assertTrue(remove.left >= slot.left && remove.top >= slot.top)
        assertTrue(remove.right <= slot.right && remove.bottom <= slot.bottom)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun captureEquipmentPaperdollForVisualReview(@TempDir tempDir: Path) = runComposeUiTest {
        assumeTrue(System.getProperty("tweditor.screenshots") == "true")
        val environment = SaveSeamSupport.createEnvironment()
        val mainKey = File("C:\\Games\\The Witcher Enhanced Edition\\Data\\main.key")
        if (mainKey.isFile) {
            environment.resourceFiles = Main.resourceFilesFrom(KeyDatabase(environment, mainKey.path))
        }
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        val equipment = requireNotNull(EquipmentAccess.equipmentList(loaded.session, create = false))
        val sample = (equipment.first { (it.getValue() as DBList).getInteger("BaseItem") != 36 }.getValue() as DBList).clone()
        equipment.elementList.clear()
        InventoryViewState.ACCEPTED_EQUIPMENT_SLOTS.forEach { (slot, name) ->
            val fields = sample.clone().also {
                it.setInteger("WeaponSlot", slot)
                it.setString("LocalizedName", name)
                it.setString("TemplateResRef", "visual_${slot}")
            }
            equipment.addElement(DBElement(DBElement.STRUCT, 0, "", fields))
        }
        val visualItems = InventoryViewState.from(loaded.session).equipment.mapNotNull { it.item }
        val resrefs = visualItems.mapNotNull {
            environment.icons.itemIconResref(it.baseItem, it.modelPart, it.templateResRef)
        }
        environment.icons.prime(resrefs)
        val decodeDeadline = System.currentTimeMillis() + 10_000
        while (visualItems.any {
                environment.icons.itemViewIconFullResolution(it.baseItem, it.modelPart, it.templateResRef) == null
            } && System.currentTimeMillis() < decodeDeadline
        ) {
            Thread.sleep(25)
        }
        setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides readableDensity(baseDensity)) {
                Box(Modifier.requiredWidth(900.dp).requiredHeight(900.dp)) {
                    InventoryWorkspace(environment, workflow, workflow.snapshot())
                }
            }
        }

        onNodeWithText("Equipment", substring = false).performClick()
        val bitmap = onNodeWithContentDescription("Equipment paperdoll").captureToImage()
        val pixels = bitmap.toPixelMap()
        val rendered = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        rendered.setRGB(0, 0, pixels.width, pixels.height, pixels.buffer, pixels.bufferOffset, pixels.stride)
        val targetWidth = 800
        val targetHeight = rendered.height * targetWidth / rendered.width
        val preview = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = preview.createGraphics()
        try {
            graphics.drawImage(rendered, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        val output = Path.of("build", "screenshots", "equipment-font120-preview.jpg").toAbsolutePath()
        Files.createDirectories(output.parent)
        ImageIO.write(preview, "jpg", output.toFile())
    }
}
