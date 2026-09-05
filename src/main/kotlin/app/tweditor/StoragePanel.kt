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
 * The innkeeper storage chest ("Хранилище"). The chest is the engine's global
 * store: it lives in the save's meta database (`save_XXXXXX.smm`) as
 * `StoreList/<record>` with `IsStorage=1` — one record per save, shared by
 * every innkeeper (their `StoreRef="storage"` points at it by ObjectId). The
 * chest accepts every item class: the available tree carries the inventory
 * categorisation plus Steel/Silver/Big/Short Weapon tabs (split by weapon
 * kind), an Armor tab, and a catch-all Other for anything else.
 */
class StoragePanel(private val session: GameSession, private val environment: AppEnvironment) :
    JPanel(), ActionListener {
    private val categoryNames = InventoryPanel.categories + "Steel Sword" + "Silver Sword" + "Big Weapon" + "Short Weapon" + "Armor"
    private val tabOther = InventoryPanel.categories.size - 1
    private val tabSteel = InventoryPanel.categories.size
    private val tabSilver = InventoryPanel.categories.size + 1
    private val tabBig = InventoryPanel.categories.size + 2
    private val tabShort = InventoryPanel.categories.size + 3
    private val tabArmor = InventoryPanel.categories.size + 4

    /** Storage tab per baseitem (type grouping for the tree and the sort). */
    private val categoryByBaseItem: Map<Int, Int> by lazy {
        val map = HashMap<Int, Int>()
        map[1] = tabSteel
        map[2] = tabSilver
        for (baseItem in intArrayOf(3, 4, 5, 6, 7, 9)) {
            map[baseItem] = tabBig
        }
        for (baseItem in intArrayOf(8, 12, 17, 19)) {
            map[baseItem] = tabShort
        }
        map[29] = tabArmor
        for (mapping in InventoryPanel.categoryMappings) {
            map[mapping[0]] = mapping[1]
        }
        map
    }

    /** The chest sort/display key: type first, then name, resref and stack. */
    internal fun categoryOf(element: DBElement): Int {
        val baseItem = (element.getValue() as DBList).getInteger("BaseItem")
        return categoryByBaseItem[baseItem] ?: tabOther
    }
    private val rootNode = DefaultMutableTreeNode("Items")
    private val categoryNodes: Array<CategoryNode> = Array(categoryNames.size) { i -> CategoryNode(categoryNames[i]) }
    private val itemsModel = DefaultListModel<InventoryItem>()
    private val itemsField = JList(itemsModel)
    private val availModel = DefaultTreeModel(rootNode)
    private val availField = JTree(availModel)
    private var availDone = false
    private var storeRecord: DBList? = null

    init {
        for (node in categoryNodes) {
            rootNode.add(node)
        }

        itemsField.selectionMode = 0
        itemsField.prototypeCellValue = InventoryItem("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm", DBElement(14, 0, "", DBList(environment, 0)))
        itemsField.cellRenderer = ItemListCellRenderer(environment)
        itemsField.fixedCellHeight = ROW_HEIGHT
        val currentScrollPane = JScrollPane(itemsField)

        var buttonPane = JPanel()
        var button = JButton("Sort Chest")
        button.addActionListener(this)
        button.actionCommand = "sort chest"
        buttonPane.add(button)

        button = JButton("Examine Item")
        button.addActionListener(this)
        button.actionCommand = "examine current item"
        buttonPane.add(button)

        button = JButton("Edit Item")
        button.addActionListener(this)
        button.actionCommand = "edit current item"
        buttonPane.add(button)

        button = JButton("Remove Item")
        button.addActionListener(this)
        button.actionCommand = "remove current item"
        buttonPane.add(button)

        val itemsPane = JPanel(BorderLayout())
        itemsPane.add(JLabel("Stored Items", 0), "North")
        itemsPane.add(currentScrollPane, "Center")
        itemsPane.add(buttonPane, "South")

        val selectionModel = DefaultTreeSelectionModel()
        selectionModel.selectionMode = 1

        availField.selectionModel = selectionModel
        availField.cellRenderer = ItemTreeCellRenderer(environment)
        availField.rowHeight = ROW_HEIGHT
        val availScrollPane = JScrollPane(availField)

        buttonPane = JPanel()
        button = JButton("Examine Item")
        button.addActionListener(this)
        button.actionCommand = "examine available item"
        buttonPane.add(button)

        button = JButton("Store Item")
        button.addActionListener(this)
        button.actionCommand = "add available item"
        buttonPane.add(button)

        val availPane = JPanel(BorderLayout())
        availPane.add(JLabel("Available Items", 0), "North")
        availPane.add(availScrollPane, "Center")
        availPane.add(buttonPane, "South")

        layout = BorderLayout()
        add(itemsPane, "West")
        add(availPane, "Center")
    }

    /** Populate from the save's meta database (smm) top-level list. */
    @Throws(DBException::class, IOException::class)
    fun setFields(smmList: DBList) {
        storeRecord = findStorageRecord(smmList)
        itemsModel.clear()
        val itemList = storeRecord?.getElement("ItemList")?.getValue() as? DBList
        itemList?.let { list ->
            for (itemElement in list) {
                val itemFields = itemElement.getValue() as DBList
                val itemName = itemFields.getString("LocalizedName")
                if (itemName.isNotEmpty()) {
                    insertItem(InventoryItem(itemName, itemElement))
                }
            }
        }
        buildAvailableTree()
    }

    /** The count of items currently in the chest (for tests). */
    fun storageItemCount(): Int = itemsModel.size()

    /** The available-item tree (for tests). */
    internal fun availTree(): JTree = availField

    /** Appends a clone of the template to the chest; safe to call from tests. */
    @Throws(DBException::class)
    fun addTemplate(template: ItemTemplate) {
        val record = storeRecord ?: throw DBException("This save has no storage chest record")
        var itemList = record.getElement("ItemList")?.getValue() as? DBList
        if (itemList == null) {
            itemList = DBList(environment, 10)
            record.addElement(DBElement(15, 0, "ItemList", itemList))
        }
        val stackSize = template.fieldList.getInteger("MaxStack").coerceAtLeast(1)
        val fieldList = template.fieldList.clone()
        fieldList.setInteger("Dropable", 1, 0)
        fieldList.setInteger("Identified", 1, 0)
        fieldList.setInteger("StackSize", stackSize, 2)

        val element = DBElement(14, 0, "", fieldList)
        itemList.addElement(element)
        insertItem(InventoryItem(template.itemName, element))
        session.setDataModified(true)
        Main.mainWindow?.setTitle(null)
    }

    /**
     * Reorders the chest's item structs by type (the storage tab order), then
     * name, resref and stack. The game displays the chest in list order - the
     * structs carry no per-item grid positions (they all sit at 0,0) - so this
     * is the order the player sees in-game.
     */
    @Throws(DBException::class)
    fun sortChest() {
        val itemList = storeRecord?.getElement("ItemList")?.getValue() as? DBList
            ?: throw DBException("This save has no storage chest record")
        val count = itemList.getElementCount()
        val sorted = (0 until count).map { itemList.getElement(it) }.sortedWith(
            compareBy({ categoryOf(it) }, { displayName(it) }, { resref(it) }, { stackSize(it) })
        )
        for (i in 0 until count) {
            itemList.setElement(i, sorted[i])
        }
        itemsModel.clear()
        for (element in sorted) {
            itemsModel.addElement(InventoryItem(displayName(element), element))
        }
        session.setDataModified(true)
        Main.mainWindow?.setTitle(null)
    }

    private fun displayName(element: DBElement): String {
        val fields = element.getValue() as DBList
        val itemName = fields.getString("LocalizedName")
        return if (itemName.isNotEmpty()) itemName else fields.getString("TemplateResRef")
    }

    private fun resref(element: DBElement): String {
        return (element.getValue() as DBList).getString("TemplateResRef")
    }

    private fun stackSize(element: DBElement): Int {
        return (element.getValue() as DBList).getInteger("StackSize")
    }

    private fun insertItem(item: InventoryItem) {
        val listSize = itemsModel.size()
        var inserted = false
        for (j in 0 until listSize) {
            if (compareForDisplay(item, itemsModel.getElementAt(j)) < 0) {
                itemsModel.insertElementAt(item, j)
                inserted = true
                break
            }
        }
        if (!inserted) {
            itemsModel.addElement(item)
        }
    }

    /** The display order matches the chest sort: type, then name, resref, stack. */
    private fun compareForDisplay(a: InventoryItem, b: InventoryItem): Int {
        var diff = categoryOf(a.element) - categoryOf(b.element)
        if (diff == 0) {
            diff = a.name.compareTo(b.name)
        }
        if (diff == 0) {
            diff = resref(a.element).compareTo(resref(b.element))
        }
        if (diff == 0) {
            diff = a.count.compareTo(b.count)
        }
        return diff
    }

    /** Removes an item struct from the chest; safe to call from tests. */
    @Throws(DBException::class)
    fun removeItem(item: InventoryItem) {
        val itemList = storeRecord?.getElement("ItemList")?.getValue() as? DBList
            ?: throw DBException("This save has no storage chest record")
        for (itemElement in itemList) {
            if (itemElement === item.element) {
                itemList.removeElement(itemElement)
                // Copies of the same item compare equal, so remove exactly this
                // row by element identity, not via equals-based removeElement.
                for (j in 0 until itemsModel.size()) {
                    if (itemsModel.getElementAt(j).element === item.element) {
                        itemsModel.removeElementAt(j)
                        break
                    }
                }
                session.setDataModified(true)
                Main.mainWindow?.setTitle(null)
                return
            }
        }
        throw DBException("The item is no longer in the storage chest")
    }

    override fun actionPerformed(ae: ActionEvent?) {
        try {
            when (ae!!.actionCommand) {
                "sort chest" -> sortCurrentChest()
                "examine current item" -> examineCurrentItem()
                "edit current item" -> editCurrentItem()
                "remove current item" -> removeCurrentItem()
                "examine available item" -> examineAvailableItem()
                "add available item" -> addSelectedItem()
            }
        } catch (exc: DBException) {
            Main.logException("Unable to process database field", exc)
        } catch (exc: IOException) {
            Main.logException("An I/O error occurred", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    private fun sortCurrentChest() {
        if (storeRecord == null) {
            JOptionPane.showMessageDialog(this,
                "This save has no storage chest yet - use any innkeeper's storage in the game once, save, then edit it here",
                "No storage chest", 0)
            return
        }
        try {
            sortChest()
        } catch (exc: DBException) {
            JOptionPane.showMessageDialog(this, exc.message, "Storage", 0)
        }
    }

    private fun examineCurrentItem() {
        val sel = itemsField.selectedIndex
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "You must select an item to examine", "No item selected", 0)
            return
        }
        val item = itemsModel.getElementAt(sel)
        examineItem(item.name, item.element.getValue() as DBList)
    }

    private fun editCurrentItem() {
        val sel = itemsField.selectedIndex
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "You must select an item to edit", "No item selected", 0)
            return
        }
        val item = itemsModel.getElementAt(sel)
        ItemEditDialog.showDialog(Main.mainWindow, session, environment, item.name, item.element.getValue() as DBList)
    }

    private fun removeCurrentItem() {
        val sel = itemsField.selectedIndex
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "You must select an item to remove", "No item selected", 0)
            return
        }
        try {
            removeItem(itemsModel.getElementAt(sel))
        } catch (exc: DBException) {
            JOptionPane.showMessageDialog(this, exc.message, "Storage", 0)
        }
    }

    private fun examineAvailableItem() {
        val treePath = availField.selectionPath
        val node = treePath?.lastPathComponent as? DefaultMutableTreeNode
        val template = node?.userObject as? ItemTemplate
        if (template == null) {
            JOptionPane.showMessageDialog(this, "You must select an item to examine", "No item selected", 0)
            return
        }
        examineItem(template.itemName, template.fieldList)
    }

    private fun addSelectedItem() {
        if (storeRecord == null) {
            JOptionPane.showMessageDialog(this,
                "This save has no storage chest yet - use any innkeeper's storage in the game once, save, then edit it here",
                "No storage chest", 0)
            return
        }
        val treePath = availField.selectionPath
        val node = treePath?.lastPathComponent as? DefaultMutableTreeNode
        val template = node?.userObject as? ItemTemplate
        if (template == null) {
            JOptionPane.showMessageDialog(this, "You must select an item to store", "No item selected", 0)
            return
        }
        try {
            addTemplate(template)
        } catch (exc: DBException) {
            JOptionPane.showMessageDialog(this, exc.message, "Storage", 0)
        }
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
        ExamineDialog.showDialog(Main.mainWindow, environment, label, description.toString())
    }

    private fun buildAvailableTree() {
        if (availDone) {
            return
        }
        for (itemTemplate in environment.itemTemplates) {
            val baseItem = itemTemplate.baseItem
            if (baseItem == 36 || baseItem == 43) {
                continue // fists pseudo-item, npc work tool
            }
            categoryNodes[categoryByBaseItem[baseItem] ?: tabOther].insert(InventoryNode(itemTemplate))
        }
        availModel.nodeStructureChanged(rootNode)
        availDone = true
    }

    companion object {
        private const val ROW_HEIGHT = 56

        /**
         * The chest record inside the smm: `StoreList/<record>` with
         * `IsStorage=1` (falls back to the first record; the game keeps one
         * global storage store per save).
         */
        fun findStorageRecord(smmList: DBList): DBList? {
            val storeList = smmList.getElement("StoreList")?.getValue() as? DBList ?: return null
            var fallback: DBList? = null
            for (element in storeList) {
                val record = element.getValue() as? DBList ?: continue
                if (record.getInteger("IsStorage") == 1) {
                    return record
                }
                if (fallback == null) {
                    fallback = record
                }
            }
            return fallback
        }
    }
}
