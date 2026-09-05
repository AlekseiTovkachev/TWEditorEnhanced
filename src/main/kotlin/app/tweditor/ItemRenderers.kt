package app.tweditor

import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeCellRenderer

/** Icon-and-text renderer for inventory/equipment item lists. */
class ItemListCellRenderer(private val environment: AppEnvironment) : DefaultListCellRenderer() {

    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        selected: Boolean,
        focused: Boolean
    ): Component {
        val label = super.getListCellRendererComponent(list, value, index, selected, focused) as JLabel
        val item = value as? InventoryItem
        if (item != null) {
            label.icon = itemIcon(item)
            label.iconTextGap = 6
        }
        return label
    }

    private fun itemIcon(item: InventoryItem): Icon? {
        return try {
            environment.icons.itemIcon(item.element.getValue() as DBList)
        } catch (exc: DBException) {
            null
        }
    }
}

/** Icon-and-text renderer for the equipment tab's slot rows (populated or empty). */
class EquipSlotCellRenderer(private val environment: AppEnvironment) : DefaultListCellRenderer() {

    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        selected: Boolean,
        focused: Boolean
    ): Component {
        val label = super.getListCellRendererComponent(list, value, index, selected, focused) as JLabel
        val slot = value as? SlotEntry
        if (slot != null) {
            label.text = slot.toString()
            if (slot.item != null) {
                label.icon = itemIcon(slot.item)
                label.iconTextGap = 6
            }
        }
        return label
    }

    private fun itemIcon(item: InventoryItem): Icon? {
        return try {
            environment.icons.itemIcon(item.element.getValue() as DBList)
        } catch (exc: DBException) {
            null
        }
    }
}

/** Icon-and-text renderer for the available-item template tree. */
class ItemTreeCellRenderer(private val environment: AppEnvironment) : DefaultTreeCellRenderer() {

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        focus: Boolean
    ): Component {
        val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus)
        val template = (value as? InventoryNode)?.userObject as? ItemTemplate
        if (component is JLabel && template != null) {
            component.icon = templateIcon(template)
            component.iconTextGap = 6
        }
        return component
    }

    private fun templateIcon(template: ItemTemplate): Icon? =
        environment.icons.templateIcon(template)
}
