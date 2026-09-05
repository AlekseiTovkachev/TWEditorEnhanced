package app.tweditor

import java.awt.Container
import java.awt.GraphicsDevice
import java.awt.Rectangle
import java.awt.Robot
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@Timeout(60)
class SavePickerDialogTest {
    @Test
    fun listingShowsNameLevelAndScreenshotForEachSave(@TempDir tempDir: Path) {
        assumeTrue(java.lang.Boolean.getBoolean("tweditor.screenshots"))

        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        var dialog: SavePickerDialog? = null
        SwingUtilities.invokeAndWait {
            dialog = SavePickerDialog(null, environment, tempDir.toFile(), SaveSummaryCache(environment))
        }

        waitUntilLoaded(dialog!!, 1)
        assertEquals(1, dialog!!.loadedCount(), "the fixture save must be summarized")
        val summary = dialog!!.summaryAt(0)!!
        assertEquals(saveInfoName, summary.saveName)
        assertEquals(0, summary.level)
        assertNotNull(summary.screenshot)

        capture(dialog!!, Path.of("docs", "screenshots", "save-picker.png"))

        SwingUtilities.invokeAndWait {
            findButton(dialog!!, "Open").doClick()
        }

        assertEquals(save, dialog!!.selectedFile, "Open must approve the selected save")
    }

    @Test
    fun cancelKeepsNoSelection(@TempDir tempDir: Path) {
        assumeTrue(java.lang.Boolean.getBoolean("tweditor.screenshots"))

        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        var dialog: SavePickerDialog? = null
        SwingUtilities.invokeAndWait {
            dialog = SavePickerDialog(null, environment, tempDir.toFile(), SaveSummaryCache(environment))
        }

        waitUntilLoaded(dialog!!, 1)
        SwingUtilities.invokeAndWait {
            findButton(dialog!!, "Cancel").doClick()
        }

        assertEquals(null, dialog!!.selectedFile, "Cancel must not approve any save")
        assertTrue(save.exists())
    }

    @Test
    fun localSavesDirectoryRendersTheRealBrowser() {
        assumeTrue(java.lang.Boolean.getBoolean("tweditor.screenshots"))

        val saves = SaveSeamSupport.localSaves()
        assumeTrue(saves.isNotEmpty(),
            "no local saves in '" + System.getProperty("tweditor.localSaves", ".local-saves") + "' - drop *.TheWitcherSave files there to exercise them")

        var dialog: SavePickerDialog? = null
        SwingUtilities.invokeAndWait {
            dialog = SavePickerDialog(null, environment, saves[0].getParentFile(), SaveSummaryCache(environment))
        }

        waitUntilLoaded(dialog!!, saves.size)
        assertEquals(saves.size, dialog!!.loadedCount(), "every local save must be summarized")
        capture(dialog!!, Path.of("docs", "screenshots", "save-picker-local.png"))

        SwingUtilities.invokeAndWait {
            findButton(dialog!!, "Cancel").doClick()
        }
        assertEquals(null, dialog!!.selectedFile)
    }

    private fun waitUntilLoaded(dialog: SavePickerDialog, expectedCount: Int) {
        val deadline = System.currentTimeMillis() + 30000
        var loaded = -1
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait {
                loaded = dialog.loadedCount()
            }
            if (loaded >= expectedCount) {
                return
            }
            Thread.sleep(100)
        }
        throw AssertionError("the picker never finished loading summaries: " + loaded + " of " + expectedCount)
    }

    /**
     * Shows the dialog for real (a never-shown dialog has no peer and prints blank),
     * captures it with a Robot like ThemeScreenshotTest does, then closes it.
     * The show must go through invokeLater: invokeAndWait around a modal dialog
     * blocks until it closes, which would hang the test.
     */
    private fun capture(dialog: JDialog, target: Path) {
        Files.createDirectories(target.parent)
        SwingUtilities.invokeLater {
            dialog.isAlwaysOnTop = true
            dialog.setLocationRelativeTo(null)
            dialog.isVisible = true
            dialog.toFront()
        }
        Thread.sleep(1000)

        var bounds: Rectangle? = null
        var device: GraphicsDevice? = null
        SwingUtilities.invokeAndWait {
            bounds = dialog.bounds
            device = dialog.graphicsConfiguration.device
        }
        val robot = Robot(device)
        ImageIO.write(robot.createScreenCapture(bounds), "png", target.toFile())

        SwingUtilities.invokeAndWait {
            dialog.isVisible = false
        }
    }

    private fun findButton(container: Container, text: String): JButton {
        for (component in allComponents(container, JButton::class.java)) {
            if (component.text == text) {
                return component
            }
        }
        throw AssertionError("no button labeled " + text)
    }

    private fun <T : Container> allComponents(container: Container, type: Class<T>): List<T> {
        val found = ArrayList<T>()
        for (component in container.components) {
            if (type.isInstance(component)) {
                found.add(type.cast(component))
            }
            if (component is Container) {
                found.addAll(allComponents(component, type))
            }
        }
        return found
    }

    companion object {
        lateinit var environment: AppEnvironment
        private val saveInfoName = "Территория Каэр Морхен"

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }
    }
}
