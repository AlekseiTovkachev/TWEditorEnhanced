package app.tweditor

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton

class KnowledgePanel(private val session: GameSession, private val environment: AppEnvironment) : JPanel() {
    private class TabDef(val title: String, val catalogCategories: List<String>)

    private class CatalogRow(val category: String, val entryId: String, val picture: String)

    private class CatalogEntry(val category: String, val entryId: String, val label: String, val inSave: Boolean, val variantIds: List<String>)

    private val tabbedPane = JPanel(CardLayout())
    private val cardLayout = tabbedPane.layout as CardLayout
    private val buttonBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
    private val tabs = ArrayList<TabDef>()
    private var selectedTab: TabDef? = null
    private var catalogCache: Map<String, List<CatalogRow>>? = null
    private val templateNameCache = HashMap<String, String?>()

    init {
        layout = BorderLayout()

        val tabDefs = listOf(
            TabDef("Characters", listOf("character")),
            TabDef("Places", listOf("place")),
            TabDef("Monsters", listOf("bestiary")),
            TabDef("Recipes", listOf("recipe", "recipe_oil", "recipe_bomb")),
            TabDef("Ingredients", SUBSTANCE_CATEGORIES.toList()),
            TabDef("Glossary", listOf("info")),
            TabDef("Tutorial", listOf("tutorial", "alchemy")))

        val buttonGroup = ButtonGroup()
        for (tab in tabDefs) {
            tabs.add(tab)
            val button = JToggleButton(tab.title)
            button.actionCommand = tab.title
            button.addActionListener { selectTab(tab) }
            buttonGroup.add(button)
            buttonBar.add(button)
            if (tabs.size == 1) {
                button.isSelected = true
            }
        }

        val hintLabel = JLabel("Ticked entries are written into the save on the next Save.")
        val southBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        southBar.add(hintLabel)

        add(buttonBar, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)
        add(southBar, BorderLayout.SOUTH)
        selectTab(tabs[0])
    }

    private fun selectTab(tab: TabDef) {
        selectedTab = tab
        cardLayout.show(tabbedPane, tab.title)
    }

    fun setFields(list: DBList) {
        refresh()
    }

    fun getFields(list: DBList) {
    }

    private fun refresh() {
        tabbedPane.removeAll()
        for (tab in tabs) {
            val checklist = JPanel()
            checklist.layout = BoxLayout(checklist, BoxLayout.PAGE_AXIS)
            for (entry in catalogEntries(tab)) {
                val checkBox = JCheckBox(entry.label)
                checkBox.isSelected = entry.inSave
                checkBox.addActionListener {
                    try {
                        if (checkBox.isSelected) {
                            val bookStyle = entry.variantIds.firstOrNull { it.contains("/s/") }
                            session.addJournalEntry(entry.category, bookStyle ?: entry.variantIds.first())
                        } else {
                            session.removeJournalEntries(entry.variantIds.map { JournalEntry(entry.category, it, false) })
                        }
                    } catch (exc: Throwable) {
                        Main.logException("Unable to edit journal entry", exc)
                    }
                }
                checklist.add(checkBox)
            }
            tabbedPane.add(tab.title, JScrollPane(checklist))
        }
        val tab = selectedTab ?: tabs[0]
        cardLayout.show(tabbedPane, tab.title)
        revalidate()
        repaint()
    }

    private fun catalogEntries(tab: TabDef): List<CatalogEntry> {
        val journalData = session.getJournalData()
        val inSave = HashMap<String, JournalEntry>()
        if (journalData != null) {
            for (entry in journalData.entries) {
                inSave[entry.category + ":" + entry.entryId.lowercase()] = entry
            }
        }

        val result = LinkedHashMap<String, CatalogEntry>()
        fun put(category: String, entryId: String, label: String) {
            val key = category + ":" + entryId.lowercase()
            val entry = inSave[key]
            val unread = if (entry != null && !entry.isRead) " (unread)" else ""
            result[key] = CatalogEntry(category, entryId, label + unread, entry != null, listOf(entryId))
        }

        val catalog = journalCatalog()
        if (tab.title == "Monsters") {
            val rows = catalog?.get("bestiary") ?: emptyList()
            val grouped = LinkedHashMap<String, MutableList<CatalogRow>>()
            for (row in rows) {
                val sep = row.entryId.indexOf('/')
                val prefix = if (sep > 0) row.entryId.substring(0, sep) else row.entryId
                grouped.getOrPut(prefix.lowercase()) { ArrayList() }.add(row)
            }
            for (groupedEntry in grouped) {
                val group = groupedEntry.value
                val representative = group.first()
                val variantIds = group.map { it.entryId }
                val present = variantIds.map { inSave[representative.category + ":" + it.lowercase()] }
                val inSave = present.any { it != null }
                val unread = present.any { it != null && !it.isRead }
                val label = JournalEntryNames.displayName(representative.entryId) + if (unread) " (unread)" else ""
                val key = representative.category.lowercase() + ":" + groupedEntry.key
                result[key] = CatalogEntry(representative.category, representative.entryId, label, inSave, variantIds)
            }
        } else {
            for (category in tab.catalogCategories) {
                val rows = catalog?.get(category) ?: emptyList()
                for (row in rows) {
                    put(row.category, row.entryId, rowLabel(row))
                }
            }
        }

        if (tab.title == "Ingredients" || tab.title == "Glossary") {
            val uniqueRows = catalog?.get("unique") ?: emptyList()
            for (row in uniqueRows) {
                val isIngredient = row.picture.startsWith("je_ingr")
                if (isIngredient == (tab.title == "Ingredients")) {
                    put(row.category, row.entryId, rowLabel(row))
                }
            }
        }

        if (journalData != null) {
            val covered = HashSet<String>()
            for (entry in result.values) {
                for (id in entry.variantIds) {
                    covered.add(entry.category + ":" + id.lowercase())
                }
            }
            for (entry in journalData.entries) {
                val key = entry.category + ":" + entry.entryId.lowercase()
                if (result.containsKey(key) || covered.contains(key)) {
                    continue
                }
                val belongsToTab = if (tab.title == "Monsters") {
                    !NON_MONSTER_CATEGORIES.contains(entry.category)
                } else {
                    tab.catalogCategories.contains(entry.category)
                }
                if (belongsToTab) {
                    put(entry.category, entry.entryId, fallbackLabel(entry.category, entry.entryId))
                }
            }
        }

        return result.values.sortedBy { it.label.lowercase() }
    }

