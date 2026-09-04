package app.tweditor

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import javax.swing.ButtonGroup
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton
import javax.swing.ListSelectionModel

class KnowledgePanel(private val session: GameSession, private val environment: AppEnvironment) : JPanel() {
    private class TabDef(
        val title: String,
        val list: JList<Any>,
        val categories: List<String>,
        val addCategory: String?,
        val isJournal: Boolean
    )

    private class Row(val name: String, val label: String, val entries: List<JournalEntry>)

    private val tabbedPane = JPanel(CardLayout())
    private val buttonBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
    private val cardLayout = tabbedPane.layout as CardLayout
    private val tabs = ArrayList<TabDef>()
    private val rowsByTab = HashMap<TabDef, List<Row>>()
    private var selectedTab: TabDef? = null
    private var lastList: DBList? = null
    private var catalogCache: Map<String, List<String>>? = null

    private val addButton = javax.swing.JButton("Add Entry")
    private val removeButton = javax.swing.JButton("Remove Entry")

    init {
        layout = BorderLayout()

        addTab("Characters", listOf("character"), "character")
        addTab("Places", listOf("place"), "place")
        addTab("Monsters", emptyList(), "bestiary")
        addTab("Recipes", emptyList(), null)
        addTab("Ingredients", emptyList(), null)
        addTab("Glossary", listOf("info", "unique"), "info")
        addTab("Tutorial", listOf("tutorial"), "tutorial")

        val buttonGroup = ButtonGroup()
        for (tab in tabs) {
            val button = JToggleButton(tab.title)
            button.actionCommand = tab.title
            button.addActionListener {
                selectTab(tab)
            }
            buttonGroup.add(button)
            buttonBar.add(button)
            if (tab === tabs[0]) {
                button.isSelected = true
            }
        }

        addButton.addActionListener { addEntry() }
        removeButton.addActionListener { removeEntry() }

        val editBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        editBar.add(addButton)
        editBar.add(removeButton)

        val southBar = JPanel(BorderLayout())
        southBar.add(editBar, BorderLayout.WEST)

        add(buttonBar, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)
        add(southBar, BorderLayout.SOUTH)
        selectTab(tabs[0])
    }

    private fun addTab(title: String, categories: List<String>, addCategory: String?) {
        val list = JList<Any>()
        list.visibleRowCount = 18
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        val tab = TabDef(title, list, categories, addCategory, isJournal = addCategory != null || title == "Monsters")
        tabs.add(tab)
        tabbedPane.add(title, JScrollPane(list))
    }

    private fun selectTab(tab: TabDef) {
        selectedTab = tab
        cardLayout.show(tabbedPane, tab.title)
        val editable = tab.addCategory != null
        addButton.isEnabled = editable
        removeButton.isEnabled = editable && tab !== tabs[2]
    }

    fun setFields(list: DBList) {
        lastList = list
        refresh()
    }

    fun getFields(list: DBList) {
    }

    private fun refresh() {
        for (tab in tabs) {
            val rows: List<Row> = when {
                tab.title == "Characters" || tab.title == "Places" || tab.title == "Glossary" || tab.title == "Tutorial" ->
                    journalRows(tab.categories)
                tab.title == "Monsters" -> monsterRows()
                tab.title == "Recipes" -> recipeRows()
                else -> ingredientRows()
            }
            rowsByTab[tab] = rows
            val labels = rows.map { it.label }
            tab.list.setListData(labels.toTypedArray())
            tab.list.selectedIndex = -1
        }
    }

    private fun journalRows(categories: List<String>): List<Row> {
        val journalData = session.getJournalData() ?: return emptyList()
        val entries = ArrayList<JournalEntry>()
        for (category in categories) {
            entries.addAll(journalData.entriesInCategory(category))
        }
        return groupRows(entries)
    }

    private fun monsterRows(): List<Row> {
        val journalData = session.getJournalData() ?: return emptyList()
        val entries = ArrayList<JournalEntry>()
        for (entry in journalData.entries) {
            if (!NON_MONSTER_CATEGORIES.contains(entry.category)) {
                entries.add(entry)
            }
        }
        if (entries.isEmpty()) {
            return listOf(Row("(no monsters learned yet)", "(no monsters learned yet)", emptyList()))
        }
        return groupRows(entries)
    }

    private fun groupRows(entries: List<JournalEntry>): List<Row> {
        val grouped = LinkedHashMap<String, MutableList<JournalEntry>>()
        for (entry in entries) {
            val name = JournalEntryNames.displayName(entry.entryId)
            grouped.getOrPut(name) { ArrayList() }.add(entry)
        }
        val rows = ArrayList<Row>()
        for (groupedEntry in grouped) {
            val name = groupedEntry.key
            val group = groupedEntry.value
            val unread = group.any { !it.isRead }
            rows.add(Row(name, name + if (unread) " (unread)" else "", group))
        }
        return rows.sortedBy { it.name.lowercase() }
    }

    private fun recipeRows(): List<Row> {
        val labels = ArrayList<String>()
        appendAlchemySection(labels, "AlchKnowledge", "AlchRecipName")
        if (labels.isEmpty()) {
            labels.add("(no formulas known)")
        }
        return labels.map { Row(it, it, emptyList()) }
    }

