package app.tweditor

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane

class QuestsPanel(private val session: GameSession, private val environment: AppEnvironment) :
    JPanel(), ActionListener {
    private val tabbedPane = JTabbedPane(2)
    private var startedList: MutableList<Quest> = ArrayList()
    private val startedField = JList<Any>()
    private var completedList: MutableList<Quest> = ArrayList()
    private val completedField = JList<Any>()
    private var failedList: MutableList<Quest> = ArrayList()
    private val failedField = JList<Any>()
    private var notStartedList: MutableList<Quest> = ArrayList()
    private val notStartedField = JList<Any>()

    init {
        startedField.visibleRowCount = 26
        var scrollPane = JScrollPane(startedField)

        var buttonPane = JPanel()
        var button = JButton("Examine")
        button.addActionListener(this)
        button.actionCommand = "examine started"
        buttonPane.add(button)

        var panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(scrollPane)
        panel.add(Box.createVerticalStrut(5))
        panel.add(buttonPane)
        tabbedPane.addTab("Started", panel)

        completedField.visibleRowCount = 26
        scrollPane = JScrollPane(completedField)

        button = JButton("Examine")
        button.addActionListener(this)
        button.actionCommand = "examine completed"

        buttonPane = JPanel()
        buttonPane.add(button)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(scrollPane)
        panel.add(Box.createVerticalStrut(5))
        panel.add(buttonPane)
        tabbedPane.addTab("Completed", panel)

        failedField.visibleRowCount = 26
        scrollPane = JScrollPane(failedField)

        button = JButton("Examine")
        button.addActionListener(this)
        button.actionCommand = "examine failed"

        buttonPane = JPanel()
        buttonPane.add(button)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(scrollPane)
        panel.add(Box.createVerticalStrut(5))
        panel.add(buttonPane)
        tabbedPane.addTab("Failed", panel)

        notStartedField.visibleRowCount = 26
        scrollPane = JScrollPane(notStartedField)

        button = JButton("Examine")
        button.addActionListener(this)
        button.actionCommand = "examine not started"

        buttonPane = JPanel()
        buttonPane.add(button)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
        panel.add(scrollPane)
        panel.add(Box.createVerticalStrut(5))
        panel.add(buttonPane)
        tabbedPane.addTab("Not Started", panel)

        add(tabbedPane)
    }

    override fun actionPerformed(ae: ActionEvent?) {
        try {
            val action = ae!!.actionCommand

            if (action == "examine started") {
                val sel = startedField.selectedIndex
                if (sel < 0) {
                    JOptionPane.showMessageDialog(this, "You must select a quest to examine", "No quest selected", 0)
                } else {
                    examineQuest(startedList[sel])
                }
            } else if (action == "examine completed") {
                val sel = completedField.selectedIndex
                if (sel < 0) {
                    JOptionPane.showMessageDialog(this, "You must select a quest to examine", "No quest selected", 0)
                } else {
                    examineQuest(completedList[sel])
                }
            } else if (action == "examine failed") {
                val sel = failedField.selectedIndex
                if (sel < 0) {
                    JOptionPane.showMessageDialog(this, "You must select a quest to examine", "No quest selected", 0)
                } else {
                    examineQuest(failedList[sel])
                }
            } else if (action == "examine not started") {
                val sel = notStartedField.selectedIndex
                if (sel < 0) {
                    JOptionPane.showMessageDialog(this, "You must select a quest to examine", "No quest selected", 0)
                } else {
                    examineQuest(notStartedList[sel])
                }
            }
        } catch (exc: DBException) {
            Main.logException("Unable to access database field", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    @Throws(DBException::class)
    private fun examineQuest(quest: Quest) {
        var fieldList = quest.getQuestElement().getValue() as DBList
        fieldList = fieldList.getElement("MainPhase")!!.getValue() as DBList
        fieldList = fieldList.getElement(0).getValue() as DBList
        var currentPhase = fieldList.getInteger("CurrPhase")
        val element = fieldList.getElement("Phases")
        if (element == null || element.getType() != 15) {
            throw DBException("No phase list found for quest " + quest.getResourceName())
        }
        val phaseList = element.getValue() as DBList
        currentPhase = currentPhase.coerceAtMost(phaseList.getElementCount())

        while (currentPhase > 0) {
            val phaseFields = phaseList.getElement(currentPhase - 1).getValue() as DBList
            val subquestFields = locateSubquest(phaseFields)
            if ((phaseFields.getInteger("Completed") == 1 || phaseFields.getInteger("Failed") == 1) &&
                phaseFields.getString("LocDescription").isNotEmpty()
            ) {
                fieldList = phaseFields
                break
            }

            if (subquestFields != null) {
                fieldList = subquestFields
                break
            }

            currentPhase--
        }

        val description = StringBuilder(256)
        var string = fieldList.getString("LocPhaseName")
        description.append("<b>")
        description.append(if (string.isNotEmpty()) string else quest.questName)
        description.append("</b><br><br>")

        string = fieldList.getString("LocDescription")
        if (string.isNotEmpty()) {
            description.append(string)
            description.append("<br><br>")
        }

        string = fieldList.getString("LocShortDescript")
        if (string.isNotEmpty()) {
            description.append("<i>")
            description.append(string)
            description.append("</i><br><br>")
        }

        description.append("Quest file: ")
        description.append(quest.getResourceName())
        ExamineDialog.showDialog(Main.mainWindow, environment, quest.questName, description.toString())
    }

    @Throws(DBException::class)
    private fun locateSubquest(fieldList: DBList): DBList? {
        var subquestList: DBList? = null
        val element = fieldList.getElement("Phases")
        if (element != null && element.getType() == 15) {
            val questList = element.getValue() as DBList
            val count = questList.getElementCount()
            for (i in count - 1 downTo 0) {
                val questFields = questList.getElement(i).getValue() as DBList
                val subquestFields = locateSubquest(questFields)
                if (subquestFields != null) {
                    subquestList = subquestFields
                    break
                }

                if ((questFields.getInteger("Completed") == 1 || questFields.getInteger("Failed") == 1) &&
                    questFields.getString("LocDescription").isNotEmpty()
                ) {
                    subquestList = questFields
                    break
                }
            }
        }

        return subquestList
    }

    fun setFields(list: DBList) {
        val count = session.getQuests()!!.size
        startedList = ArrayList(count)
        completedList = ArrayList(count)
        failedList = ArrayList(count)
        notStartedList = ArrayList(count)

        for (quest in session.getQuests()!!) {
            when (quest.questState) {
                1 -> insertItem(startedList, quest)
                2 -> insertItem(completedList, quest)
                3 -> insertItem(failedList, quest)
                0 -> insertItem(notStartedList, quest)
            }
        }

        startedField.setListData(startedList.toTypedArray())
        startedField.selectedIndex = -1

        completedField.setListData(completedList.toTypedArray())
        completedField.selectedIndex = -1

        failedField.setListData(failedList.toTypedArray())
        failedField.selectedIndex = -1

        notStartedField.setListData(notStartedList.toTypedArray())
        notStartedField.selectedIndex = -1
    }

    fun getFields(list: DBList) {
    }

    private fun insertItem(list: MutableList<Quest>, quest: Quest) {
        val questName = quest.questName
        val count = list.size
        var index = 0
        while (index < count) {
            val listItem = list[index]
            if (questName < listItem.questName) {
                break
            }
            index++
        }
        list.add(index, quest)
    }
}