    private fun rowLabel(row: CatalogRow): String {
        if (row.entryId.startsWith("it_")) {
            val name = itemTemplateName(row.entryId)
            if (name != null) {
                return withVariant(row.entryId, name)
            }
        }
        if (row.picture.startsWith("je_ingr")) {
            val resref = "it_ingr_" + row.picture.substring("je_ingr_".length)
            val name = itemTemplateName(resref)
            if (name != null) {
                return withVariant(row.entryId, name)
            }
            return withVariant(row.entryId, "Ingredient " + row.picture)
        }
        return withVariant(row.entryId, JournalEntryNames.displayName(row.entryId))
    }

    private fun fallbackLabel(category: String, entryId: String): String {
        if (entryId.startsWith("it_")) {
            val name = itemTemplateName(entryId)
            if (name != null) {
                return withVariant(entryId, name)
            }
        }
        return withVariant(entryId, JournalEntryNames.displayName(entryId))
    }

    private fun withVariant(entryId: String, label: String): String {
        val sep = entryId.indexOf('/')
        if (sep < 0) {
            return label
        }
        val variant = entryId.substring(sep + 1)
        if (variant.isEmpty() || variant == "info" || variant == "basic") {
            return label
        }
        return label + " (" + variant + ")"
    }

    private fun itemTemplateName(resref: String): String? {
        if (templateNameCache.containsKey(resref.lowercase())) {
            return templateNameCache[resref.lowercase()]
        }
        var name: String? = null
        for (itemTemplate in environment.itemTemplates) {
            if (itemTemplate.resourceName.equals(resref, ignoreCase = true)) {
                name = itemTemplate.itemName
                break
            }
        }
        if (name == null) {
            val entryObject = environment.resourceFiles[resref.lowercase() + ".uti"]
            if (entryObject != null) {
                val input: InputStream? = try {
                    when (entryObject) {
                        is KeyEntry -> entryObject.getInputStream()
                        is File -> FileInputStream(entryObject)
                        else -> null
                    }
                } catch (exc: IOException) {
                    null
                }
                if (input != null) {
                    try {
                        input.use { stream ->
                            val database = Database(environment)
                            database.load(stream)
                            val fields = database.getTopLevelStruct()!!.getValue() as DBList
                            name = fields.getString("LocalizedName").ifEmpty { null }
                        }
                    } catch (exc: Throwable) {
                        name = null
                    }
                }
            }
        }
        templateNameCache[resref.lowercase()] = name
        return name
    }

    private fun journalCatalog(): Map<String, List<CatalogRow>>? {
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
        val map = HashMap<String, MutableList<CatalogRow>>()
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
                    val picture = columnValue(columns, tokens, "Picture")
                    map.getOrPut(category.lowercase()) { ArrayList() }.add(CatalogRow(category, entryId, picture))
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
        if (index < 0 || index >= tokens.size) {
            return ""
        }
        val value = tokens[index].trim()
        return if (value == "****") "" else value
    }

    companion object {
        private val SUBSTANCE_CATEGORIES = setOf(
            "hydragenum", "vermilion", "rebis", "quebrith", "aether", "vitriol")
        private val NON_MONSTER_CATEGORIES = SUBSTANCE_CATEGORIES + setOf(
            "recipe", "recipe_oil", "recipe_bomb", "alchemy", "character", "place", "info", "tutorial", "unique", "hidden")
    }
}
