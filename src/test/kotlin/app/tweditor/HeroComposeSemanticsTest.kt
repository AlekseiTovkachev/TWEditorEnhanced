package app.tweditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class HeroComposeSemanticsTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun summaryValuesAreEditableAndTalentLevelsRunLeftToRight(@TempDir tempDir: Path) = runComposeUiTest {
        val environment = SaveSeamSupport.createEnvironment()
        val mainKey = File("C:\\Games\\The Witcher Enhanced Edition\\Data\\main.key")
        if (mainKey.isFile) {
            environment.resourceFiles = Main.resourceFilesFrom(KeyDatabase(environment, mainKey.path))
            environment.icons.primeTalentTalents(HeroAbilityLabels.attributes)
            val resrefs = HeroAbilityLabels.attributes.mapNotNull(AbilityResrefs::resref)
            val deadline = System.currentTimeMillis() + 10_000
            while (resrefs.any { environment.icons.imageByResrefBaseArchive(it) == null } && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
        }
        val loaded = SaveSeamSupport.load(environment, SaveSeamSupport.copyFixtureTo(tempDir), tempDir)
        val workflow = ComposeFileWorkflow(environment, loaded.session)
        setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides readableDensity(baseDensity)) {
                Box(Modifier.requiredWidth(1024.dp).requiredHeight(768.dp)) {
                    HeroWorkspace(environment, workflow, workflow.snapshot())
                }
            }
        }

        onNodeWithContentDescription("Hero value Level").assertIsDisplayed()
        onNodeWithContentDescription("Hero value Endurance").assertIsDisplayed()
        onNodeWithContentDescription("Hero value Toxicity").assertIsDisplayed()
        onAllNodesWithContentDescription("Edit hero summary").assertCountEquals(0)
        if (System.getProperty("tweditor.screenshots") == "true") {
            writePreview(onRoot().captureToImage(), "hero-summary-inline-final.jpg")
        }
        val editedLevel = HeroData.summary(loaded.session).level + 1
        onNodeWithContentDescription("Hero value Level").performTextReplacement(editedLevel.toString())
        onNodeWithText("Apply values").assertIsEnabled().performClick()
        assertTrue(HeroData.summary(loaded.session).level == editedLevel)

        onNodeWithText("Attributes").performClick()
        val levelOne = onNodeWithContentDescription("Toggle Strength1").fetchSemanticsNode().boundsInRoot
        val levelTwo = onNodeWithContentDescription("Toggle Strength2").fetchSemanticsNode().boundsInRoot
        val levelFive = onNodeWithContentDescription("Toggle Strength5").fetchSemanticsNode().boundsInRoot
        assertTrue(levelOne.left < levelTwo.left && levelTwo.left < levelFive.left)
        onNodeWithContentDescription("Toggle Strength2 Upgrade3").assertIsDisplayed()

        if (System.getProperty("tweditor.screenshots") == "true") {
            writePreview(onRoot().captureToImage(), "hero-game-tiles-final.jpg")
        }
    }

    private fun writePreview(bitmap: ImageBitmap, fileName: String) {
        val pixels = bitmap.toPixelMap()
        val rendered = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        rendered.setRGB(0, 0, pixels.width, pixels.height, pixels.buffer, pixels.bufferOffset, pixels.stride)
        val targetWidth = 800
        val targetHeight = rendered.height * targetWidth / rendered.width
        val preview = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = preview.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(rendered, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        val output = Path.of("build", "screenshots", fileName).toAbsolutePath()
        Files.createDirectories(output.parent)
        ImageIO.write(preview, "jpg", output.toFile())
    }
}
