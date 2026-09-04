package app.tweditor

import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JList
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Component
import java.awt.Container
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class ItemEditDialogTest {
    @Test
    fun constructingTheDialogPopulatesEveryField(@TempDir tempDir: Path) {
        assumeTrue(java.lang.Boolean.getBoolean("tweditor.screenshots"))

        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val fields = rustySword(loaded)
        val edit = ItemEdit(environment, fields)

        var dialog: ItemEditDialog? = null
        SwingUtilities.invokeAndWait {
            dialog = ItemEditDialog(null, loaded.session, environment, "Rusty sword", fields)
        }

        assertEquals(listOf("it_stlswd_006"), allRows(dialog!!)[0], "self ability list must be prefilled")
        assertTrue(dialog!!.title.contains("Rusty sword"))
        assertFalse(loaded.session.isDataModified(), "opening the dialog must not modify data")

        SwingUtilities.invokeAndWait {
            dialog!!.dispose()
        }
    }

    @Test
    fun applyWritesEveryEditedFieldOntoTheItemStruct(@TempDir tempDir: Path) {
        assumeTrue(java.lang.Boolean.getBoolean("tweditor.screenshots"))

        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val fields = rustySword(loaded)
        val edit = ItemEdit(environment, fields)

        var dialog: ItemEditDialog? = null
        SwingUtilities.invokeLater {
            dialog = ItemEditDialog(null, loaded.session, environment, "Rusty sword", fields)
            dialog!!.isVisible = true
        }
        SwingUtilities.invokeAndWait {
            val numericFields = allComponents(dialog!!, NumericField::class.java)
            assertEquals(3, numericFields.size)
            numericFields[0].text = "2"
            numericFields[1].text = "1"
            numericFields[2].text = "750"

            findButton(dialog!!, "Apply").doClick()
        }

        assertEquals(2, edit.modelPart1, "ModelPart1 must be applied onto the item struct")
        assertEquals(1, edit.quality, "Quality must be applied onto the item struct")
        assertEquals(750, edit.customCost, "CustomCost must be applied onto the item struct")
        assertTrue(loaded.session.isDataModified(), "Apply must flag the session as modified")
        assertEquals(listOf(WeaponAbility("it_stlswd_006", 1)), edit.weaponAbilitiesSelf,
            "an untouched ability list must be applied unchanged")
    }

    private fun rustySword(loaded: SaveSeamSupport.Loaded): DBList {
        val equipList = loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        for (itemElement in equipList) {
            val fields = itemElement.getValue() as DBList
            if (fields.getInteger("BaseItem") == 1) {
                return fields
            }
        }
        throw AssertionError("the fixture save has no steel sword in Equip_ItemList")
    }

    private fun allRows(dialog: JDialog): List<List<String>> = allComponents(dialog, JList::class.java).map { list ->
        (0 until list.model.size).map { list.model.getElementAt(it) as String }
    }

    private fun findButton(container: Container, text: String): JButton {
        for (component in allComponents(container, JButton::class.java)) {
            if (component.text == text) {
                return component
            }
        }
        throw AssertionError("no button labeled " + text)
    }

    private fun <T : Component> allComponents(container: Container, type: Class<T>): List<T> {
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

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }
    }
}
