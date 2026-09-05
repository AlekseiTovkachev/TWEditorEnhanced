package app.tweditor

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
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

class InventoryPanel(private val session: GameSession, private val environment: AppEnvironment) :
    JPanel(), ActionListener {
    private val rootNode = DefaultMutableTreeNode("Items")
    private val categoryNodes: Array<CategoryNode> = Array(categories.size) { i -> CategoryNode(categories[i]) }
    private val itemsModel = DefaultListModel<InventoryItem>()
    private val itemsField = JList(itemsModel)
    private val availModel = DefaultTreeModel(rootNode)
    private val availField = JTree(availModel)
    private var availDone = false
    private var ingredients: MutableList<AlchemyIngredient>? = null
    private var ingredientsMap: MutableMap<Int, AlchemyIngredient>? = null
    private val slots = Array(6) { BooleanArray(14) }

    init {
        for (node in categoryNodes) {
            rootNode.add(node)
        }

        itemsField.selectionMode = 0
        itemsField.visibleRowCount = 20
        itemsField.prototypeCellValue = InventoryItem("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm", DBElement(14, 0, "", DBList(environment, 0)))
        itemsField.cellRenderer = ItemListCellRenderer(environment)
        itemsField.fixedCellHeight = ROW_HEIGHT
        var scrollPane = JScrollPane(itemsField)

        var buttonPane = JPanel()
        var button = JButton("Examine Item")
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
        itemsPane.add(JLabel("Current Inventory", 0), "North")
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

    private fun editCurrentItem() {
        val sel = itemsField.selectedIndex
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "You must select an item to edit", "No item selected", 0)
            return
        }

        val item = itemsModel.getElementAt(sel)

        ItemEditDialog.showDialog(Main.mainWindow, session, environment, item.name, item.element.getValue() as DBList)
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

        val alchemyID = fieldList.getInteger("AlchIngredient")
        if (alchemyID > 0) {
            val ingredient = ingredientsMap!![alchemyID]
            if (ingredient != null) {
                description.append("<br><ul>")
                for (substance in ingredient.substances) {
                    description.append("<li>")
                    description.append(substance)
                }

                description.append("</ul>")
            }
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
        val itemList = list.getElement("ItemList")!!.getValue() as DBList
        for (element in itemList) {
            if (element === itemElement) {
                itemList.removeElement(element)
                val fieldList = itemElement.getValue() as DBList
                val x = fieldList.getInteger("Repos_PosX")
                val y = fieldList.getInteger("Repos_PosY")
                val questItem = fieldList.getInteger("QuestItem")
                if (questItem != 0 || x < 0 || x >= 14 || y < 0 || y >= 6) break
                slots[y][x] = false
                break
            }
        }

        session.setDataModified(true)
        Main.mainWindow!!.setTitle(null)
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
        val questItem = templateList.getInteger("QuestItem")
        val alchemyIngredient = templateList.getInteger("AlchIngredient")

        var x = 0
        var y = 0
        if (questItem == 0) {
            var foundSlot = false
            if (alchemyIngredient == 0) {
                while (y < 3) {
                    x = 0
                    while (x < 14) {
                        if (!slots[y][x]) {
                            foundSlot = true
                            break
                        }
                        x++
                    }

                    if (foundSlot) break
                    y++
                }
            } else {
                y = 3
                while (y < 6) {
                    x = 0
                    while (x < 14) {
                        if (!slots[y][x]) {
                            foundSlot = true
                            break
                        }
                        x++
                    }

                    if (foundSlot) {
                        break
                    }
                    y++
                }
            }
            if (!foundSlot) {
                JOptionPane.showMessageDialog(this, "No inventory slot available", "Inventory is full", 0)
                return
            }
        }

        val stackSize = templateList.getInteger("MaxStack").coerceAtLeast(1)

        val fieldList = templateList.clone()
        fieldList.setInteger("Dropable", 1, 0)
        fieldList.setInteger("Identified", 1, 0)
        fieldList.setInteger("StackSize", stackSize, 2)
        fieldList.setInteger("Repos_PosX", x, 2)
        fieldList.setInteger("Repos_PosY", y, 2)

        if (questItem == 0) {
            slots[y][x] = true
        }

        var list = session.database!!.getTopLevelStruct()!!.getValue() as DBList
        list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
        list = list.getElement(0).getValue() as DBList

        var element = list.getElement("ItemList")
        val itemList: DBList
        if (element == null) {
            itemList = DBList(environment, 10)
            element = DBElement(15, 0, "ItemList", itemList)
            list.addElement(element!!)
        } else {
            itemList = element.getValue() as DBList
        }

        element = DBElement(14, 0, "", fieldList)
        itemList.addElement(element!!)

        val item = InventoryItem(template.itemName, element)
        insertItem(itemsModel, item)

        session.setDataModified(true)
        Main.mainWindow!!.setTitle(null)
    }

    @Throws(DBException::class, IOException::class)
    fun setFields(list: DBList) {
        var itemCount = 0
        var itemList: DBList? = null

        if (ingredients == null) {
            val resource = environment.resourceFiles["alchemy_ingre.2da"]
            if (resource == null) {
                throw IOException("alchemy_ingre.2da not found")
            }
            var input: InputStream? = null
            if (resource is File) {
                input = FileInputStream(resource)
            } else if (resource is KeyEntry) {
                input = resource.getInputStream()
            }

            if (input == null) {
                throw IOException("alchemy_ingre.2da not found")
            }
            val textDatabase = TextDatabase(input)
            val count = textDatabase.getResourceCount()
            ingredients = ArrayList(count)
            ingredientsMap = HashMap(count)
            for (i in 0 until count) {
                val name = textDatabase.getString(i, "NameRef")
                if (name.isNotEmpty()) {
                    val substances = ArrayList<String>(4)
                    for (substanceName in substanceNames) {
                        if (textDatabase.getInteger(i, substanceName) == 1) {
                            substances.add(substanceName)
                        }
                    }
                    val ingredient = AlchemyIngredient(i, substances)
                    ingredients!!.add(ingredient)
                    ingredientsMap!![ingredient.getID()] = ingredient
                }
            }
        }

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

        val element = list.getElement("ItemList")
        if (element != null && element.getType() == 15) {
            itemList = element.getValue() as DBList
            itemCount = itemList.getElementCount()
        }

        itemsModel.clear()
        if (itemCount != 0) {
            itemsModel.ensureCapacity(itemCount)
        }

        for (y in 0 until 6) {
            for (x in 0 until 14) {
                slots[y][x] = false
            }
        }

        itemList?.let { list ->
            for (itemElement in list) {
                val itemFields = itemElement.getValue() as DBList
                val itemName = itemFields.getString("LocalizedName")
                if (itemName.isNotEmpty()) {
                    val questItem = itemFields.getInteger("QuestItem")
                    val x = itemFields.getInteger("Repos_PosX")
                    val y = itemFields.getInteger("Repos_PosY")
                    val item = InventoryItem(itemName, itemElement)
                    insertItem(itemsModel, item)
                    if (questItem == 0 && x >= 0 && x < 14 && y >= 0 && y < 6) {
                        slots[y][x] = true
                    }
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

    /** The available-item tree (for tests). */
    internal fun availTree(): JTree = availField

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
        private const val ROW_HEIGHT = 56
        // The player's bags hold no weapons or armor in the game - those live
        // on the paperdoll (Equipment tab) and in the storage chest.
        internal val categories = arrayOf("Bomb", "Book", "Drink", "Food", "Gem", "Grease", "Ingredient", "Jewelry", "Magical", "Potion", "Quest", "Upgrade", "Other")
        private const val TAB_BOMB = 0
        private const val TAB_BOOK = 1
        private const val TAB_DRINK = 2
        private const val TAB_FOOD = 3
        private const val TAB_GEM = 4
        private const val TAB_GREASE = 5
        private const val TAB_INGREDIENT = 6
        private const val TAB_JEWELRY = 7
        private const val TAB_MAGICAL = 8
        private const val TAB_POTION = 9
        private const val TAB_QUEST = 10
        private const val TAB_UPGRADE = 11
        private const val TAB_OTHER = 12
        internal val categoryMappings = arrayOf(
            intArrayOf(10, 9), intArrayOf(11, 9), intArrayOf(16, 12), intArrayOf(20, 7), intArrayOf(21, 8),
            intArrayOf(22, 9), intArrayOf(23, 7), intArrayOf(27, 12), intArrayOf(28, 12), intArrayOf(30, 1),
            intArrayOf(31, 12), intArrayOf(32, 4), intArrayOf(33, 6), intArrayOf(34, 11), intArrayOf(35, 8),
            intArrayOf(37, 8), intArrayOf(38, 7), intArrayOf(40, 10), intArrayOf(44, 3), intArrayOf(45, 12),
            intArrayOf(46, 5), intArrayOf(47, 0), intArrayOf(48, 2), intArrayOf(49, 12), intArrayOf(54, 12)
        )

        internal val substanceNames = arrayOf("Vitriol", "Rebis", "Aether", "Quebirth", "Hydragenum", "Vermilion", "Albedo", "Nigredo", "Rubedo")
    }
}
