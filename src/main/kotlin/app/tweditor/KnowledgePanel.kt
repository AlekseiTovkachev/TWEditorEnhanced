package app.tweditor

import javax.swing.BoxLayout
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane

class KnowledgePanel(private val session: GameSession, private val environment: AppEnvironment) : JPanel() {
    private val tabbedPane = JTabbedPane(2)
    private val charactersField = JList<Any>()
    private val placesField = JList<Any>()
    private val monstersField = JList<Any>()
    private val recipesField = JList<Any>()
    private val ingredientsField = JList<Any>()
    private val glossaryField = JList<Any>()
    private val tutorialField = JList<Any>()

    init {
        var panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(charactersField))
        tabbedPane.addTab("Characters", panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(placesField))
        tabbedPane.addTab("Places", panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(monstersField))
        tabbedPane.addTab("Monsters", panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(recipesField))
        tabbedPane.addTab("Recipes", panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(ingredientsField))
        tabbedPane.addTab("Ingredients", panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(glossaryField))
        tabbedPane.addTab("Glossary", panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(tutorialField))
        tabbedPane.addTab("Tutorial", panel)

        add(tabbedPane)
    }

    fun setFields(list: DBList) {
        setJournalView(charactersField, "character")
        setJournalView(placesField, "place")
        setMonstersView()
        setRecipesView(list)
        setIngredientsView(list)
        setJournalView(glossaryField, "info", "unique")
        setJournalView(tutorialField, "tutorial")
    }

    fun getFields(list: DBList) {
    }

    private fun setJournalView(field: JList<Any>, vararg categories: String) {
        val journalData = session.getJournalData()
        val entries = ArrayList<JournalEntry>()
        if (journalData != null) {
            for (category in categories) {
                entries.addAll(journalData.entriesInCategory(category))
            }
        }
        entries.sortBy { it.entryId }

        val labels = ArrayList<String>(entries.size)
        for (entry in entries) {
            labels.add(label(entry))
        }
        field.setListData(labels.toTypedArray())
        field.selectedIndex = -1
    }

    private fun setMonstersView() {
        val journalData = session.getJournalData()
        val monsters = ArrayList<JournalEntry>()
        if (journalData != null) {
            for (entry in journalData.entries) {
                if (!NON_MONSTER_CATEGORIES.contains(entry.category)) {
                    monsters.add(entry)
                }
            }
        }
        monsters.sortBy { it.entryId }

        val labels = ArrayList<String>()
        if (monsters.isEmpty()) {
            labels.add("(no monsters learned yet)")
        } else {
            for (entry in monsters) {
                labels.add(label(entry))
            }
        }
        monstersField.setListData(labels.toTypedArray())
        monstersField.selectedIndex = -1
    }

    private fun setRecipesView(list: DBList) {
        val labels = ArrayList<String>()
        appendAlchemySection(labels, list, "AlchKnowledge", "AlchRecipName")
        if (labels.isEmpty()) {
            labels.add("(no formulas known)")
        }
        recipesField.setListData(labels.toTypedArray())
        recipesField.selectedIndex = -1
    }

    private fun setIngredientsView(list: DBList) {
        val labels = ArrayList<String>()
        appendAlchemySection(labels, list, "AlchIdent", "AlchSubstance")
        val journalData = session.getJournalData()
        if (journalData != null) {
            val substances = ArrayList<JournalEntry>(journalData.entriesInCategory("hydragenum"))
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
        ingredientsField.setListData(labels.toTypedArray())
        ingredientsField.selectedIndex = -1
    }

    private fun appendAlchemySection(labels: MutableList<String>, list: DBList, elementName: String, fieldName: String) {
        val element = list.getElement(elementName)
        if (element == null || element.getType() != 15) {
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

    private fun label(entry: JournalEntry): String {
        return JournalEntryNames.displayName(entry.entryId) + unreadSuffix(entry)
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
