package app.tweditor

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.IOException
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel

/**
 * The character's equipment, shown as the game's paperdoll: every slot is one
 * row, populated or empty. Each equipped item struct in
 * `Mod_PlayerList[0]/Equip_ItemList` carries `WeaponSlot` = the
 * weaponslots.2da row of its position, and baseitems.2da's EquipableSlots
 * mask says which positions an item class accepts. Adding requires an empty
 * slot to be selected; the item is written with that exact slot.
 */
class EquipPanel(private val session: GameSession, private val environment: AppEnvironment) :
    JPanel(), ActionListener {
    private val rootNode = DefaultMutableTreeNode("Items")
    private val categoryNodes: Array<CategoryNode> = Array(categories.size) { i -> CategoryNode(categories[i]) }
    private val slotModel = DefaultListModel<SlotEntry>()
    private val slotsField = JList(slotModel)
    private val availModel = DefaultTreeModel(rootNode)
    private val availField = JTree(availModel)
    private var availDone = false

    init {
        for (node in categoryNodes) {
            rootNode.add(node)
        }

        slotsField.selectionMode = 0
        slotsField.visibleRowCount = 20
        slotsField.prototypeCellValue = SlotEntry("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm", 0, null)
        slotsField.cellRenderer = EquipSlotCellRenderer(environment)
        slotsField.fixedCellHeight = ROW_HEIGHT
        var scrollPane = JScrollPane(slotsField)

        var buttonPane = JPanel()
        var button = JButton("Examine Item")
        button.addActionListener(this)
        button.actionCommand = "examine current item"
        buttonPane.add(button)

        button = JButton("Edit Item")
        button.addActionListener(this)
        button.actionCommand = "edit current item"
        buttonPane.add(button)

        button = JButton("Move to Slot")
        button.addActionListener(this)
        button.actionCommand = "move current item"
        buttonPane.add(button)

        button = JButton("Remove Item")
        button.addActionListener(this)
        button.actionCommand = "remove current item"
        buttonPane.add(button)

        val itemsPane = JPanel(BorderLayout())
        itemsPane.add(JLabel("Equipment Slots", 0), "North")
        itemsPane.add(scrollPane, "Center")
        itemsPane.add(buttonPane, "South")

        val selectionModel = DefaultTreeSelectionModel()
        selectionModel.selectionMode = 1

        availField.selectionModel = selectionModel
        availField.cellRenderer = ItemTreeCellRenderer(environment)
        availField.rowHeight = ROW_HEIGHT
        scrollPane = JScrollPane(availField)

        buttonPane = JPanel()
        button = JButton("Examine Item")
        button.addActionListener(this)
        button.actionCommand = "examine available item"
        buttonPane.add(button)

        button = JButton("Add Item")
        button.addActionListener(this)
        button.actionCommand = "add available item"
        buttonPane.add(button)

        val availPane = JPanel(BorderLayout())
        availPane.add(JLabel("Available Items", 0), "North")
        availPane.add(scrollPane, "Center")
        availPane.add(buttonPane, "South")

        // Fill the tab so the scroll panes shrink with the window; otherwise a
        // tall preferred size clips both lists below the fold with no scrollbar.
        layout = BorderLayout()
        add(itemsPane, "West")
        add(availPane, "Center")
    }

    override fun actionPerformed(ae: ActionEvent?) {
        try {
            val action = ae!!.actionCommand
            if (action == "examine available item") {
                examineAvailableItem()
            } else if (action == "examine current item") {
                examineCurrentItem()
            } else if (action == "edit current item") {
                editCurrentItem()
            } else if (action == "move current item") {
                moveSelectedItem()
            } else if (action == "add available item") {
                addSelectedItem()
            } else if (action == "remove current item") {
                removeSelectedItem()
            }
        } catch (exc: DBException) {
            Main.logException("Unable to process database field", exc)
        } catch (exc: IOException) {
            Main.logException("An I/O error occurred", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    /** The selected paperdoll row, or null (the caller reports the problem). */
    private fun selectedSlotEntry(): SlotEntry? {
        val sel = slotsField.selectedIndex
        return if (sel < 0) null else slotModel.getElementAt(sel)
    }

    private fun examineCurrentItem() {
        val entry = selectedSlotEntry()
        if (entry == null) {
            JOptionPane.showMessageDialog(this, "You must select a slot", "No slot selected", 0)
            return
        }
        if (entry.item == null) {
            JOptionPane.showMessageDialog(this, "The " + entry.slotName + " slot is empty", "Empty slot", 0)
            return
        }

        examineItem(entry.item.name, entry.item.element.getValue() as DBList)
    }

    private fun editCurrentItem() {
        val entry = selectedSlotEntry()
        if (entry == null) {
            JOptionPane.showMessageDialog(this, "You must select a slot", "No slot selected", 0)
            return
        }
        if (entry.item == null) {
            JOptionPane.showMessageDialog(this, "The " + entry.slotName + " slot is empty", "Empty slot", 0)
            return
        }

        ItemEditDialog.showDialog(Main.mainWindow, session, environment, entry.item.name, entry.item.element.getValue() as DBList)
    }

    @Throws(DBException::class)
    private fun moveSelectedItem() {
        val entry = selectedSlotEntry()
        if (entry == null) {
            JOptionPane.showMessageDialog(this, "You must select a slot", "No slot selected", 0)
            return
        }
        if (entry.item == null) {
            JOptionPane.showMessageDialog(this, "The " + entry.slotName + " slot is empty", "Empty slot", 0)
            return
        }

        val fields = entry.item.element.getValue() as DBList
        val slotElement = fields.getElement("WeaponSlot")
        val current = (slotElement?.getValue() as? Number)?.toInt() ?: 0
        val mask = equipableMasks[fields.getInteger("BaseItem")] ?: 0
        // Offer only the paperdoll slots; Left_Hand (26) is a leftover.
        val slots = WeaponSlots.slotsFor(mask).filter { SLOT_ORDER.contains(it) }.toMutableList()
        if (slots.isEmpty()) {
            JOptionPane.showMessageDialog(this, "This item has no alternative equipment slots", "No slots", 0)
            return
        }
        if (!slots.contains(current)) {
            slots.add(0, current)
        }

        val combo = javax.swing.JComboBox(slots.map { WeaponSlots.name(it) }.toTypedArray())
        combo.selectedIndex = 0
        val panel = JPanel(BorderLayout())
        panel.add(JLabel(entry.item.name + " is in " + WeaponSlots.name(current)), BorderLayout.NORTH)
        panel.add(combo, BorderLayout.CENTER)
        val choice = JOptionPane.showConfirmDialog(Main.mainWindow, panel, "Move to Slot", JOptionPane.OK_CANCEL_OPTION)
        if (choice != JOptionPane.OK_OPTION) {
            return
        }

        val slot = slots[combo.selectedIndex]
        if (slotElement != null) {
            slotElement.setValue(slot)
        } else {
            fields.setInteger("WeaponSlot", slot)
        }
        refreshSlots()

        session.setDataModified(true)
        Main.mainWindow?.setTitle(null)
    }

    @Throws(DBException::class)
    private fun removeSelectedItem() {
        val sel = slotsField.selectedIndex
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "You must select a slot", "No slot selected", 0)
            return
        }
        val entry = slotModel.getElementAt(sel)
        if (entry.item == null) {
            JOptionPane.showMessageDialog(this, "The " + entry.slotName + " slot is empty", "Empty slot", 0)
            return
        }

        val itemElement = entry.item.element
        var list = session.database!!.getTopLevelStruct()!!.getValue() as DBList
        list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
        list = list.getElement(0).getValue() as DBList
        val itemList = list.getElement("Equip_ItemList")!!.getValue() as DBList
        for (element in itemList) {
            if (element === itemElement) {
                itemList.removeElement(element)
                break
            }
        }
        // Duplicates share the slot; this removes exactly one of them.
        refreshSlots()

        session.setDataModified(true)
        Main.mainWindow!!.setTitle(null)
    }

    @Throws(DBException::class, IOException::class)
    private fun examineAvailableItem() {
        val count = availField.selectionCount
        if (count == 0) {
            JOptionPane.showMessageDialog(this, "You must select an item to examine", "No item selected", 0)
            return
        }

        val treePath = availField.selectionPath
        val node = treePath!!.lastPathComponent as DefaultMutableTreeNode
        val userObject = node.userObject
        if (userObject !is ItemTemplate) {
            JOptionPane.showMessageDialog(this, "You must select an item to examine", "No item selected", 0)
            return
        }

        examineItem(userObject.itemName, userObject.fieldList)
    }

    @Throws(DBException::class, IOException::class)
    private fun addSelectedItem() {
        val entry = selectedSlotEntry()
        if (entry == null) {
            JOptionPane.showMessageDialog(this, "You must select the slot to fill", "No slot selected", 0)
            return
        }
        if (entry.item != null) {
            JOptionPane.showMessageDialog(this, "The " + entry.slotName + " slot is occupied - remove the item first",
                "Slot occupied", 0)
            return
        }

        val count = availField.selectionCount
        if (count == 0) {
            JOptionPane.showMessageDialog(this, "You must select an item to add", "No item selected", 0)
            return
        }

        val treePath = availField.selectionPath
        val node = treePath!!.lastPathComponent as DefaultMutableTreeNode
        val userObject = node.userObject
        if (userObject !is ItemTemplate) {
            JOptionPane.showMessageDialog(this, "You must select an item to add", "No item selected", 0)
            return
        }

        try {
            addTemplateToSlot(userObject, entry.slot)
            refreshSlots()
        } catch (exc: DBException) {
            JOptionPane.showMessageDialog(this, exc.message, "Wrong slot", 0)
            return
        }

        session.setDataModified(true)
        Main.mainWindow!!.setTitle(null)
    }

    /**
     * Writes a clone of the template into the equipment list with the given
     * weaponslot. The item class's EquipableSlots mask must accept the slot.
     * Shared by the Add button (with an empty slot selected) and tests.
     */
    @Throws(DBException::class)
    internal fun addTemplateToSlot(template: ItemTemplate, slot: Int): InventoryItem {
        val baseItem = template.fieldList.getInteger("BaseItem")
        val mask = equipableMasks[baseItem] ?: 0
        if (!WeaponSlots.slotsFor(mask).contains(slot)) {
            throw DBException(template.itemName + " cannot go in " + WeaponSlots.name(slot) +
                " (its slots: " + WeaponSlots.slotsFor(mask).joinToString { WeaponSlots.name(it) } + ")")
        }

        val stackSize = template.fieldList.getInteger("MaxStack").coerceAtLeast(1)

        val fieldList = template.fieldList.clone()
        fieldList.setInteger("Dropable", 1, 0)
        fieldList.setInteger("Identified", 1, 0)
        fieldList.setInteger("StackSize", stackSize, 2)
        fieldList.setInteger("WeaponSlot", slot)

        var list = session.database!!.getTopLevelStruct()!!.getValue() as DBList
        list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
        list = list.getElement(0).getValue() as DBList
        var element = list.getElement("Equip_ItemList")
        val itemList: DBList
        if (element == null) {
            itemList = DBList(environment, 10)
            element = DBElement(15, 0, "Equip_ItemList", itemList)
            list.addElement(element!!)
        } else {
            itemList = element.getValue() as DBList
        }

        element = DBElement(14, 0, "", fieldList)
        itemList.addElement(element!!)

        return InventoryItem(template.itemName, element)
    }

    fun setFields(list: DBList) {
        if (!availDone) {
            for (itemTemplate in environment.itemTemplates) {
                val baseItem = itemTemplate.baseItem
                for (categoryMapping in categoryMappings) {
                    if (categoryMapping[0] == baseItem) {
                        val categoryNode = categoryNodes[categoryMapping[1]]
                        val inventoryNode = InventoryNode(itemTemplate)
                        categoryNode.insert(inventoryNode)
                        break
                    }
                }
            }

            availModel.nodeStructureChanged(rootNode)
            availDone = true
        }

        slotsField.selectedIndex = -1
        refreshSlots()
    }

    /** Rebuilds the paperdoll rows from the save's Equip_ItemList, grouping
     * duplicates into one row (e.g. "Big weapon (x6)") so they stay visible
     * and removable; anything outside the paperdoll slots goes to tail rows. */
    private fun refreshSlots() {
        var list = session.database!!.getTopLevelStruct()!!.getValue() as DBList
        list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
        list = list.getElement(0).getValue() as DBList
        val bySlot = LinkedHashMap<Int, MutableList<DBElement>>()
        val element = list.getElement("Equip_ItemList")
        if (element != null && element.getType() == 15) {
            for (itemElement in element.getValue() as DBList) {
                val itemFields = itemElement.getValue() as DBList
                if (itemFields.getInteger("BaseItem") == 36) {
                    continue // the fists pseudo-item is part of Geralt, not gear
                }
                bySlot.getOrPut(itemFields.getInteger("WeaponSlot")) { ArrayList() }.add(itemElement)
            }
        }

        val sel = slotsField.selectedIndex
        slotModel.clear()
        for (slot in SLOT_ORDER) {
            slotModel.addElement(slotRow(slot, bySlot.remove(slot) ?: emptyList()))
        }
        for ((slot, group) in bySlot) {
            slotModel.addElement(slotRow(slot, group))
        }

        slotsField.model = slotModel
        slotsField.selectedIndex = if (sel >= 0 && sel < slotModel.size()) sel else -1
        if (slotModel.size() > 0) {
            slotsField.ensureIndexIsVisible(0)
        }
    }

    private fun slotRow(slot: Int, group: List<DBElement>): SlotEntry {
        if (group.isEmpty()) {
            return SlotEntry(WeaponSlots.name(slot), slot, null)
        }
        val label = if (group.size > 1) WeaponSlots.name(slot) + " (x" + group.size + ")" else WeaponSlots.name(slot)
        return SlotEntry(label, slot, itemOf(group[0]))
    }

    fun getFields(list: DBList) {
    }

    /** The available-item tree (for tests). */
    internal fun availTree(): JTree = availField

    private fun itemOf(element: DBElement?): InventoryItem? {
        if (element == null) {
            return null
        }
        val fields = element.getValue() as DBList
        val itemName = fields.getString("LocalizedName")
        return InventoryItem(if (itemName.isNotEmpty()) itemName else fields.getString("TemplateResRef"), element)
    }

    private fun examineItem(label: String, fieldList: DBList) {
        val description = StringBuilder(256)

        var string = fieldList.getString("DescIdentified")
        if (string.isEmpty()) {
            string = fieldList.getString("Description")
        }
        if (string.isNotEmpty()) {
            description.append(string)
        }

        val extraDescription = fieldList.getString("ExtraDesc")
        if (extraDescription.isNotEmpty()) {
            description.append("<br><br>")
            description.append(extraDescription)
        }

        ExamineDialog.showDialog(Main.mainWindow, environment, label, description.toString())
    }

    /** The baseitems.2da EquipableSlots mask per baseitem row (read once). */
    private val equipableMasks: Map<Int, Int> by lazy {
        WeaponSlots.equipableSlots(environment)
    }

    companion object {
        private const val ROW_HEIGHT = 56

        /** The paperdoll rows, in the game's order (weapons, armor, rings, belt, elixirs).
         * Left_Hand (row 26) is a development leftover - the torch actually sits
         * in a sidearm slot - so it is not shown; anything in slot 26 would
         * appear in the tail rows. */
        private val SLOT_ORDER = intArrayOf(
            WeaponSlots.BACK_NORMAL, WeaponSlots.BACK_SILVER,
            WeaponSlots.SHORT_1, WeaponSlots.SHORT_2, WeaponSlots.BIG_WEAPON,
            WeaponSlots.ARMOR, WeaponSlots.TROPHY,
            WeaponSlots.FOREARM_RIGHT, WeaponSlots.FOREARM_LEFT,
            WeaponSlots.ELIXIR_1, WeaponSlots.ELIXIR_2, WeaponSlots.ELIXIR_3
        )

        private val categories = arrayOf("Armor", "Silver Sword", "Steel Sword", "Big Weapon", "Short Weapon", "Potion", "Accessory", "Trophy")
        private const val TAB_ARMOR = 0
        private const val TAB_SILVER_SWORD = 1
        private const val TAB_STEEL_SWORD = 2
        private const val TAB_BIG_WEAPON = 3
        private const val TAB_SHORT_WEAPON = 4
        private const val TAB_POTION = 5
        private const val TAB_ACCESSORY = 6
        private const val TAB_TROPHY = 7
        private val categoryMappings = arrayOf(
            intArrayOf(29, TAB_ARMOR),
            intArrayOf(2, TAB_SILVER_SWORD),
            intArrayOf(1, TAB_STEEL_SWORD),
            intArrayOf(3, TAB_BIG_WEAPON), intArrayOf(4, TAB_BIG_WEAPON), intArrayOf(5, TAB_BIG_WEAPON),
            intArrayOf(6, TAB_BIG_WEAPON), intArrayOf(7, TAB_BIG_WEAPON), intArrayOf(9, TAB_BIG_WEAPON),
            intArrayOf(8, TAB_SHORT_WEAPON), intArrayOf(12, TAB_SHORT_WEAPON), intArrayOf(17, TAB_SHORT_WEAPON),
            intArrayOf(19, TAB_SHORT_WEAPON),
            intArrayOf(22, TAB_POTION), intArrayOf(47, TAB_POTION),
            intArrayOf(20, TAB_ACCESSORY), intArrayOf(21, TAB_ACCESSORY), intArrayOf(23, TAB_ACCESSORY),
            intArrayOf(37, TAB_ACCESSORY),
            intArrayOf(39, TAB_TROPHY)
        )
    }
}
