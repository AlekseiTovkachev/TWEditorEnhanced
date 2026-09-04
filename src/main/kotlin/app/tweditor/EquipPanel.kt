package app.tweditor

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.IOException
import javax.swing.Box
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
import javax.swing.tree.TreePath

class EquipPanel(private val session: GameSession, private val environment: AppEnvironment) :
    JPanel(), ActionListener {
    private val rootNode = DefaultMutableTreeNode("Items")
    private val categoryNodes: Array<CategoryNode> = Array(categories.size) { i -> CategoryNode(categories[i]) }
    private val itemsModel = DefaultListModel<InventoryItem>()
    private val itemsField = JList(itemsModel)
    private val availModel = DefaultTreeModel(rootNode)
    private val availField = JTree(availModel)
    private var availDone = false

    init {
        for (node in categoryNodes) {
            rootNode.add(node)
        }

        itemsField.selectionMode = 0
        itemsField.visibleRowCount = 20
        itemsField.prototypeCellValue = InventoryItem("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm", DBElement(14, 0, "", DBList(environment, 0)))
        var scrollPane = JScrollPane(itemsField)
        val preferredSize: Dimension = scrollPane.preferredSize

        var buttonPane = JPanel()
        var button = JButton("Examine Item")
        button.addActionListener(this)
        button.actionCommand = "examine current item"
        buttonPane.add(button)

        button = JButton("Remove Item")
        button.addActionListener(this)
        button.actionCommand = "remove current item"
        buttonPane.add(button)

        val itemsPane = JPanel(BorderLayout())
        itemsPane.add(JLabel("Current Inventory", 0), "North")
        itemsPane.add(scrollPane, "Center")
        itemsPane.add(buttonPane, "South")

        val selectionModel = DefaultTreeSelectionModel()
        selectionModel.selectionMode = 1

        availField.selectionModel = selectionModel
        scrollPane = JScrollPane(availField)
        scrollPane.preferredSize = preferredSize

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
        availPane.preferredSize = itemsPane.preferredSize

        add(itemsPane)
        add(Box.createHorizontalStrut(15))
        add(availPane)
    }

    override fun actionPerformed(ae: ActionEvent?) {
        try {
            val action = ae!!.actionCommand
            if (action == "examine available item") {
                examineAvailableItem()
            } else if (action == "examine current item") {
                examineCurrentItem()
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

    @Throws(DBException::class, IOException::class)
    private fun examineCurrentItem() {
        val sel = itemsField.selectedIndex
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "You must select an item to examine", "No item selected", 0)
            return
        }

        val item = itemsModel.getElementAt(sel)

        examineItem(item.name, item.element.getValue() as DBList)
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

    @Throws(DBException::class)
    private fun removeSelectedItem() {
        val sel = itemsField.selectedIndex
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "You must select an item to remove", "No item selected", 0)
            return
        }

        val item = itemsModel.getElementAt(sel)
        val itemElement = item.element

        itemsModel.removeElementAt(sel)
        itemsField.selectedIndex = -1

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

        session.setDataModified(true)
        Main.mainWindow.setTitle(null)
    }

    @Throws(DBException::class, IOException::class)
    private fun addSelectedItem() {
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

        val template = userObject
        val templateList = template.fieldList
        val weaponSlot = templateList.getInteger("WeaponSlot")

        var list = session.database!!.getTopLevelStruct()!!.getValue() as DBList
        list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
        list = list.getElement(0).getValue() as DBList
        var element = list.getElement("Equip_ItemList")
        if (element != null) {
            var swordCount = 0
            val itemList = element.getValue() as DBList
            for (itemElement in itemList) {
                val itemFields = itemElement.getValue() as DBList
                if (itemFields.getInteger("WeaponSlot") == weaponSlot) {
                    if (weaponSlot == 1) {
                        swordCount++
                    }
                    if (weaponSlot != 1 || swordCount == 2) {
                        JOptionPane.showMessageDialog(this, "No equipment slot available for this item", "No slot", 0)
                        return
                    }
                }
            }
        }

        val stackSize = templateList.getInteger("MaxStack").coerceAtLeast(1)

        val fieldList = templateList.clone()
        fieldList.setInteger("Dropable", 1, 0)
        fieldList.setInteger("Identified", 1, 0)
        fieldList.setInteger("StackSize", stackSize, 2)

        element = list.getElement("Equip_ItemList")
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

        val item = InventoryItem(template.itemName, element)
        insertItem(itemsModel, item)

        session.setDataModified(true)
        Main.mainWindow.setTitle(null)
    }

    fun setFields(list: DBList) {
        var itemCount = 0
        var itemList: DBList? = null

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

        var element = list.getElement("Equip_ItemList")
        if (element != null && element.getType() == 15) {
            itemList = element.getValue() as DBList
            itemCount = itemList.getElementCount()
        }

        itemsModel.clear()
        if (itemCount != 0) {
            itemsModel.ensureCapacity(itemCount)
        }

        itemList?.let { list ->
            for (itemElement in list) {
                val itemFields = itemElement.getValue() as DBList
                val itemName = itemFields.getString("LocalizedName")
                if (itemName.isNotEmpty() && itemFields.getInteger("BaseItem") != 36) {
                    val item = InventoryItem(itemName, itemElement)
                    insertItem(itemsModel, item)
                }
            }
        }

        itemsField.model = itemsModel
        itemsField.selectedIndex = -1
        if (itemsModel.size() > 0) {
            itemsField.ensureIndexIsVisible(0)
        }
    }

    fun getFields(list: DBList) {
    }

    private fun insertItem(itemModel: DefaultListModel<InventoryItem>, item: InventoryItem) {
        val listSize = itemModel.size()
        var inserted = false
        for (j in 0 until listSize) {
            val listItem = itemModel.getElementAt(j)
            val diff = item.compareTo(listItem)
            if (diff < 0) {
                itemModel.insertElementAt(item, j)
                inserted = true
                break
            }
        }

        if (!inserted) {
            itemModel.addElement(item)
        }
    }

    companion object {
        private val categories = arrayOf("Armor", "Silver Sword", "Steel Sword", "Trophy")
        private const val TAB_ARMOR = 0
        private const val TAB_SILVER_SWORD = 1
        private const val TAB_STEEL_SWORD = 2
        private const val TAB_TROPHY = 3
        private val categoryMappings = arrayOf(intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(29, 0), intArrayOf(39, 3))
    }
}
