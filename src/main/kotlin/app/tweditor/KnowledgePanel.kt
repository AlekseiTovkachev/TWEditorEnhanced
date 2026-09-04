package app.tweditor

import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class KnowledgePanel(private val session: GameSession, private val environment: AppEnvironment) : JPanel() {
    private val tabbedPane = JTabbedPane(2)
    private val storyPhaseField = JLabel()
    private val journalTree = JTree()
    private val bestiaryField = JList<Any>()
    private val alchemyField = JList<Any>()
    private val combatField = JList<Any>()
    private val flagsField = JList<Any>()
    private val profileField = JLabel()

    init {
        var panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(storyPhaseField)
        val journalPane = JScrollPane(journalTree)
        panel.add(journalPane)
        tabbedPane.addTab("Journal", panel)

        bestiaryField.visibleRowCount = 26
        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(bestiaryField))
        tabbedPane.addTab("Bestiary", panel)

        alchemyField.visibleRowCount = 26
        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(alchemyField))
        tabbedPane.addTab("Alchemy", panel)

        combatField.visibleRowCount = 20
        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(JScrollPane(combatField))
        tabbedPane.addTab("Combat", panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(profileField)
        panel.add(JScrollPane(flagsField))
        tabbedPane.addTab("Flags", panel)

        add(tabbedPane)
    }

    fun setFields(list: DBList) {
        setJournalView()
        setBestiaryView()
        setAlchemyView(list)
        setCombatView(list)
        setFlagsView(list)
    }

    fun getFields(list: DBList) {
    }

    private fun setJournalView() {
        val journalData = session.getJournalData()
        if (journalData == null) {
            storyPhaseField.text = "Story phase: (unknown)"
            journalTree.model = DefaultTreeModel(DefaultMutableTreeNode("Journal"))
            return
        }

        storyPhaseField.text = "Story phase: " + journalData.storyPhase

        val groups = LinkedHashMap<String, MutableList<JournalEntry>>()
        for (entry in journalData.entries) {
            val group = knowledgeGroup(entry.category) ?: continue
            var groupList = groups[group]
            if (groupList == null) {
                groupList = ArrayList()
                groups[group] = groupList
            }
            groupList.add(entry)
        }

        var visibleCount = 0
        for (groupList in groups.values) {
            visibleCount += groupList.size
        }
        val root = DefaultMutableTreeNode("Journal (" + visibleCount + " entries)")

        for (group in GROUP_ORDER) {
            val groupList = groups[group] ?: continue
            groupList.sortBy { it.entryId }
            val categoryNode = DefaultMutableTreeNode(groupLabel(group) + " (" + groupList.size + ")")
            for (entry in groupList) {
                val label = entry.entryId + if (entry.isRead) "" else " (unread)"
                categoryNode.add(DefaultMutableTreeNode(label))
            }
            root.add(categoryNode)
        }

        if (journalData.trackedQuests.isNotEmpty()) {
            val trackedNode = DefaultMutableTreeNode("Tracked quests (" + journalData.trackedQuests.size + ")")
            for (questId in journalData.trackedQuests) {
                trackedNode.add(DefaultMutableTreeNode(trackedQuestLabel(questId)))
            }
            root.add(trackedNode)
        }

        journalTree.model = DefaultTreeModel(root)
    }

    private fun setBestiaryView() {
        val quests = session.getQuests()
        val bestiary = ArrayList<Quest>()
        if (quests != null) {
            for (quest in quests) {
                if (quest.getResourceName().startsWith(BESTIARY_PREFIX)) {
                    bestiary.add(quest)
                }
            }
        }
        bestiary.sortBy { it.getResourceName() }

        val labels = ArrayList<String>(bestiary.size)
        for (quest in bestiary) {
            labels.add(quest.questName + " (" + stateLabel(quest.questState) + ")")
        }
        bestiaryField.setListData(labels.toTypedArray())
        bestiaryField.selectedIndex = -1
    }

    private fun setAlchemyView(list: DBList) {
        val lines = ArrayList<String>()
        appendAlchemySection(lines, list, "AlchKnowledge", "AlchRecipName", "Known formulas")
        appendAlchemySection(lines, list, "AlchIdent", "AlchSubstance", "Identified substances")
        alchemyField.setListData(lines.toTypedArray())
        alchemyField.selectedIndex = -1
    }

    private fun appendAlchemySection(lines: MutableList<String>, list: DBList, elementName: String, fieldName: String, header: String) {
        val element = list.getElement(elementName)
        if (element == null || element.getType() != 15) {
            return
        }
        val entries = element.getValue() as DBList
        lines.add(header + " (" + entries.getElementCount() + "):")
        for (item in entries) {
            val fields = item.getValue() as DBList
            val resref = fields.getString(fieldName)
            if (resref.isNotEmpty()) {
                lines.add("  " + resolveItemName(resref))
            }
        }
    }

    private fun setCombatView(list: DBList) {
        val counts = LinkedHashMap<String, Int>()
        val killElement = list.getElement("KillList")
        if (killElement != null && killElement.getType() == 15) {
            val killList = killElement.getValue() as DBList
            for (element in killList) {
                val fields = element.getValue() as DBList
                for (tag in fields.getString("OppTag").split(';')) {
                    val trimmed = tag.trim()
                    if (trimmed.isEmpty()) {
                        continue
                    }
                    val previous = counts[trimmed]
                    counts[trimmed] = (previous ?: 0) + 1
                }
            }
        }

        val lines = ArrayList<String>(counts.size)
        for (tag in counts.keys.sorted()) {
            lines.add(tag + " x " + counts[tag])
        }
        combatField.setListData(lines.toTypedArray())
        combatField.selectedIndex = -1
    }

    private fun setFlagsView(list: DBList) {
        val lines = ArrayList<String>()
        val varElement = list.getElement("VarTable")
        if (varElement != null && varElement.getType() == 15) {
            val varTable = varElement.getValue() as DBList
            for (element in varTable) {
                val fields = element.getValue() as DBList
                val name = fields.getString("Name")
                if (name.isNotEmpty()) {
                    lines.add(name)
                }
            }
        }
        flagsField.setListData(lines.toTypedArray())
        flagsField.selectedIndex = -1

        var profileCount = 0
        val profileElement = list.getElement("ProfileList")
        if (profileElement != null && profileElement.getType() == 15) {
            profileCount = (profileElement.getValue() as DBList).getElementCount()
        }
        profileField.text = "Creature profiles: " + profileCount
    }

    private fun trackedQuestLabel(questId: String): String {
        val quests = session.getQuests() ?: return questId
        for (quest in quests) {
            if (quest.getResourceName() == questId) {
                return quest.questName
            }
        }
        return questId
    }

    private fun resolveItemName(resref: String): String {
        for (itemTemplate in environment.itemTemplates) {
            if (itemTemplate.resourceName.equals(resref, ignoreCase = true)) {
                return itemTemplate.itemName
            }
        }
        return resref
    }

    private fun stateLabel(state: Int): String = when (state) {
        1 -> "Started"
        2 -> "Completed"
        3 -> "Failed"
        else -> "Not started"
    }

    companion object {
        private const val BESTIARY_PREFIX = "q9"
        private val GROUP_ORDER = listOf("recipe", "character", "place", "info", "substance", "unique", "tutorial", "other")
        private val KNOWN_GROUPS = setOf("recipe", "character", "place", "info", "unique", "tutorial")
        private val SUBSTANCES = setOf("hydragenum", "vermilion", "rebis", "quebrith", "aether", "vitriol")

        private fun knowledgeGroup(category: String): String? = when {
            category == "hidden" -> null
            KNOWN_GROUPS.contains(category) -> category
            SUBSTANCES.contains(category) -> "substance"
            else -> "other"
        }

        private fun groupLabel(group: String): String = when (group) {
            "recipe" -> "Recipes"
            "character" -> "Characters"
            "place" -> "Places"
            "info" -> "Glossary"
            "substance" -> "Substances"
            "unique" -> "Unique"
            "tutorial" -> "Tutorial"
            else -> "Other"
        }
    }
}
