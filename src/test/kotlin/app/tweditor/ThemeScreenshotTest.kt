package app.tweditor

import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull

class ThemeScreenshotTest {

    @Test
    fun captureLightAndDarkMainWindow() {
        assumeTrue(java.lang.Boolean.getBoolean("tweditor.screenshots"))

        val outDir = Path.of("docs", "screenshots")
        Files.createDirectories(outDir)
        capture(ThemeSelection.Preference.LIGHT, outDir.resolve("theme-light.png"))
        capture(ThemeSelection.Preference.DARK, outDir.resolve("theme-dark.png"))
    }

    private fun capture(preference: ThemeSelection.Preference, target: Path) {
        val environment = createEnvironment()

        SwingUtilities.invokeAndWait {
            ThemeSelection.install(preference)
        }

        var window: MainWindow? = null
        SwingUtilities.invokeAndWait {
            window = MainWindow(environment)
        }
        SwingUtilities.invokeAndWait {
            window!!.isAlwaysOnTop = true
            window!!.setLocationRelativeTo(null)
            window!!.pack()
            window!!.isVisible = true
            window!!.toFront()
        }

        loadFixture(environment, window!!)

        SwingUtilities.invokeAndWait {
            try {
                window!!.session.setDataChanging(true)
                var list = window!!.session.database!!.getTopLevelStruct()!!.getValue() as DBList
                list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
                list = list.getElement(0).getValue() as DBList

                window!!.statsPanel.setFields(list)
                window!!.attributesPanel.setFields(list)
                window!!.signsPanel.setFields(list)
                window!!.stylesPanel.setFields(list)
                window!!.questsPanel.setFields(list)
                window!!.difficultyPanel.setFields(list)

                window!!.tabbedPane.selectedIndex = 0
                window!!.tabbedPane.isVisible = true
                window!!.session.setDataChanging(false)
                window!!.session.setDataModified(false)
            } catch (exc: Exception) {
                throw RuntimeException(exc)
            }
        }

        Thread.sleep(1000)

        val robot = Robot(window!!.graphicsConfiguration.device)
        val shot = robot.createScreenCapture(window!!.bounds)
        ImageIO.write(shot, "png", target.toFile())

        SwingUtilities.invokeAndWait {
            window!!.dispose()
        }
    }

    private fun createEnvironment(): AppEnvironment {
        val environment = AppEnvironment()
        environment.fileSeparator = System.getProperty("file.separator")
        environment.lineSeparator = System.getProperty("line.separator")
        environment.tmpDir = System.getProperty("java.io.tmpdir")
        environment.properties = java.util.Properties()
        environment.languageID = 3
        environment.resourceFiles = HashMap()
        environment.itemTemplates = ArrayList()
        environment.stringsDatabase = StringsDatabase(fakeTlk().getPath())
        return environment
    }

    private fun loadFixture(environment: AppEnvironment, window: MainWindow) {
        val saveFile = SaveSeamSupport.copyFixtureTo(Files.createTempDirectory("theme-shots"))
        val task = LoadFile(ProgressDialog(window, "Loading"), window.session, environment, saveFile)
        task.run()
        assertNotNull(window.session.saveDatabase, "fixture save failed to load")
    }

    /**
     * A minimal but valid TLK so localized-string lookups resolve to an empty
     * string instead of hitting a missing strings database.
     */
    private fun fakeTlk(): File {
        val file = Files.createTempFile("theme-shots", ".tlk").toFile()
        file.deleteOnExit()
        FileOutputStream(file).use { out ->
            out.write(byteArrayOf('T'.code.toByte(), 'L'.code.toByte(), 'K'.code.toByte(), ' '.code.toByte(), 'V'.code.toByte(), '3'.code.toByte(), '.'.code.toByte(), '0'.code.toByte()))
            out.write(leInt(3))
            out.write(leInt(1))
            out.write(leInt(20 + 40))
            val entry = ByteArray(40)
            entry[0] = 0x01
            entry[28] = 0x00
            entry[32] = 0x00
            out.write(entry)
        }
        return file
    }

    private fun leInt(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value ushr 8).toByte(),
            (value ushr 16).toByte(),
            (value ushr 24).toByte()
        )
    }
}
