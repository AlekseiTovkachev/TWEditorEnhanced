package app.tweditor

import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.Robot
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Gated visual evidence for the icon feature: renders item lists and the
 * ability grid with real textures from the game install. Runs only with
 * `-Dtweditor.screenshots=true` and only on a machine with the install.
 */
@Timeout(180)
class IconScreenshotsTest {

    @Test
    fun captureItemAndAbilityIcons() {
        assumeTrue(java.lang.Boolean.getBoolean("tweditor.screenshots"))
        val environment = environment() ?: return assumeTrue(false, "game install not present")

        loadTemplates(environment, screenshotTemplates(environment))
        val library = environment.icons
        library.primeTemplates(environment.itemTemplates)
        library.primeAbilities(SignsPanel.abilityLabels() + StylesPanel.abilityLabels())
        waitForDrain(environment)

        SwingUtilities.invokeAndWait { ThemeSelection.install() }

        val outDir = File("docs", "screenshots")
        outDir.mkdirs()
        captureItems(environment, outDir)
        captureAbilities(environment, outDir)
    }

    private fun captureItems(environment: AppEnvironment, outDir: File) {
        val items = environment.itemTemplates.take(14).map { template ->
            InventoryItem(template.itemName, DBElement(14, 0, "", template.fieldList))
        }
        var frame: JFrame? = null
        SwingUtilities.invokeAndWait {
            val list = JList(items.toTypedArray())
            list.cellRenderer = ItemListCellRenderer(environment, 32)
            list.fixedCellHeight = 42
            val panel = JPanel(BorderLayout())
            panel.add(JLabel("Current Inventory"), BorderLayout.NORTH)
            panel.add(JScrollPane(list), BorderLayout.CENTER)
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            frame = JFrame("Icons: items")
            frame.contentPane = panel
            frame.pack()
            frame.setSize(420, 560)
            frame.isAlwaysOnTop = true
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
        }
        Thread.sleep(600)
        val robot = Robot(frame!!.graphicsConfiguration.device)
        ImageIO.write(robot.createScreenCapture(frame!!.bounds), "png", File(outDir, "icons-inventory.png"))
        SwingUtilities.invokeAndWait { frame!!.dispose() }
    }

    private fun captureAbilities(environment: AppEnvironment, outDir: File) {
        val labels = (SignsPanel.abilityLabels() + StylesPanel.abilityLabels()).take(30)
        var frame: JFrame? = null
        SwingUtilities.invokeAndWait {
            val grid = JPanel(GridLayout(0, 3, 8, 8))
            for (label in labels) {
                val field = JCheckBox(label)
                field.icon = environment.icons.abilityIcon(label, 18)
                grid.add(field)
            }
            val panel = JPanel(BorderLayout())
            panel.add(JLabel("Signs & Styles"), BorderLayout.NORTH)
            panel.add(JScrollPane(grid), BorderLayout.CENTER)
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            frame = JFrame("Icons: abilities")
            frame.contentPane = panel
            frame.pack()
            frame.setSize(560, 560)
            frame.isAlwaysOnTop = true
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
        }
        Thread.sleep(600)
        val robot = Robot(frame!!.graphicsConfiguration.device)
        ImageIO.write(robot.createScreenCapture(frame!!.bounds), "png", File(outDir, "icons-abilities.png"))
        SwingUtilities.invokeAndWait { frame!!.dispose() }
    }

    /** Waits until every queued icon decode has landed in the cache. */
    private fun waitForDrain(environment: AppEnvironment) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val allLoaded = environment.itemTemplates.all { template ->
                environment.icons.templateIcon(template, 32) != null
            } && (SignsPanel.abilityLabels() + StylesPanel.abilityLabels()).all { label ->
                environment.icons.abilityIcon(label, 18) != null
            }
            if (allLoaded) {
                return
            }
            Thread.sleep(50)
        }
    }

    private fun loadTemplates(environment: AppEnvironment, resrefs: List<String>) {
        for (resref in resrefs) {
            val entry = environment.resourceFiles[resref + ".uti"] ?: continue
            val stream: InputStream? = when (entry) {
                is File -> FileInputStream(entry)
                is KeyEntry -> entry.getInputStream()
                else -> null
            }
            if (stream == null) {
                continue
            }
            val database = Database(environment)
            database.load(stream)
            stream.close()
            val fieldList = database.getTopLevelStruct()!!.getValue() as DBList
            val itemName = fieldList.getString("LocalizedName")
            val itemDescription = fieldList.getString("Description")
            if (itemName.isNotEmpty() && itemDescription.isNotEmpty()) {
                fieldList.setElement("TemplateResRef", DBElement(11, 0, "TemplateResRef", resref))
                environment.itemTemplates.add(ItemTemplate(fieldList))
            }
        }
    }

    private fun environment(): AppEnvironment? {
        val installData = File("C:\\Games\\The Witcher Enhanced Edition\\Data")
        val mainKey = File(installData, "main.key")
        if (!mainKey.isFile) {
            return null
        }
        val environment = AppEnvironment()
        environment.fileSeparator = "\\"
        environment.lineSeparator = System.getProperty("line.separator")
        environment.tmpDir = System.getProperty("java.io.tmpdir")
        environment.languageID = 3
        environment.stringsDatabase = StringsDatabase(File(installData, "dialog_3.tlk").path)
        environment.resourceFiles = Main.resourceFilesFrom(KeyDatabase(environment, mainKey.path))
        return environment
    }

    companion object {
        /** Verified-per-probe names, filled up with a filtered scan of the scan list. */
        private val CURATED_RESREFS = listOf(
            "it_stlswd_001", "it_stlswd_005", "it_stlswd_014", "it_stlswd_rrr", "it_stlswd_yyy",
            "it_svswd_001", "it_svswd_eee", "it_bomb_001", "it_drink_001", "it_gem_001",
            "it_torch_001", "it_trophy_001", "it_food_001", "it_grease_001", "dice_adv_001"
        )

        private fun screenshotTemplates(environment: AppEnvironment): List<String> {
            val resrefs = ArrayList(CURATED_RESREFS)
            val skipPrefixes = listOf("w_h_", "ff_", "it_v", "it_uniq", "it_val", "is_", "jp_", "fx_")
            var diceCount = resrefs.count { it.startsWith("dice") }
            for (name in environment.resourceFiles.keys.filter { it.endsWith(".uti") }.map { it.removeSuffix(".uti") }.sorted()) {
                if (resrefs.size >= 45) {
                    break
                }
                if (skipPrefixes.any { name.startsWith(it) }) {
                    continue
                }
                if (name.startsWith("dice") && ++diceCount > 2) {
                    continue
                }
                if (name !in resrefs) {
                    resrefs.add(name)
                }
            }
            return resrefs
        }
    }
}