    private fun ingredientRows(): List<Row> {
        val labels = ArrayList<String>()
        appendAlchemySection(labels, "AlchIdent", "AlchSubstance")
        val journalData = session.getJournalData()
        if (journalData != null) {
            val substances = ArrayList<JournalEntry>()
            for (substance in SUBSTANCE_CATEGORIES) {
                substances.addAll(journalData.entriesInCategory(substance))
            }
            substances.sortBy { it.entryId }
            for (substance in substances) {
                labels.add(JournalEntryNames.displayName(substance.entryId) + unreadSuffix(substance))
            }
        }
        if (labels.isEmpty()) {
            labels.add("(no ingredients identified)")
        }
        return labels.map { Row(it, it, emptyList()) }
    }

    private fun appendAlchemySection(labels: MutableList<String>, elementName: String, fieldName: String) {
        val list = lastList ?: return
        val element = list.getElement(elementName)
        if (element == null || element.getType() != DBElement.LIST) {
            return
        }
        val entries = element.getValue() as DBList
        for (item in entries) {
            val fields = item.getValue() as DBList
            val resref = fields.getString(fieldName)
            if (resref.isNotEmpty()) {
                labels.add(resolveItemName(resref))
            }
        }
    }

    private fun addEntry() {
        val tab = selectedTab ?: return
        val category = tab.addCategory ?: return
        val catalog = journalCatalog()
        val knownIds = catalog?.get(category) ?: emptyList()

        val combo = JComboBox<String>()
        for (id in knownIds) {
            combo.addItem(id)
        }
        combo.isEditable = true
        combo.selectedItem = ""

        val pane = JOptionPane(combo, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION)
        val dialog = pane.createDialog(this, "Add Journal Entry (" + category + ")")
        dialog.isVisible = true
        if (pane.value != JOptionPane.OK_OPTION) {
            return
        }
        val entryId = (combo.selectedItem as? String)?.trim() ?: ""
        if (entryId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entry id supplied", "No Entry", 0)
            return
        }

        session.addJournalEntry(category, entryId)
        refresh()
    }

    private fun removeEntry() {
        val tab = selectedTab ?: return
        if (!tab.isJournal) {
            return
        }
        val index = tab.list.selectedIndex
        if (index < 0) {
            JOptionPane.showMessageDialog(this, "You must select an entry to remove", "No Entry Selected", 0)
            return
        }
        val rows = rowsByTab[tab] ?: return
        val row = rows.getOrNull(index) ?: return
        if (row.entries.isEmpty()) {
            return
        }
        val option = JOptionPane.showConfirmDialog(this, "Remove the selected journal entry?  The change is written when you save.", "Remove Entry", 0)
        if (option != 0) {
            return
        }
        session.removeJournalEntries(row.entries)
        refresh()
    }

    private fun journalCatalog(): Map<String, List<String>>? {
        catalogCache?.let { return it }
        val entryObject = environment.resourceFiles["journal.2da"] ?: return null
        val input: InputStream = try {
            when (entryObject) {
                is KeyEntry -> entryObject.getInputStream()
                is File -> FileInputStream(entryObject)
                else -> return null
            }
        } catch (exc: IOException) {
            return null
        }
        val map = HashMap<String, MutableList<String>>()
        try {
            val lines = input.use { stream ->
                stream.readBytes().toString(Charsets.ISO_8859_1).lines()
            }
            var columns: List<String>? = null
            for (line in lines) {
                if (line.isBlank()) {
                    continue
                }
                if (line.trim().startsWith("2DA")) {
                    continue
                }
                if (columns == null) {
                    columns = line.split('\t').map { it.trim() }
                    continue
                }
                val tokens = line.split('\t')
                val category = columnValue(columns, tokens, "Category")
                val entryId = columnValue(columns, tokens, "EntryId")
                if (category.isNotEmpty() && entryId.isNotEmpty()) {
                    map.getOrPut(category.lowercase()) { ArrayList() }.add(entryId)
                }
            }
        } catch (exc: IOException) {
            return null
        }
        catalogCache = map
        return map
    }

    private fun columnValue(columns: List<String>, tokens: List<String>, name: String): String {
        val index = columns.indexOf(name)
        if (index < 0 || index + 1 >= tokens.size) {
            return ""
        }
        val value = tokens[index + 1].trim()
        return if (value == "****") "" else value
    }

    private fun unreadSuffix(entry: JournalEntry): String {
        return if (entry.isRead) "" else " (unread)"
    }

    private fun resolveItemName(resref: String): String {
        for (itemTemplate in environment.itemTemplates) {
            if (itemTemplate.resourceName.equals(resref, ignoreCase = true)) {
                return itemTemplate.itemName
            }
        }
        return resref
    }

    companion object {
        private val SUBSTANCE_CATEGORIES = setOf(
            "hydragenum", "vermilion", "rebis", "quebrith", "aether", "vitriol")
        private val NON_MONSTER_CATEGORIES = SUBSTANCE_CATEGORIES + setOf(
            "recipe", "character", "place", "info", "tutorial", "unique", "hidden")
    }
}
